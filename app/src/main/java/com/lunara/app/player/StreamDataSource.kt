package com.lunara.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Sizes of the audio files the resolver has handed out, keyed by stream URL.
 *
 * [RangedHttpDataSource] needs the exact file size to turn ExoPlayer's *open ended* requests
 * into closed byte ranges, and the resolver already knows it (`contentLength` on every
 * InnerTube format). The cache is process wide and self healing: even when a URL is missing
 * from it the data source learns the size from the response's `Content-Range` header on the
 * very first request.
 */
object StreamSizes {

    private val sizes = ConcurrentHashMap<String, Long>()

    /** Remembers the exact size of [url] so later range requests can be closed. */
    fun remember(url: String?, bytes: Long) {
        if (!url.isNullOrBlank() && bytes > 0L) sizes[url] = bytes
    }

    /** Size of [url] in bytes, or `0` when it is unknown. */
    fun sizeOf(url: String?): Long {
        if (url.isNullOrBlank()) return 0L
        sizes[url]?.let { return it }
        // Every InnerTube stream URL also carries `clen`, which holds the same number, so a
        // URL that was resolved before this process started still gets a closed range.
        val clen = runCatching { Uri.parse(url).getQueryParameter("clen") }.getOrNull()
        return clen?.toLongOrNull()?.takeIf { it > 0L } ?: 0L
    }
}

/**
 * Wraps any [DataSource.Factory] so that every request against YouTube's CDN carries a
 * **closed** byte range instead of an open ended one.
 *
 * This is not an optimisation, it is what makes playback possible at all. YouTube's
 * `*.googlevideo.com` servers throttle a plain `GET` (and `Range: bytes=0-`) down to roughly
 * 31 KB/s, while the same URL asked for with a closed range such as `Range: bytes=0-3449446`
 * is served at several MB/s. Measured on one audio format, same URL, same user agent:
 *
 * ```
 * no Range header           -> 200,   31 KB/s   (3.1 MB took 97.8 s)
 * Range: bytes=0-           -> 206, 9280 KB/s   (3.1 MB took 0.3 s)
 * Range: bytes=0-<clen - 1> -> 206, 9215 KB/s   (whole 3.4 MB file in 0.4 s)
 * ```
 *
 * ExoPlayer's own requests are the throttled kind: `HttpUtil.buildRangeRequestHeader` only
 * emits a `Range` header when the length is already known, so the first request for a fresh
 * media item - and therefore every playback start - goes out *without* one. The loader then
 * crawls, the buffer never fills and the player reports a source error. That is exactly what
 * "stuck on buffering, then a source error" was.
 *
 * Non-YouTube URIs (local files, artwork, `content://`) and HLS playlists are passed through
 * untouched - a playlist is an index file, it must never be turned into a byte range.
 */
@UnstableApi
class RangedHttpDataSourceFactory(
    private val upstream: DataSource.Factory
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        RangedHttpDataSource(upstream.createDataSource())
}

