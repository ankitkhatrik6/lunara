package com.lunara.app.data.remote.music

import com.lunara.app.domain.model.Album
import com.lunara.app.domain.model.Artist
import com.lunara.app.domain.model.PlayableMedia
import com.lunara.app.domain.model.RadioPage
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

    /**
     * YouTube Music's own "up next" for [song]: the mix of similar tracks the service builds
     * around it, with the token that pages through the rest of it.
     */
    suspend fun upNext(song: Song, continuation: String? = null): RadioPage =
        youTubeMusic.upNext(song, continuation)

    suspend fun getSong(id: String): Song? =
        youTubeMusic.getSong(id)

    suspend fun resolvePlayableMedia(song: Song, forceRefresh: Boolean = false): PlayableMedia =
        youTubeMusic.resolvePlayableMedia(song, forceRefresh)

    /**
     * Progressive audio only, for saving a track.
     *
     * A download must be real audio: saving an HLS playlist produces a file ExoPlayer rejects with
     * "unsupported audio format" (3003), which is why downloaded tracks used to be unplayable.
     */
    suspend fun resolveDownloadableMedia(song: Song): PlayableMedia =
        youTubeMusic.resolvePlayableMedia(song, forceRefresh = false, allowHls = false)
}

