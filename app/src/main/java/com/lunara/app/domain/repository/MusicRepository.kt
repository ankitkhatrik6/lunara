package com.lunara.app.domain.repository

import com.lunara.app.core.result.Resource
import com.lunara.app.domain.model.Album
import com.lunara.app.domain.model.Artist
import com.lunara.app.domain.model.PlayableMedia
import com.lunara.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun searchSongs(query: String): Resource<List<Song>>
    suspend fun searchArtists(query: String): Resource<List<Artist>>
    suspend fun searchAlbums(query: String): Resource<List<Album>>
    suspend fun getSong(id: String): Resource<Song>
    suspend fun getAlbumTracks(albumId: String): Resource<List<Song>>
    suspend fun resolvePlayableMedia(song: Song, forceRefresh: Boolean = false): Resource<PlayableMedia>
    suspend fun getLocalSongs(): List<Song>
    suspend fun getRecentSearches(): List<String>
    suspend fun saveRecentSearch(query: String)
    suspend fun clearRecentSearches()
}

interface LyricsRepository {
    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null
    ): Resource<com.lunara.app.domain.model.Lyrics>
}

interface LibraryRepository {
    fun getFavorites(): Flow<List<Song>>

    /**
     * Idempotently stores [favorite] for [song] and returns the stored value. Preferred over
     * [toggleFavorite] because rapid taps converge instead of flipping twice.
     */
    suspend fun setFavorite(song: Song, favorite: Boolean): Boolean

    suspend fun toggleFavorite(song: Song): Boolean
    suspend fun isFavorite(songId: String): Boolean
    
    fun getHistory(): Flow<List<Song>>
    suspend fun addToHistory(song: Song)
    suspend fun clearHistory()
    
    fun getPlaylists(): Flow<List<com.lunara.app.domain.model.Playlist>>
    suspend fun createPlaylist(name: String, description: String? = null): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>
    suspend fun addSongToPlaylist(playlistId: Long, song: Song)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
}

interface DownloadRepository {
    fun getDownloads(): Flow<List<com.lunara.app.domain.model.DownloadItem>>
    suspend fun startDownload(song: Song)
    suspend fun cancelDownload(songId: String)
    suspend fun removeDownload(songId: String)
    suspend fun isDownloaded(songId: String): Boolean
}

interface SettingsRepository {
    val settingsFlow: Flow<com.lunara.app.domain.model.UserSettings>
    suspend fun updateHighQuality(enabled: Boolean)
    suspend fun updateAutoPlay(enabled: Boolean)
    suspend fun updateOfflineMode(enabled: Boolean)
    suspend fun updateLyricsFontSize(size: Float)
}
