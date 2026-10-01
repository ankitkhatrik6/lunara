package com.lunara.app.domain.model

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

/**
 * One page of the mix YouTube Music builds around a single track - its "song radio".
 *
 * [songs] holds real, playable recommendations: the same list the official app shows under
 * "Up next" the moment a track starts, which is what makes Lunara keep playing *this kind* of
 * music instead of looping the queue. [continuation] fetches the following batch and is `null`
 * once YouTube has no further suggestions for that seed; [title] is YouTube Music's own name
 * for the mix ("Never Gonna Give You Up Mix").
 */
data class RadioPage(
    val songs: List<Song> = emptyList(),
    val continuation: String? = null,
    val title: String? = null
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
