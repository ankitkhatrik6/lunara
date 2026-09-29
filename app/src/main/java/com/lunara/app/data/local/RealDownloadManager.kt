package com.lunara.app.data.local

import android.content.Context
import android.net.Uri
import com.lunara.app.player.StreamUserAgents
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads a resolved audio URL straight into the app's private media folder
 * (`filesDir/lunara_media`), never into the device's shared Downloads folder.
 *
 * The old pipeline queued a WorkManager job per track: foreground promotion, scheduling delays
 * and retry backoffs turned seconds into minutes, and a plain (non-ranged) GET made YouTube's
 * CDN answer 403, so downloads spun forever before failing. This implementation instead streams
 * in the app process with a big buffer and, crucially, sends the *closed* byte range the CDN
 * demands (read from the stream URL's `clen`) — which is what makes saves start instantly and
 * finish reliably.
 */
@Singleton
class RealDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val downloadDir: File
        get() = File(context.filesDir, MEDIA_DIR).apply { if (!exists()) mkdirs() }

    /** Whatever file is stored for [songId] (any container extension), if any. */
    fun getSongFile(songId: String): File? =
        downloadDir.listFiles()?.firstOrNull { it.isFile && it.name.startsWith(SONG_PREFIX + songId + ".") }

    /** Deletes the stored file for [songId] (any container) plus its leftover `.part` file. */
    fun deleteDownloadedFile(songId: String): Boolean {
        val prefix = SONG_PREFIX + songId + "."
        val targets = downloadDir.listFiles { file ->
            file.isFile && (file.name.startsWith(prefix) || file.name == "$songId.part")
        } ?: return false
        return targets.fold(false) { removed, file -> file.delete() || removed }
    }

    /**
     * Streams [url] into `filesDir/lunara_media/<songId>.<ext>` and calls [onProgress] with
     * `(downloadedBytes, totalBytes)`. Coroutine cancellation aborts the transfer immediately;
     * the partial file is left behind for the caller to clean up.
     */
    suspend fun downloadStream(
        songId: String,
        url: String,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        // YouTube only serves bytes to a *closed* byte range; `clen` is the exact file size.
        val declaredLength = Uri.parse(url).getQueryParameter("clen")?.toLongOrNull() ?: 0L

        val request = Request.Builder()
            .url(url)
            // The URL is bound to the client identity that minted it, so the agent has to come
            // from the URL (see StreamUserAgents) - a fixed agent is answered with 403.
            .header("User-Agent", StreamUserAgents.userAgentFor(url))
            .apply { if (declaredLength > 0L) header("Range", "bytes=0-${declaredLength - 1}") }
            .build()

        val target = File(downloadDir, SONG_PREFIX + songId + "." + extensionFor(url))
        val partial = File(downloadDir, "$songId.part")
        partial.delete()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Server responded with HTTP ${response.code}")
            val body = response.body ?: error("Empty response body")
            val expected = body.contentLength().takeIf { it > 0L } ?: declaredLength

            body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    // 256 KiB chunks: the old 8 KiB buffer plus a progress emission per chunk
                    // was the other half of "downloads take forever".
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, expected)
                    }
                    output.flush()
                }
            }
        }

        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) error("Could not store the downloaded file")
        target
    }

    private fun extensionFor(url: String): String = when {
        url.contains("mime=audio%2Fwebm") || url.contains("mime=audio/webm") -> "webm"
        url.contains("mime=audio%2Fmp4") || url.contains("mime=audio/mp4") -> "m4a"
        else -> "m4a"
    }

    private companion object {
        const val MEDIA_DIR = "lunara_media"
        const val SONG_PREFIX = "lunara_"
        const val BUFFER_SIZE = 256 * 1024
    }
}

