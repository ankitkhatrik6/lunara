package com.lunara.app.domain.repository

import com.lunara.app.core.result.Resource
import com.lunara.app.domain.model.Album
import com.lunara.app.domain.model.Artist
import com.lunara.app.domain.model.PlayableMedia
import com.lunara.app.domain.model.RadioPage
import com.lunara.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun searchSongs(query: String): Resource<List<Song>>
    suspend fun searchArtists(query: String): Resource<List<Artist>>
    suspend fun searchAlbums(query: String): Resource<List<Album>>
    suspend fun getSong(id: String): Resource<Song>
    suspend fun getAlbumTracks(albumId: String): Resource<List<Song>>
    suspend fun resolvePlayableMedia(song: Song, forceRefresh: Boolean = false): Resource<PlayableMedia>

    /**
     * YouTube Music's own "up next" for [song]: the mix of similar tracks the service itself
     * would keep playing from that track, which is what the player appends to the queue once
     * the user's own picks have run out.
     *
     * [continuation] is the token a previous page returned; passing `null` starts the mix for
     * [song]. A page with no [RadioPage.continuation] means YouTube has no more suggestions
     * for that seed.
     */
    suspend fun getUpNext(song: Song, continuation: String? = null): Resource<RadioPage>

    /**
     * YouTube Music's own home feed, shelf by shelf.
     *
     * The whole page arrives in one response, so the feed is the service's real sections - new
     * releases, the moods and genres chooser, new music videos - instead of rails Lunara guessed at.
     */
    suspend fun getHomeShelves(): Resource<List<BrowseShelf>>

    /**
     * The shelves of one browse page. [params] picks the mood or genre out of the page they share,
     * and is passed on untouched.
     */
    suspend fun getBrowseShelves(browseId: String, params: String? = null): Resource<List<BrowseShelf>>

    /**
     * Re-reads the device's own audio library (MediaStore) and mirrors it into the local
     * database, so the tracks stay available offline and across restarts.
     *
     * Returns an empty list when the runtime permission has not been granted (see
     * [canReadDeviceAudio]) as well as when the device genuinely holds no music.
     */
    suspend fun getLocalSongs(): List<Song>

    /**
     * The device's own tracks as stored by the last [getLocalSongs] sync, observed straight from
     * the local database.
     *
     * Read from the database rather than kept in memory on purpose: the Library can show the
     * device's music the instant the screen opens, without permission, without touching
     * MediaStore, and without a scan that the user never asked for.
     */
    fun observeLocalSongs(): Flow<List<Song>>

    /** True when Lunara already holds the runtime permission needed to read device audio. */
    fun canReadDeviceAudio(): Boolean

    /**
     * The one runtime permission Android asks for to read device audio on this OS version:
     * `READ_MEDIA_AUDIO` from Android 13 on, `READ_EXTERNAL_STORAGE` below it.
     */
    fun deviceAudioPermission(): String

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

    /**
     * Marks a saved copy as unusable: a damaged file, or a playlist that was saved as audio and
     * is rejected by the player as "unsupported audio format".
     *
     * The bytes are left on disk - they cost nothing, and seeing the failed row is more honest
     * than silently deleting the user's download - but nothing will try to play them again.
     */
    suspend fun markLocalCopyUnusable(songId: String)
}

interface SettingsRepository {
    val settingsFlow: Flow<com.lunara.app.domain.model.UserSettings>
    suspend fun updateHighQuality(enabled: Boolean)
    suspend fun updateAutoPlay(enabled: Boolean)

    /**
     * Whether launching Lunara brings back the queue and the playhead of the previous run. Turning
     * it off leaves the player empty on every cold start, and clears nothing that is already
     * stored - the stored session is simply never restored.
     */
    suspend fun updateResumePlayback(enabled: Boolean)
    suspend fun updateOfflineMode(enabled: Boolean)
    suspend fun updateLyricsFontSize(size: Float)
    suspend fun updateThemeMode(mode: com.lunara.app.domain.model.ThemeMode)

    /**
     * Material You. When on (and the device runs Android 12+) the palette is derived from the
     * wallpaper instead of the Lunara brand shades; older devices keep the brand palette.
     */
    suspend fun updateDynamicColor(enabled: Boolean)
}
