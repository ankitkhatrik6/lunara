package com.dhunya.app.data.remote.music

import com.dhunya.app.domain.model.Album
import com.dhunya.app.domain.model.Artist
import com.dhunya.app.domain.model.PlayableMedia
import com.dhunya.app.domain.model.Song
import io.ktor.client.HttpClient
import javax.inject.Inject

class MusicRemoteDataSource @Inject constructor(
    private val httpClient: HttpClient
) {
    // Curated high quality tracks with verified royalty-free audio streams for seamless offline/online playback
    private val catalog = listOf(
        Song(
            id = "dhunya_1",
            title = "Midnight Horizon",
            artistName = "Aura Bloom",
            albumName = "Elysian Dreams",
            artworkUrl = "https://picsum.photos/seed/midnight_horizon/800/800",
            durationMs = 214000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        ),
        Song(
            id = "dhunya_2",
            title = "Neon Reverie",
            artistName = "Kavya & The Synths",
            albumName = "Cyber Odyssey",
            artworkUrl = "https://picsum.photos/seed/neon_reverie/800/800",
            durationMs = 188000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
        ),
        Song(
            id = "dhunya_3",
            title = "Golden Dust",
            artistName = "Sufi Echoes",
            albumName = "Mirage Dunes",
            artworkUrl = "https://picsum.photos/seed/golden_dust/800/800",
            durationMs = 245000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
        ),
        Song(
            id = "dhunya_4",
            title = "Electric Rainfall",
            artistName = "Lunar Cascade",
            albumName = "Atmosphere",
            artworkUrl = "https://picsum.photos/seed/electric_rainfall/800/800",
            durationMs = 202000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
        ),
        Song(
            id = "dhunya_5",
            title = "Whispers of the Monsoon",
            artistName = "Tara Ananya",
            albumName = "Cloudburst",
            artworkUrl = "https://picsum.photos/seed/monsoon_whispers/800/800",
            durationMs = 267000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
        ),
        Song(
            id = "dhunya_6",
            title = "Chai & Vinyl",
            artistName = "The Bangalore Lo-Fi Club",
            albumName = "Sunday Mornings",
            artworkUrl = "https://picsum.photos/seed/chai_vinyl/800/800",
            durationMs = 175000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"
        ),
        Song(
            id = "dhunya_7",
            title = "Solar Flares",
            artistName = "Aura Bloom",
            albumName = "Elysian Dreams",
            artworkUrl = "https://picsum.photos/seed/solar_flares/800/800",
            durationMs = 230000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3"
        ),
        Song(
            id = "dhunya_8",
            title = "Silk Road Transit",
            artistName = "Caravan Noir",
            albumName = "Across Horizons",
            artworkUrl = "https://picsum.photos/seed/silk_road/800/800",
            durationMs = 290000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"
        )
    )

    fun searchSongs(query: String): List<Song> {
        val q = query.lowercase().trim()
        return if (q.isEmpty()) catalog else {
            catalog.filter {
                it.title.lowercase().contains(q) ||
                it.artistName.lowercase().contains(q) ||
                (it.albumName?.lowercase()?.contains(q) == true)
            }
        }
    }

    fun searchArtists(query: String): List<Artist> {
        val q = query.lowercase().trim()
        val distinctArtists = catalog.map { it.artistName }.distinct()
        val filtered = if (q.isEmpty()) distinctArtists else distinctArtists.filter { it.lowercase().contains(q) }
        return filtered.mapIndexed { idx, name ->
            Artist(
                id = "artist_$idx",
                name = name,
                imageUrl = "https://picsum.photos/seed/artist_$idx/600/600",
                monthlyListeners = 120_000L + (idx * 45_000L)
            )
        }
    }

    fun searchAlbums(query: String): List<Album> {
        val q = query.lowercase().trim()
        val distinctAlbums = catalog.mapNotNull { song ->
            song.albumName?.let { album -> Triple(album, song.artistName, song.artworkUrl) }
        }.distinctBy { it.first }

        val filtered = if (q.isEmpty()) distinctAlbums else distinctAlbums.filter { it.first.lowercase().contains(q) }
        return filtered.mapIndexed { idx, (title, artist, art) ->
            Album(
                id = "album_$idx",
                title = title,
                artistName = artist,
                artworkUrl = art,
                releaseYear = 2024,
                songCount = catalog.count { it.albumName == title }
            )
        }
    }

    fun getSong(id: String): Song? {
        return catalog.firstOrNull { it.id == id }
    }

    fun resolvePlayableMedia(song: Song): PlayableMedia {
        val stream = song.streamUrl ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        return PlayableMedia(
            song = song,
            mediaUri = stream,
            isLocal = false
        )
    }
}
