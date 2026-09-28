package com.lunara.app.data.remote.lyrics

import com.lunara.app.core.constants.AppConstants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class LrclibResponse(
    val id: Long? = null,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

class LrclibLyricsApi @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null
    ): LrclibResponse? {
        return try {
            httpClient.get("${AppConstants.LRCLIB_BASE_URL}/get") {
                parameter("track_name", trackName)
                parameter("artist_name", artistName)
                if (!albumName.isNullOrBlank()) parameter("album_name", albumName)
                if (durationSeconds != null && durationSeconds > 0) parameter("duration", durationSeconds)
            }.body<LrclibResponse>()
        } catch (e: Exception) {
            // Fallback to search endpoint if exact match wasn't found
            searchFallback(trackName, artistName)
        }
    }

    private suspend fun searchFallback(trackName: String, artistName: String): LrclibResponse? {
        return try {
            val results = httpClient.get("${AppConstants.LRCLIB_BASE_URL}/search") {
                parameter("q", "$trackName $artistName")
            }.body<List<LrclibResponse>>()
            results.firstOrNull { 
                it.plainLyrics != null || it.syncedLyrics != null 
            }
        } catch (e: Exception) {
            null
        }
    }
}
