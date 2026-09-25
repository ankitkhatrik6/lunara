package com.dhunya.app.domain.model

data class Song(
    val id: String,
    val title: String,
    val artistName: String,
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val streamUrl: String? = null,
    val localUri: String? = null,
    val isDownloaded: Boolean = false,
    val isFavorite: Boolean = false
)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val monthlyListeners: Long? = null
)

data class Album(
    val id: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val songCount: Int = 0
)

data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val songCount: Int = 0,
    val coverArtworkUrl: String? = null
)
