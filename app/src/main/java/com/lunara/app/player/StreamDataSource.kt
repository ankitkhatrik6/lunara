package com.lunara.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.lunara.app.data.remote.innertube.InnerTubeClients
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

    /** Roughly a session's worth of URLs; beyond this the cache is simply dropped. */
    private const val MAX_TRACKED_URLS = 512

    private val sizes = ConcurrentHashMap<String, Long>()

    /** Remembers the exact size of [url] so later range requests can be closed. */
    fun remember(url: String?, bytes: Long) {
        if (url.isNullOrBlank() || bytes <= 0L) return
        // One resolve records a URL per format of every identity it tried, and nothing ever
        // removes an entry - a long session would keep every signed URL it has seen alive.
        if (sizes.size >= MAX_TRACKED_URLS) sizes.clear()
        sizes[url] = bytes
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
 * The user agent each stream URL must be fetched with, keyed by that URL.
 *
 * YouTube signs a stream URL for the client that asked for it and binds it to that client's
 * user agent: fetching `&c=IOS` signed bytes with an Oculus user agent (what earlier builds did
 * by pinning one global user agent) is answered with `403` by the CDN. The resolver therefore
 * records the exact identity it used for every URL it hands out, so playback, downloads and
 * probes all agree on the agent.
 */
object StreamUserAgents {

    /** Kept in step with the size cache: the same resolve fills both, so both bound themselves. */
    private const val MAX_TRACKED_URLS = 512

    private val userAgents = ConcurrentHashMap<String, String>()

    /** Remembers that [url] was signed for [userAgent]. */
    fun remember(url: String?, userAgent: String?) {
        if (url.isNullOrBlank() || userAgent.isNullOrBlank()) return
        if (userAgents.size >= MAX_TRACKED_URLS) userAgents.clear()
        userAgents[url] = userAgent
    }

    /**
     * The user agent [url] must be fetched with: the one recorded at resolve time when known,
     * otherwise the agent of the client named in the URL's own `&c=` tag.
     */
    fun userAgentFor(url: String?): String {
        if (url.isNullOrBlank()) return InnerTubeClients.STREAM_USER_AGENT
        userAgents[url]?.let { return it }
        return InnerTubeClients.playbackUserAgentFor(runCatching { Uri.parse(url) }.getOrNull())
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
    /**
     * Builds the upstream data source for a given user agent. One factory per agent is created
     * lazily, because the agent a URL is served to depends on the client that minted it.
     */
    private val upstreamFactoryFor: (String) -> DataSource.Factory,
    /** The user agent the URL being opened must be fetched with. */
    private val userAgentFor: (Uri) -> String = { StreamUserAgents.userAgentFor(it.toString()) }
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        RangedHttpDataSource(upstreamFactoryFor, userAgentFor)
}

@UnstableApi
private class RangedHttpDataSource(
    private val upstreamFactoryFor: (String) -> DataSource.Factory,
    private val userAgentFor: (Uri) -> String
) : DataSource {

    private companion object {
        /** Size of one range request while the file size is still unknown. */
        const val CHUNK_BYTES = 1024L * 1024L

        /** How often one `read` may reopen the connection before it gives up. */
        const val MAX_RESUME_ATTEMPTS = 4
    }

    /**
     * One upstream per user agent. The player swaps in a fallback URL mid item, and that URL is
     * usually signed for a *different* client than the one that failed, so a single upstream
     * would send the wrong agent and get `403`.
     */
    private val upstreams = HashMap<String, DataSource>()

    /** The upstream currently serving bytes, or `null` before the first [open]. */
    private var upstream: DataSource? = null

    /** Transfer listener handed to every upstream, so byte counts reach ExoPlayer's stats. */
    private var transferListener: TransferListener? = null

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

    override fun addTransferListener(transferListener: TransferListener) {
        if (this.transferListener === transferListener) return
        this.transferListener = transferListener
        upstreams.values.forEach { it.addTransferListener(transferListener) }
    }

    /**
     * The URL the currently open request went to, or `null` before the first [open].
     *
     * Media3 declares this as a method on `DataSource` (not a JavaBean getter that Kotlin would
     * turn into a property), so it is implemented as one.
     */
    override fun getUri(): Uri? = upstream?.getUri()

    /** Response headers of the open request, forwarded from the upstream that served it. */
    override fun getResponseHeaders(): Map<String, List<String>> =
        upstream?.getResponseHeaders() ?: emptyMap()

    override fun open(dataSpec: DataSpec): Long {
        currentSpec = dataSpec
        endOfInput = false

        val selected = upstreamFor(dataSpec.uri)
        if (upstream !== selected) {
            // Switching identity mid item: drop the previous connection instead of leaking it.
            runCatching { upstream?.close() }
            upstream = selected
        }

        if (!isYouTubeStream(dataSpec.uri)) {
            // Local files, HLS playlists, artwork: exactly the original behaviour.
            passthrough = true
            nextPosition = dataSpec.position
            requestEnd = if (dataSpec.length == C.LENGTH_UNSET.toLong()) -1L
            else dataSpec.position + dataSpec.length - 1L
            return selected.open(dataSpec)
        }

        passthrough = false
        totalBytes = StreamSizes.sizeOf(dataSpec.uri.toString())
        val ranged = rangeSpecFor(dataSpec, dataSpec.position)
        val opened = selected.open(ranged)
        // Only now does the response carry the size (see [learnSize]).
        learnSize(ranged.uri)
        return opened
    }

    /** Upstream for [uri]'s client, created on first use and reused afterwards. */
    private fun upstreamFor(uri: Uri): DataSource {
        val userAgent = userAgentFor(uri)
        return upstreams.getOrPut(userAgent) {
            upstreamFactoryFor(userAgent).createDataSource().also { dataSource ->
                transferListener?.let { dataSource.addTransferListener(it) }
            }
        }
    }

    override fun close() {
        endOfInput = false
        upstreams.values.forEach { runCatching { it.close() } }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = upstream ?: return C.RESULT_END_OF_INPUT
        if (passthrough) return source.read(buffer, offset, length)

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
            val read = source.read(buffer, offset, toRead)
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
        val source = upstream ?: return false
        val position = nextPosition
        if (totalBytes > 0L && position >= totalBytes) return false
        if (!force && position <= requestEnd) return false
        val end = if (totalBytes > 0L) totalBytes - 1L else position + CHUNK_BYTES - 1L
        if (end < position) return false
        return try {
            source.close()
            val ranged = rangeSpecFor(spec, position)
            source.open(ranged)
            learnSize(ranged.uri)
            true
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // YouTube refuses the *rest* of a stream it is not willing to serve: a signature not
            // bound to this client, a missing PO token, an expired URL. What it does serve is the
            // beginning of the file, so the failure always arrives on a later range request.
            // Returning `false` here - as this used to - looked like a clean end of file and cut
            // the track off mid way with no recovery. Letting the exception through reaches
            // ExoPlayer, which swaps in the next resolved URL, and that is the whole difference
            // between "buffers forever, then a source error" and playing through.
            throw e
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
        return dataSpec.buildUpon()
            .setPosition(position)
            .setLength(requestEnd - position + 1L)
            .build()
    }

    /**
     * Reads the file size out of the response's `Content-Range` header
     * (`bytes 0-1048575/3449447`), so a chunked start upgrades itself to a single closed range
     * for the rest of the file.
     */
    private fun learnSize(uri: Uri) {
        if (totalBytes > 0L) return
        val contentRange = getResponseHeaders().entries
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

