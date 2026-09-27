package com.dhunya.app.data.repository

import com.dhunya.app.core.result.Resource
import com.dhunya.app.data.local.PreferencesDataStore
import com.dhunya.app.data.local.dao.*
import com.dhunya.app.data.local.entity.*
import com.dhunya.app.data.mapper.toDomain
import com.dhunya.app.data.mapper.toEntity
import com.dhunya.app.data.remote.lyrics.LrclibLyricsApi
import com.dhunya.app.data.remote.music.MusicRemoteDataSource
import com.dhunya.app.domain.model.*
import com.dhunya.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val WHOLE_LOTTA_RED_PLAYLIST_ID = "OLAK5uy_lW6cMszmMtqMeepM6dSApqU1K2meB4ajE"

class MusicRepositoryImpl @Inject constructor(
    private val remoteSource: MusicRemoteDataSource,
    private val localAudioSource: com.dhunya.app.data.local.LocalAudioDataSource,
    private val songDao: SongDao,
    private val recentSearchDao: RecentSearchDao,
    private val favoriteDao: FavoriteDao
) : MusicRepository {

    override suspend fun searchSongs(query: String): Resource<List<Song>> {
        return try {
            val remoteSongs = if (query.isBlank()) getFeaturedSongs() else remoteSource.searchSongs(query)
            val localSongs = localAudioSource.queryDeviceAudioFiles().filter {
                query.isBlank() || it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true)
            }
            val combined = (localSongs + remoteSongs).distinctBy { it.id }

            // Persist metadata to local cache
            songDao.insertSongs(combined.map { it.toEntity() })
            val songsWithFav = combined.map { song ->
                song.copy(isFavorite = favoriteDao.isFavorite(song.id))
            }
            Resource.Success(songsWithFav)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to search songs", e)
        }
    }

    override suspend fun getLocalSongs(): List<Song> {
        val local = localAudioSource.queryDeviceAudioFiles()
        songDao.insertSongs(local.map { it.toEntity() })
        return local.map { it.copy(isFavorite = favoriteDao.isFavorite(it.id)) }
    }

    override suspend fun searchArtists(query: String): Resource<List<Artist>> {
        return try {
            Resource.Success(remoteSource.searchArtists(query))
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to search artists", e)
        }
    }

    override suspend fun searchAlbums(query: String): Resource<List<Album>> {
        return try {
            Resource.Success(remoteSource.searchAlbums(query))
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to search albums", e)
        }
    }

    override suspend fun getSong(id: String): Resource<Song> {
        val cached = songDao.getSongById(id)
        if (cached != null) {
            val isFav = favoriteDao.isFavorite(id)
            return Resource.Success(cached.toDomain(isFavorite = isFav))
        }
        val remote = try {
            remoteSource.getSong(id)
        } catch (e: Exception) {
            null
        } ?: return Resource.Error("Song not found")
        songDao.insertSong(remote.toEntity())
        return Resource.Success(remote)
    }

    /**
     * Home content with zero placeholders. "Whole Lotta Red" (shared album
     * playlist OLAK5uy_lW6cMszmMtqMeepM6dSApqU1K2meB4ajE) is fetched live from
     * YouTube Music; if the device is offline the list is simply empty.
     */
    private suspend fun getFeaturedSongs(): List<Song> = try {
        remoteSource.getAlbumTracks(WHOLE_LOTTA_RED_PLAYLIST_ID)
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getAlbumTracks(albumId: String): Resource<List<Song>> {
        return try {
            Resource.Success(remoteSource.getAlbumTracks(albumId))
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to load album", e)
        }
    }

    /**
     * Opens a track for ExoPlayer: local files and already resolved URLs are returned
     * as-is, YouTube Music tracks are resolved to a signed audio URL on demand by the
     * remote source. Failures surface as [Resource.Error] so the UI can report them.
     */
    override suspend fun resolvePlayableMedia(song: Song): Resource<PlayableMedia> {
        return try {
            Resource.Success(remoteSource.resolvePlayableMedia(song))
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Could not resolve audio stream", e)
        }
    }

    override suspend fun getRecentSearches(): List<String> {
        return recentSearchDao.getRecentSearches()
    }

    override suspend fun saveRecentSearch(query: String) {
        if (query.isNotBlank()) {
            recentSearchDao.insertSearch(RecentSearchEntity(query.trim()))
        }
    }

    override suspend fun clearRecentSearches() {
        recentSearchDao.clearAll()
    }
}

class LyricsRepositoryImpl @Inject constructor(
    private val lrclibApi: LrclibLyricsApi,
    private val blazifyLyricsEngine: com.dhunya.app.data.remote.lyrics.BlazifyLyricsEngine
) : LyricsRepository {

    override suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSeconds: Int?
    ): Resource<Lyrics> {
        return try {
            val durationMs = (durationSeconds ?: 200) * 1000L
            val lyrics = blazifyLyricsEngine.getLyrics(
                songId = "${trackName}_$artistName",
                title = trackName,
                artist = artistName,
                durationMs = durationMs
            )
            Resource.Success(lyrics)
        } catch (e: Exception) {
            Resource.Error("Could not retrieve lyrics", e)
        }
    }
}

class LibraryRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : LibraryRepository {

    override fun getFavorites(): Flow<List<Song>> {
        return favoriteDao.getFavoriteSongs().map { list ->
            list.map { it.toDomain(isFavorite = true) }
        }
    }

    override suspend fun toggleFavorite(song: Song): Boolean {
        songDao.insertSong(song.toEntity())
        val currentlyFav = favoriteDao.isFavorite(song.id)
        return if (currentlyFav) {
            favoriteDao.removeFavorite(song.id)
            false
        } else {
            favoriteDao.addFavorite(FavoriteEntity(song.id))
            true
        }
    }

