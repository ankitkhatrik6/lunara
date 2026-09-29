package com.lunara.app.data.remote.music

import android.net.Uri
import com.lunara.app.data.remote.innertube.AudioStream
import com.lunara.app.data.remote.innertube.InnerTubeApi
import com.lunara.app.data.remote.innertube.InnerTubeClient
import com.lunara.app.data.remote.innertube.InnerTubeClients
import com.lunara.app.data.remote.innertube.InnerTubeParams
import com.lunara.app.data.remote.innertube.InnerTubeParser
import com.lunara.app.data.remote.innertube.VideoInfo
import com.lunara.app.domain.model.Album
import com.lunara.app.domain.model.Artist
import com.lunara.app.domain.model.PlayableMedia
import com.lunara.app.domain.model.Song
import com.lunara.app.player.StreamSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music catalogue over the InnerTube API.
 *
 * Mirrors Blazify's flow (from `blazify/innertube` + `YouTube.kt`):
 *  - catalogue/search via `WEB_REMIX` (search/browse endpoints)
 *  - playable audio via `player` endpoint with the audio-friendly client
 *    chain (ANDROID_VR -> IOS -> TVHTML5 -> ANDROID), picking the
 *    best audio-only adaptive format - exactly how Blazify's PlayerConnection
 *    opens a track for ExoPlayer/Media3.
 *
 * No third-party proxies, no Piped instances, no placeholder tracks.
 */
