package com.lunara.app.data.remote.music

import com.lunara.app.domain.model.Album
import com.lunara.app.domain.model.Artist
import com.lunara.app.domain.model.PlayableMedia
import com.lunara.app.domain.model.Song
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outer contract for Lunara's online catalogue.
 *
 * Implementation detail: the catalogue is YouTube Music (InnerTube), the same
 * transport Blazify uses (search -> browse/album -> player -> audio stream).
 * The old SoundHelix/picsum placeholders were removed.
 */
@Singleton
class MusicRemoteDataSource @Inject constructor(
    private val youTubeMusic: YouTubeMusicRemoteDataSource,
) {
    suspend fun searchSongs(query: String): List<Song> =
        youTubeMusic.searchSongs(query)

    suspend fun searchArtists(query: String): List<Artist> =
        youTubeMusic.searchArtists(query)

    suspend fun searchAlbums(query: String): List<Album> =
        youTubeMusic.searchAlbums(query)

    suspend fun getAlbumTracks(albumId: String): List<Song> =
        youTubeMusic.getAlbumTracks(albumId)

    suspend fun getSong(id: String): Song? =
        youTubeMusic.getSong(id)

    suspend fun resolvePlayableMedia(song: Song): PlayableMedia =
        youTubeMusic.resolvePlayableMedia(song)
}

