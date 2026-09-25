package com.dhunya.app.data.remote.music

import com.dhunya.app.domain.model.Album
import com.dhunya.app.domain.model.Artist
import com.dhunya.app.domain.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music extractor and streaming source for Dhunya.
 * Compatible with YouTube Music's InnerTube API and Piped instances (used by ViMusic / InnerTune).
 * Enables searching millions of tracks, artists, and streaming Opus/M4A audio directly in ExoPlayer.
 */
@Singleton
class YouTubeMusicRemoteDataSource @Inject constructor(
    private val httpClient: HttpClient
) {
    private val pipedInstances = listOf(
        "https://api.piped.privacydev.net",
        "https://pipedapi.kavin.rocks",
        "https://api.piped.yt"
    )

    private var activeInstance = pipedInstances.first()

    /**
     * Searches YouTube Music for songs matching [query].
     */
    suspend fun searchYouTubeMusic(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            // Query Piped / InnerTube search endpoint
            val response: List<PipedSearchResultItem> = httpClient.get("$activeInstance/search") {
                parameter("q", query)
                parameter("filter", "music_songs")
            }.body()

            response.filter { it.type == "stream" }.map { item ->
                Song(
                    id = "yt_${item.url.substringAfter("watch?v=")}",
                    title = item.title ?: "Unknown Track",
                    artistName = item.uploaderName ?: "YouTube Music Artist",
                    albumName = null,
                    artworkUrl = item.thumbnail ?: "https://picsum.photos/seed/${item.title}/800/800",
                    durationMs = (item.duration ?: 180) * 1000L,
                    streamUrl = null, // resolved on play via resolveAudioStreamUrl
                    isDownloaded = false,
                    isFavorite = false
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback or empty if offline / blocked
            emptyList()
        }
    }

    /**
     * Resolves the direct audio-only playback stream (Opus/M4A 128-256kbps) for a YouTube video ID.
     * The resulting URL can be passed directly to AndroidX Media3 ExoPlayer.
     */
    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")
        try {
            val response: PipedStreamResponse = httpClient.get("$activeInstance/streams/$cleanId").body()

            // Pick highest quality audio-only stream (m4a or webm/opus)
            val audioStream = response.audioStreams
                .filter { it.mimeType.contains("audio") }
                .maxByOrNull { it.bitrate ?: 0 }

            audioStream?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Direct YouTube InnerTube Music API payload helper for native client-side extraction.
     */
    suspend fun queryInnerTubeSearch(query: String): String = withContext(Dispatchers.IO) {
        val bodyPayload = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20231201.01.00",
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "query": "$query",
                "params": "Eg-KAQwIABAAGAAgACgAMABqChAMEAMQBBAFEAo="
            }
        """.trimIndent()

        httpClient.post("https://music.youtube.com/youtubei/v1/search") {
            contentType(ContentType.Application.Json)
            header("Origin", "https://music.youtube.com")
            setBody(bodyPayload)
        }.body()
    }
}

@Serializable
data class PipedSearchResultItem(
    val url: String = "",
    val type: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val duration: Long? = null
)

@Serializable
data class PipedStreamResponse(
    val title: String = "",
    val description: String = "",
    val uploadDate: String = "",
    val audioStreams: List<PipedAudioStream> = emptyList()
)

@Serializable
data class PipedAudioStream(
    val url: String = "",
    val format: String = "",
    val quality: String = "",
    val mimeType: String = "",
    val codec: String? = null,
    val bitrate: Long? = null
)
