package com.lunara.app.domain.usecase

import com.lunara.app.core.result.Resource
import com.lunara.app.domain.model.Album
import com.lunara.app.domain.model.Artist
import com.lunara.app.domain.model.Lyrics
import com.lunara.app.domain.model.Playlist
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.DownloadRepository
import com.lunara.app.domain.repository.LibraryRepository
import com.lunara.app.domain.repository.LyricsRepository
import com.lunara.app.domain.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class SearchResult(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList()
)

class SearchMusicUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(query: String): Resource<SearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return Resource.Success(SearchResult())
        }
        
        musicRepository.saveRecentSearch(trimmed)
        
        val songsRes = musicRepository.searchSongs(trimmed)
        val artistsRes = musicRepository.searchArtists(trimmed)
        val albumsRes = musicRepository.searchAlbums(trimmed)
        
        val songs = if (songsRes is Resource.Success) songsRes.data else emptyList()
        val artists = if (artistsRes is Resource.Success) artistsRes.data else emptyList()
        val albums = if (albumsRes is Resource.Success) albumsRes.data else emptyList()
        
        return if (songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && songsRes is Resource.Error) {
            Resource.Error(songsRes.message)
        } else {
            Resource.Success(SearchResult(songs = songs, artists = artists, albums = albums))
        }
    }
}

class GetLyricsUseCase @Inject constructor(
    private val lyricsRepository: LyricsRepository
) {
    suspend operator fun invoke(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null
    ): Resource<Lyrics> {
        return lyricsRepository.getLyrics(trackName, artistName, albumName, durationSeconds)
    }
}

class ToggleFavoriteUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(song: Song): Boolean {
        return libraryRepository.toggleFavorite(song)
    }
}

class GetFavoritesUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(): Flow<List<Song>> = libraryRepository.getFavorites()
}

class GetHistoryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(): Flow<List<Song>> = libraryRepository.getHistory()
    suspend fun add(song: Song) = libraryRepository.addToHistory(song)
    suspend fun clear() = libraryRepository.clearHistory()
}

class ManagePlaylistUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    fun getPlaylists(): Flow<List<Playlist>> = libraryRepository.getPlaylists()
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> = libraryRepository.getPlaylistSongs(playlistId)
    suspend fun create(name: String, description: String? = null): Long = libraryRepository.createPlaylist(name, description)
    suspend fun delete(playlistId: Long) = libraryRepository.deletePlaylist(playlistId)
    suspend fun rename(playlistId: Long, name: String) = libraryRepository.renamePlaylist(playlistId, name)
    suspend fun addSong(playlistId: Long, song: Song) = libraryRepository.addSongToPlaylist(playlistId, song)
    suspend fun removeSong(playlistId: Long, songId: String) = libraryRepository.removeSongFromPlaylist(playlistId, songId)
}

class ManageDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    fun getDownloads() = downloadRepository.getDownloads()
    suspend fun download(song: Song) = downloadRepository.startDownload(song)
    suspend fun cancel(songId: String) = downloadRepository.cancelDownload(songId)
    suspend fun remove(songId: String) = downloadRepository.removeDownload(songId)
}
