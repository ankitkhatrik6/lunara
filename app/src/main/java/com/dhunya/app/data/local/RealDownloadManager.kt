package com.dhunya.app.data.local

import android.content.Context
import com.dhunya.app.domain.model.DownloadStatus
import com.dhunya.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadProgress {
    data class Progress(val percentage: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadProgress
    data class Success(val localFile: File) : DownloadProgress
    data class Failed(val error: String) : DownloadProgress
}

@Singleton
class RealDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder().build()

    fun getDownloadDir(): File {
        val dir = File(context.filesDir, "dhunya_media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSongFile(songId: String): File {
        return File(getDownloadDir(), "$songId.mp3")
    }

    /**
     * Downloads an audio stream directly to internal storage as a real audio file
     * and emits real-time progress for UI updates.
     */
    fun downloadAudioStream(song: Song): Flow<DownloadProgress> = flow {
        val url = song.streamUrl
        if (url.isNullOrBlank()) {
            emit(DownloadProgress.Failed("Song does not have a valid streaming URL"))
            return@flow
        }

        val destinationFile = getSongFile(song.id)

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                emit(DownloadProgress.Failed("Server responded with HTTP ${response.code}"))
                return@flow
            }

            val body = response.body!!
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(destinationFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0.5f

                        emit(DownloadProgress.Progress(progress, downloadedBytes, totalBytes))
                    }
                    output.flush()
                }
            }

            emit(DownloadProgress.Success(destinationFile))
        } catch (e: Exception) {
            if (destinationFile.exists()) destinationFile.delete()
            emit(DownloadProgress.Failed(e.localizedMessage ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteDownloadedFile(songId: String): Boolean {
        val file = getSongFile(songId)
        return if (file.exists()) file.delete() else false
    }
}