    override suspend fun isFavorite(songId: String): Boolean {
        return favoriteDao.isFavorite(songId)
    }

    override fun getHistory(): Flow<List<Song>> {
        return historyDao.getHistorySongs().map { list ->
            list.map { entity ->
                entity.toDomain(isFavorite = favoriteDao.isFavorite(entity.id))
            }
        }
    }

    override suspend fun addToHistory(song: Song) {
        songDao.insertSong(song.toEntity())
        historyDao.insertHistory(HistoryEntity(songId = song.id))
    }

    override suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                val count = playlistDao.getSongCount(entity.id)
                entity.toDomain(songCount = count)
            }
        }
    }

    override suspend fun createPlaylist(name: String, description: String?): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name, description = description, coverArtworkUrl = null))
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistDao.renamePlaylist(playlistId, newName)
    }

    override fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return playlistDao.getSongsForPlaylist(playlistId).map { entities ->
            entities.map { it.toDomain(isFavorite = favoriteDao.isFavorite(it.id)) }
        }
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        songDao.insertSong(song.toEntity())
        val count = playlistDao.getSongCount(playlistId)
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = song.id, orderIndex = count)
        )
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
}

class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    private val songDao: SongDao,
    private val workManager: androidx.work.WorkManager,
    private val realDownloadManager: com.dhunya.app.data.local.RealDownloadManager,
    private val remoteSource: com.dhunya.app.data.remote.music.MusicRemoteDataSource
) : DownloadRepository {

    override fun getDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getAllDownloads().map { downloads ->
            downloads.mapNotNull { downloadEntity ->
                val songEntity = songDao.getSongById(downloadEntity.songId)
                songEntity?.let { downloadEntity.toDomain(it.toDomain()) }
            }
        }
    }

    override suspend fun startDownload(song: Song) {
        // YouTube Music rows only carry metadata, so the signed audio URL is resolved
        // first; locally stored tracks already have a playable URI.
        val streamUrl = song.streamUrl?.takeIf { it.isNotBlank() }
            ?: song.localUri?.takeIf { it.isNotBlank() }
            ?: resolveDownloadUrl(song)
        if (streamUrl.isNullOrBlank()) {
            downloadDao.insertOrUpdateDownload(
                DownloadEntity(
                    songId = song.id,
                    status = DownloadStatus.FAILED.name,
                    progress = 0f,
                    localFilePath = null,
                    totalBytes = 0L,
                    downloadedBytes = 0L
                )
            )
            return
        }

        // Persist song entity in local database
        songDao.insertSong(song.toEntity())

        // Initial QUEUED / DOWNLOADING state
        downloadDao.insertOrUpdateDownload(
            DownloadEntity(
                songId = song.id,
                status = DownloadStatus.DOWNLOADING.name,
                progress = 0f,
                localFilePath = null,
                totalBytes = song.durationMs * 32,
                downloadedBytes = 0L
            )
        )

        // WorkManager constraints: Requires network connection to download
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        // Create WorkManager OneTimeWorkRequest with unique work name
        val downloadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.dhunya.app.data.worker.MusicDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                androidx.work.workDataOf(
                    com.dhunya.app.data.worker.MusicDownloadWorker.KEY_SONG_ID to song.id,
                    com.dhunya.app.data.worker.MusicDownloadWorker.KEY_STREAM_URL to streamUrl,
                    com.dhunya.app.data.worker.MusicDownloadWorker.KEY_TITLE to song.title,
                    com.dhunya.app.data.worker.MusicDownloadWorker.KEY_ARTIST to song.artistName
                )
            )
            .addTag("download_${song.id}")
            .build()

        // Enqueue uniquely to avoid duplicate concurrent downloads for the same track
        workManager.enqueueUniqueWork(
            "download_${song.id}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            downloadWorkRequest
        )
    }

    /**
     * Resolves an online stream URL for a track that has none yet (YouTube Music rows).
     * Returns null when nothing playable could be found, e.g. while offline.
     */
    private suspend fun resolveDownloadUrl(song: Song): String? = runCatching {
        val media = remoteSource.resolvePlayableMedia(song)
        media.mediaUri.takeIf { it.isNotBlank() && !media.isLocal }
    }.getOrNull()

    override suspend fun cancelDownload(songId: String) {
        workManager.cancelUniqueWork("download_$songId")
        realDownloadManager.deleteDownloadedFile(songId)
        downloadDao.deleteDownload(songId)
        songDao.updateDownloadStatus(songId, false, null)
    }

    override suspend fun removeDownload(songId: String) {
        workManager.cancelUniqueWork("download_$songId")
        realDownloadManager.deleteDownloadedFile(songId)
        downloadDao.deleteDownload(songId)
        songDao.updateDownloadStatus(songId, false, null)
    }

    override suspend fun isDownloaded(songId: String): Boolean {
        val item = downloadDao.getDownloadById(songId)
        return item?.status == DownloadStatus.COMPLETED.name
    }
}

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: PreferencesDataStore
) : SettingsRepository {
    override val settingsFlow: Flow<UserSettings> = dataStore.userSettings

    override suspend fun updateHighQuality(enabled: Boolean) {
        dataStore.setHighQuality(enabled)
    }

    override suspend fun updateAutoPlay(enabled: Boolean) {
        dataStore.setAutoPlay(enabled)
    }

    override suspend fun updateOfflineMode(enabled: Boolean) {
        dataStore.setOfflineMode(enabled)
    }

    override suspend fun updateLyricsFontSize(size: Float) {
        dataStore.setLyricsFontSize(size)
    }
}