@Singleton
class YouTubeMusicRemoteDataSource @Inject constructor(
    private val api: InnerTubeApi,
    private val okHttpClient: OkHttpClient
) {
    /** Cached `signatureTimestamp` of the current player build (fetched lazily). */
    @Volatile
    private var signatureTimestamp: Int? = null

    private suspend fun signatureTimestampFor(client: InnerTubeClient): Int? {
        if (!client.useSignatureTimestamp) return null
        signatureTimestamp?.let { return it }
        return api.fetchSignatureTimestamp()?.also { signatureTimestamp = it }
    }

    // ---------- search ----------

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val root = api.search(InnerTubeClients.WEB_REMIX, query, InnerTubeParams.SONGS)
            ?: return@withContext emptyList()
        InnerTubeParser.parseSongs(root)
    }

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val root = api.search(InnerTubeClients.WEB_REMIX, query, InnerTubeParams.ARTISTS)
            ?: return@withContext emptyList()
        InnerTubeParser.parseItems(root).mapNotNull { item ->
            val browseId = item.browseId ?: return@mapNotNull null
            Artist(
                id = browseId,
                name = item.title,
                imageUrl = item.artworkUrl,
                monthlyListeners = null,
            )
        }
    }

    suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val root = api.search(InnerTubeClients.WEB_REMIX, query, InnerTubeParams.ALBUMS)
            ?: return@withContext emptyList()
        InnerTubeParser.parseItems(root).mapNotNull { item ->
            val browseId = item.browseId ?: item.playlistId ?: return@mapNotNull null
            Album(
                id = browseId,
                title = item.title,
                artistName = item.subtitle?.substringBefore("•")?.trim().orEmpty(),
                artworkUrl = item.artworkUrl,
                releaseYear = null,
                songCount = 0,
            )
        }
    }

    // ---------- album / playlist track listing ----------

    /**
     * Ordered track list for an album (MPRE browse id) or a shared album
     * playlist (OLAK id, e.g. Whole Lotta Red). Same `browse` call Blazify
     * makes before queueing an album for playback.
     */
    suspend fun getAlbumTracks(albumOrPlaylistId: String): List<Song> = withContext(Dispatchers.IO) {
        val id = albumOrPlaylistId.trim()
        val root = when {
            id.startsWith("OLAK") || id.startsWith("VL") || id.startsWith("PL") -> {
                val browseId = if (id.startsWith("VL")) id else "VL$id"
                api.browse(InnerTubeClients.WEB_REMIX, browseId = browseId)
                    ?: api.browse(InnerTubeClients.WEB_REMIX, browseId = id)
            }
            else -> api.browse(InnerTubeClients.WEB_REMIX, browseId = id)
        } ?: return@withContext emptyList()
        InnerTubeParser.parseSongs(root)
    }

    // ---------- single song / stream resolution ----------

    suspend fun getSong(id: String): Song? = withContext(Dispatchers.IO) {
        val videoId = id.removePrefix("yt_")
        var info: VideoInfo? = null
        for (client in InnerTubeClients.STREAM_CLIENTS) {
            val root = api.player(client, videoId, signatureTimestamp = signatureTimestampFor(client))
                ?: continue
            info = InnerTubeParser.parseVideoInfo(root)
            if (info != null) break
        }
        val details = info ?: return@withContext null
        Song(
            id = "yt_${details.videoId}",
            title = details.title ?: "Unknown Track",
            artistName = details.author ?: "Unknown Artist",
            albumName = null,
            artworkUrl = details.artworkUrl,
            durationMs = details.durationMs,
            streamUrl = null,
        )
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? = resolveAudioStream(videoId)?.url

    /** A playable audio source: a direct https stream or an HLS master playlist. */
    data class ResolvedStream(
        val url: String,
        val contentLength: Long = 0L,
        /** Full mime type from the InnerTube format, e.g. `audio/mp4; codecs="mp4a.40.2"`. */
        val mimeType: String? = null,
        val isHls: Boolean = false,
        /** Ready alternatives for the same track, best first. */
        val fallbackUrls: List<String> = emptyList()
    )

    /** One audio URL plus the metadata needed to hand it to ExoPlayer. */
    private data class Candidate(
        val url: String,
        val mimeType: String?,
        val contentLength: Long,
        val isHls: Boolean
    )

    private data class CachedStream(val stream: ResolvedStream, val resolvedAtMs: Long)

    /** Keeps a track's URLs for a few minutes so repeat plays start instantly. */
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    /**
     * Resolves the audio of a track, failing over between the client identities.
     *
     * Every client's formats are collected instead of stopping at the first usable one, so the
     * player has spare URLs to fall back on. An HLS playlist wins whenever a client hands one
     * out (HLS is not gated by YouTube's "PO token" requirement that limits the direct https
     * formats). Candidates are verified with a ranged probe — YouTube answers `403` to any
     * request that is not a *closed* byte range — but a track is still handed over when no
     * probe succeeds, because some CDN edges reject this probe while serving ExoPlayer fine.
     * That last resort is what turns the old "source error" dead ends into playable tracks.
     */
    suspend fun resolveAudioStream(
        videoId: String,
        forceRefresh: Boolean = false
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")

        if (!forceRefresh) {
            streamCache[cleanId]
                ?.takeIf { System.currentTimeMillis() - it.resolvedAtMs < STREAM_CACHE_TTL_MS }
                ?.let { return@withContext it.stream }
        }

        val verified = LinkedHashSet<Candidate>()
        val unverified = LinkedHashSet<Candidate>()

        for (client in InnerTubeClients.STREAM_CLIENTS) {
            val root = api.player(
                client = client,
                videoId = cleanId,
                signatureTimestamp = signatureTimestampFor(client)
            ) ?: continue

            InnerTubeParser.parseHlsManifestUrl(root)?.let { manifest ->
                val candidate = Candidate(manifest, "application/x-mpegURL", 0L, isHls = true)
                if (probeStream(manifest, ranged = false)) verified.add(candidate)
                else unverified.add(candidate)
            }

            val formats = InnerTubeParser.parseAudioStreams(root)
                .filter { it.url.startsWith("http") }
                // m4a (mp4) first: ExoPlayer's MP4 extractor handles seeks on these far
                // better than the webm/opus progressive streams, then highest bitrate.
                .sortedWith(
                    compareByDescending<AudioStream> { it.mimeType.contains("mp4") }
                        .thenByDescending { it.bitrate }
                )

            for (format in formats.take(MAX_CANDIDATES_PER_CLIENT)) {
                val candidate = Candidate(
                    url = format.url,
                    mimeType = format.mimeType,
                    contentLength = format.contentLength,
                    isHls = false
                )
                if (verified.size < PREFERRED_VERIFIED_CANDIDATES &&
                    probeStream(format.url, ranged = true)
                ) {
                    verified.add(candidate)
                } else {
                    unverified.add(candidate)
                }
            }

            if (verified.size >= PREFERRED_VERIFIED_CANDIDATES) break
        }

        val ordered = (verified + unverified).distinctBy { it.url }
        val best = ordered.firstOrNull() ?: return@withContext null

        // Remember every candidate's size. The player closes its byte ranges with it, and the
        // fallback URLs are swapped in mid-track without another resolve, so all of them have
        // to be known up front.
        ordered.forEach { StreamSizes.remember(it.url, it.contentLength) }

        ResolvedStream(
            url = best.url,
            contentLength = best.contentLength,
            mimeType = best.mimeType,
            isHls = best.isHls,
            fallbackUrls = ordered.drop(1).map { it.url }
        ).also { streamCache[cleanId] = CachedStream(it, System.currentTimeMillis()) }
    }

    /**
     * Verifies that [url] really serves bytes, using the *same* user agent the player will use,
     * so a probe can never pass where playback would have been rejected.
     *
     * [ranged] probes with a 1 KiB closed range, which keeps the check cheap. A failed ranged
     * probe is retried without the header, because that is how ExoPlayer itself asks for a
     * track: declaring a URL dead on a ranged probe alone threw away playable streams.
     */
    private fun probeStream(url: String, ranged: Boolean): Boolean =
        probe(url, ranged) || (ranged && probe(url, ranged = false))

    private fun probe(url: String, ranged: Boolean): Boolean = try {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", InnerTubeClients.STREAM_USER_AGENT)
        if (ranged) builder.header("Range", "bytes=0-1023")
        okHttpClient.newCall(builder.build()).execute().use { response ->
            response.isSuccessful
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Opens [song] for the player.
     *
     * Only two things are trusted as-is: a `content://` URI and a `file://` URI that really
     * exists. Everything else is resolved again, because a stored [Song.streamUrl] is a
     * *signed* URL that YouTube invalidates within minutes. Handing a dead one to ExoPlayer
     * produced a "source error" that could never recover: the retry path asked this same
     * method for a URL again and got the same expired signature back.
     *
     * Re-resolving is cheap - [resolveAudioStream] serves repeat plays from its own cache while
     * the URL is still valid - so freshness costs nothing in the common case.
     *
     * [forceRefresh] additionally bypasses that cache, used when the player reports that the
     * URL it was handed has just stopped working.
     */
    suspend fun resolvePlayableMedia(
        song: Song,
        forceRefresh: Boolean = false
    ): PlayableMedia = withContext(Dispatchers.IO) {
        song.localUri?.takeIf { it.isUsableLocalUri() }?.let {
            return@withContext PlayableMedia(song, it, isLocal = true)
        }

        // Non-YouTube sources (imported media) have no resolver behind them, so their stored
        // URL is all there is. YouTube Music tracks are always resolved fresh.
        if (!forceRefresh && !song.id.startsWith(YOUTUBE_ID_PREFIX)) {
            song.streamUrl?.takeIf { it.isNotBlank() }?.let {
                return@withContext PlayableMedia(song, it, isLocal = false)
            }
        }

        val stream = resolveAudioStream(song.id, forceRefresh = forceRefresh)
            ?: throw IllegalStateException(
                "YouTube did not return a playable stream for \"${song.title}\""
            )
        PlayableMedia(
            song.copy(streamUrl = stream.url, localUri = null),
            stream.url,
            isLocal = false,
            mimeType = stream.mimeType,
            isHls = stream.isHls,
            contentLength = stream.contentLength,
            fallbackUris = stream.fallbackUrls
        )
    }

    /**
     * `true` when the receiver is a local URI that ExoPlayer can actually open.
     *
     * A downloaded track whose file has been deleted (app data cleared, storage trimmed) must
     * fall through to streaming instead of being handed over as a dead `file://` URI, which
     * ExoPlayer reports as a "source error" that never recovers.
     */
    private fun String.isUsableLocalUri(): Boolean {
        if (isBlank()) return false
        if (!startsWith("file:")) return true
        val path = runCatching { Uri.parse(this).path }.getOrNull() ?: return false
        return File(path).let { it.exists() && it.length() > 0L }
    }

    private companion object {
        /** How long a resolved stream stays reusable before it is resolved again. */
        const val STREAM_CACHE_TTL_MS = 10 * 60 * 1000L

        /** Lunara stores every YouTube Music track under a `yt_<videoId>` id. */
        const val YOUTUBE_ID_PREFIX = "yt_"

        /** How many formats are probed per client identity. */
        const val MAX_CANDIDATES_PER_CLIENT = 4

        /**
         * Stop asking further clients once this many candidates are verified playable.
         *
         * Deliberately low: each probe is a blocking round trip, so insisting on three verified
         * URLs delayed the first note by up to a second. Two is enough to fail over, and the
         * remaining formats of the same client still join the list unprobed.
         */
        const val PREFERRED_VERIFIED_CANDIDATES = 2
    }
}

