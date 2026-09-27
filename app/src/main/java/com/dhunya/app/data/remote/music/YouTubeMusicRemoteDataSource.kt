package com.dhunya.app.data.remote.music

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

    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")
        for (client in InnerTubeClients.STREAM_CLIENTS) {
            val root = api.player(
                client = client,
                videoId = cleanId,
                signatureTimestamp = signatureTimestampFor(client)
            ) ?: continue
            val best = InnerTubeParser.parseAudioStreams(root).maxByOrNull { it.bitrate }
            if (best != null) return@withContext best.url
        }
        null
    }

    suspend fun resolvePlayableMedia(song: Song): PlayableMedia = withContext(Dispatchers.IO) {
        song.localUri?.let { return@withContext PlayableMedia(song, it, isLocal = true) }
        song.streamUrl?.takeIf { it.isNotBlank() }?.let {
            return@withContext PlayableMedia(song, it, isLocal = false)
        }
        val videoId = song.id.removePrefix("yt_")
        val url = resolveAudioStreamUrl(videoId)
            ?: throw IllegalStateException("No playable audio stream for ${song.title}")
        PlayableMedia(song.copy(streamUrl = url), url, isLocal = false)
    }
}

