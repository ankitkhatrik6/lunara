package com.dhunya.app.data.remote.music

import com.dhunya.app.data.remote.innertube.AudioStream
import com.dhunya.app.data.remote.innertube.InnerTubeApi
import com.dhunya.app.data.remote.innertube.InnerTubeClient
import com.dhunya.app.data.remote.innertube.InnerTubeClients
import com.dhunya.app.data.remote.innertube.InnerTubeParams
import com.dhunya.app.data.remote.innertube.InnerTubeParser
import com.dhunya.app.data.remote.innertube.VideoInfo
import com.dhunya.app.domain.model.Album
import com.dhunya.app.domain.model.Artist
import com.dhunya.app.domain.model.PlayableMedia
import com.dhunya.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music catalogue over the InnerTube API.
 *
 * Mirrors Blazify's flow (from `blazify/innertube` + `YouTube.kt`):
 *  - catalogue/search via `WEB_REMIX` (search/browse endpoints)
 *  - playable audio via `player` endpoint with the audio-friendly client
 *    chain (ANDROID_VR -> IOS -> TVHTML5_EMBEDDED -> WEB_REMIX), picking the
 *    best audio-only adaptive format — exactly how Blazify's PlayerConnection
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
        val isHls: Boolean = false
    )

    /**
     * Resolves the audio of a track, failing over between the client identities.
     *
     * An HLS playlist wins whenever a client hands one out (HLS is not gated by YouTube's
     * "PO token" requirement that limits the direct https formats), otherwise the best
     * audio-only adaptive format is used. Every candidate is verified with a ranged probe
     * first: YouTube answers `403` to any request that is not a *closed* byte range, and
     * capped/limited URLs must not reach the player.
     */
    suspend fun resolveAudioStream(videoId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")
        for (client in InnerTubeClients.STREAM_CLIENTS) {
            val root = api.player(
                client = client,
                videoId = cleanId,
                signatureTimestamp = signatureTimestampFor(client)
            ) ?: continue

            InnerTubeParser.parseHlsManifestUrl(root)?.let { manifest ->
                if (probeStream(manifest, client.userAgent, ranged = false)) {
                    return@withContext ResolvedStream(
                        url = manifest,
                        mimeType = "application/x-mpegURL",
                        isHls = true
                    )
                }
            }

            val candidates = InnerTubeParser.parseAudioStreams(root)
                .filter { it.url.startsWith("http") }
                // m4a (mp4) first: ExoPlayer's MP4 extractor handles seeks on these far
                // better than the webm/opus progressive streams, then highest bitrate.
                .sortedWith(
                    compareByDescending<AudioStream> { it.mimeType.contains("mp4") }
                        .thenByDescending { it.bitrate }
                )
            for (format in candidates) {
                if (probeStream(format.url, client.userAgent, ranged = true)) {
                    return@withContext ResolvedStream(
                        url = format.url,
                        contentLength = format.contentLength,
                        mimeType = format.mimeType
                    )
                }
            }
        }
        null
    }

    /**
     * Verifies that [url] really serves bytes: `*.googlevideo.com` rejects requests without
     * a closed `Range` header with `403`, so a lazily resolved URL can be dead on arrival.
     */
    private fun probeStream(url: String, userAgent: String, ranged: Boolean): Boolean = try {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        if (ranged) builder.header("Range", "bytes=0-1023")
        okHttpClient.newCall(builder.build()).execute().use { response ->
            response.isSuccessful
        }
    } catch (e: Exception) {
        false
    }

    suspend fun resolvePlayableMedia(song: Song): PlayableMedia = withContext(Dispatchers.IO) {
        song.localUri?.let { return@withContext PlayableMedia(song, it, isLocal = true) }
        song.streamUrl?.takeIf { it.isNotBlank() }?.let {
            return@withContext PlayableMedia(song, it, isLocal = false)
        }
        val stream = resolveAudioStream(song.id)
            ?: throw IllegalStateException(
                "YouTube did not return a playable stream for \"${song.title}\""
            )
        PlayableMedia(
            song.copy(streamUrl = stream.url),
            stream.url,
            isLocal = false,
            mimeType = stream.mimeType,
            isHls = stream.isHls
        )
    }
}

