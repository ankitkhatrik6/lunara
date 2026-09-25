package com.dhunya.app.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dhunya.app.data.local.dao.DownloadDao
import com.dhunya.app.data.local.dao.SongDao
import com.dhunya.app.data.local.entity.DownloadEntity
import com.dhunya.app.domain.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker responsible for downloading audio tracks reliably,
 * supporting app backgrounding, network reconnections, foreground notification progress,
 * and persistent database state synchronization (Clean Architecture Data layer).
 */
@HiltWorker
class MusicDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val songDao: SongDao,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG_ID = "KEY_SONG_ID"
        const val KEY_STREAM_URL = "KEY_STREAM_URL"
        const val KEY_TITLE = "KEY_TITLE"
        const val KEY_ARTIST = "KEY_ARTIST"

        const val NOTIFICATION_CHANNEL_ID = "dhunya_downloads_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Music Downloads"
        const val PROGRESS_PERCENT = "PROGRESS_PERCENT"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val streamUrl = inputData.getString(KEY_STREAM_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Track"
        val artist = inputData.getString(KEY_ARTIST) ?: "Artist"

        val notificationId = songId.hashCode()

        // 1. Promote to Foreground service with progress notification if Android 8.0+
        createNotificationChannel()
        val initialForegroundInfo = createForegroundInfo(notificationId, title, artist, 0)
        setForeground(initialForegroundInfo)

        // 2. Mark database entity as DOWNLOADING
        downloadDao.insertOrUpdateDownload(
            DownloadEntity(
                songId = songId,
                status = DownloadStatus.DOWNLOADING.name,
                progress = 0f,
                localFilePath = null,
                totalBytes = 0L,
                downloadedBytes = 0L
            )
        )

        val downloadDir = File(context.filesDir, "dhunya_media").apply { if (!exists()) mkdirs() }
        val destinationFile = File(downloadDir, "$songId.mp3")
        val tempFile = File(downloadDir, "$songId.tmp")

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val request = Request.Builder().url(streamUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                markFailed(songId, "Server returned HTTP ${response.code}")
                return@withContext Result.retry()
            }

            val body = response.body!!
            val totalBytes = body.contentLength()
            inputStream = body.byteStream()
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalRead = 0L
            var lastUpdateTimestamp = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isStopped) {
                    tempFile.delete()
                    return@withContext Result.failure()
                }

                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                val now = System.currentTimeMillis()
                // Throttle notification and database updates to every 400ms
                if (now - lastUpdateTimestamp > 400L || totalRead == totalBytes) {
                    lastUpdateTimestamp = now
                    val progressFloat = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                    val progressInt = (progressFloat * 100).toInt()

                    setProgress(workDataOf(PROGRESS_PERCENT to progressInt))

                    // Update notification
                    val updatedNotification = buildNotification(title, artist, progressInt)
                    notificationManager.notify(notificationId, updatedNotification)

                    // Update room database entity
                    downloadDao.insertOrUpdateDownload(
                        DownloadEntity(
                            songId = songId,
                            status = DownloadStatus.DOWNLOADING.name,
                            progress = progressFloat,
                            localFilePath = null,
                            totalBytes = totalBytes,
                            downloadedBytes = totalRead
                        )
                    )
                }
            }

            outputStream.flush()

            // Atomically rename temporary file to destination .mp3
            if (tempFile.exists()) {
                if (destinationFile.exists()) destinationFile.delete()
                tempFile.renameTo(destinationFile)
            }

            // 3. Mark database entity as COMPLETED and update SongEntity with local path
            val localPath = destinationFile.absolutePath
            downloadDao.insertOrUpdateDownload(
                DownloadEntity(
                    songId = songId,
                    status = DownloadStatus.COMPLETED.name,
                    progress = 1.0f,
                    localFilePath = localPath,
                    totalBytes = destinationFile.length(),
                    downloadedBytes = destinationFile.length()
                )
            )

            // Update Song in local DB with localUri and isDownloaded flag
            val existingSong = songDao.getSongById(songId)
            if (existingSong != null) {
                songDao.insertSong(
                    existingSong.copy(
                        isDownloaded = true,
                        localUri = localPath
                    )
                )
            }

            // Dismiss download progress notification and show completed alert
            showDownloadCompletedNotification(notificationId, title, artist)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            markFailed(songId, e.localizedMessage ?: "Download failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private suspend fun markFailed(songId: String, errorReason: String) {
        downloadDao.insertOrUpdateDownload(
            DownloadEntity(
                songId = songId,
                status = DownloadStatus.FAILED.name,
                progress = 0f,
                localFilePath = null,
                totalBytes = 0L,
                downloadedBytes = 0L
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for music downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String,
        artist: String,
        progress: Int
    ): ForegroundInfo {
        val notification = buildNotification(title, artist, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun buildNotification(title: String, artist: String, progress: Int): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading $title")
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showDownloadCompletedNotification(notificationId: Int, title: String, artist: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloaded $title")
            .setContentText("$artist • Ready for offline listening")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(notificationId, notification)
    }
}