@UnstableApi
private class RangedHttpDataSource(
    private val upstream: DataSource
) : DataSource by upstream {

    private companion object {
        /** Size of one range request while the file size is still unknown. */
        const val CHUNK_BYTES = 1024L * 1024L

        /** How often one `read` may reopen the connection before it gives up. */
        const val MAX_RESUME_ATTEMPTS = 4
    }

    private var currentSpec: DataSpec? = null

    /** `true` for URIs that must keep their exact original request (everything but YouTube). */
    private var passthrough = false

    /** Absolute position of the next byte the caller will receive. */
    private var nextPosition = 0L

    /** Absolute position of the last byte covered by the currently open request. */
    private var requestEnd = -1L

    /** Exact size of the file being streamed, or `0` while it is still unknown. */
    private var totalBytes = 0L

    private var endOfInput = false

    override fun open(dataSpec: DataSpec): Long {
        currentSpec = dataSpec
        endOfInput = false

        if (!isYouTubeStream(dataSpec.uri)) {
            // Local files, HLS playlists, artwork: exactly the original behaviour.
            passthrough = true
            nextPosition = dataSpec.position
            requestEnd = if (dataSpec.length == C.LENGTH_UNSET.toLong()) -1L
            else dataSpec.position + dataSpec.length - 1L
            return upstream.open(dataSpec)
        }

        passthrough = false
        totalBytes = StreamSizes.sizeOf(dataSpec.uri.toString())
        return upstream.open(rangeSpecFor(dataSpec, dataSpec.position))
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)

        var resumes = 0
        while (true) {
            if (endOfInput) return C.RESULT_END_OF_INPUT
            if (length == 0) return 0

            if (nextPosition > requestEnd) {
                // This range has been handed out completely: continue with the next one unless
                // the file really ends here.
                if (resumes++ >= MAX_RESUME_ATTEMPTS || !openNextRange(force = false)) {
                    endOfInput = true
                    return C.RESULT_END_OF_INPUT
                }
                continue
            }

            val toRead = minOf(length.toLong(), requestEnd - nextPosition + 1L).toInt()
            val read = upstream.read(buffer, offset, toRead)
            if (read == C.RESULT_END_OF_INPUT) {
                // The CDN ended a ranged response early. Ask for the rest from where it stopped
                // instead of letting ExoPlayer see a bogus end of stream in the middle of the
                // file, which it reports as a "source error" that never recovers.
                if (resumes++ >= MAX_RESUME_ATTEMPTS || !openNextRange(force = true)) {
                    endOfInput = true
                    return C.RESULT_END_OF_INPUT
                }
                continue
            }

            nextPosition += read
            return read
        }
    }


    /**
     * Opens the request for [nextPosition], always as a *closed* range.
     *
     * Once the size is known - from [StreamSizes] or from an earlier `Content-Range` - a single
     * range covering the rest of the file is used, so steady state playback needs no extra round
     * trips. While the size is unknown the request is capped to [CHUNK_BYTES], which is still
     * ~9 MB/s, and its response reports the size back.
     */
    private fun openNextRange(force: Boolean): Boolean {
        val spec = currentSpec ?: return false
        val position = nextPosition
        if (totalBytes > 0L && position >= totalBytes) return false
        if (!force && position <= requestEnd) return false
        val end = if (totalBytes > 0L) totalBytes - 1L else position + CHUNK_BYTES - 1L
        if (end < position) return false
        return try {
            upstream.close()
            upstream.open(rangeSpecFor(spec, position))
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Builds the spec that goes on the wire: [dataSpec] with a closed range at [position]. */
    private fun rangeSpecFor(dataSpec: DataSpec, position: Long): DataSpec {
        val explicit = dataSpec.length != C.LENGTH_UNSET.toLong() && position == dataSpec.position
        val end = when {
            explicit -> position + dataSpec.length - 1L
            totalBytes > 0L -> totalBytes - 1L
            else -> position + CHUNK_BYTES - 1L
        }
        nextPosition = position
        requestEnd = maxOf(end, position)
        val ranged = dataSpec.buildUpon()
            .setPosition(position)
            .setLength(requestEnd - position + 1L)
            .build()
        learnSize(ranged.uri)
        return ranged
    }

    /**
     * Reads the file size out of the response's `Content-Range` header
     * (`bytes 0-1048575/3449447`), so a chunked start upgrades itself to a single closed range
     * for the rest of the file.
     */
    private fun learnSize(uri: Uri) {
        if (totalBytes > 0L) return
        val contentRange = upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?: return
        val total = contentRange.substringAfterLast('/', "").trim().toLongOrNull() ?: return
        if (total > 0L) {
            totalBytes = total
            StreamSizes.remember(uri.toString(), total)
        }
    }

    /** Only YouTube's byte served media is rewritten; playlist URIs are index files. */
    private fun isYouTubeStream(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (!scheme.equals("http", ignoreCase = true) &&
            !scheme.equals("https", ignoreCase = true)
        ) {
            return false
        }
        val host = uri.host ?: return false
        if (!host.endsWith("googlevideo.com")) return false
        if (host.startsWith("manifest.")) return false
        val path = uri.path.orEmpty()
        return !path.contains(".m3u8") &&
            !path.contains("/hls_playlist/") &&
            !path.contains("manifest/hls")
    }
}

