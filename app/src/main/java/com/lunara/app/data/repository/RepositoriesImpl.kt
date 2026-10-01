package com.lunara.app.data.repository

import android.net.Uri
import com.lunara.app.core.result.Resource
import com.lunara.app.core.utils.isPlayableAudioFile
import com.lunara.app.data.local.PreferencesDataStore
import com.lunara.app.data.local.dao.*
import com.lunara.app.data.local.entity.*
import com.lunara.app.data.mapper.toDomain
import com.lunara.app.data.mapper.toEntity
import com.lunara.app.data.remote.lyrics.LrclibLyricsApi
import com.lunara.app.data.remote.music.MusicRemoteDataSource
import com.lunara.app.domain.model.*
import com.lunara.app.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

private const val WHOLE_LOTTA_RED_PLAYLIST_ID = "OLAK5uy_lW6cMszmMtqMeepM6dSApqU1K2meB4ajE"

/**
 * `catch (e: Exception)` also catches [CancellationException], and swallowing it is what made
 * cancelling a request look like a failure: the message ("LazyStandaloneCoroutine was
 * cancelled", obfuscated to something like "z0 was cancelled") was turned into a
 * `Resource.Error`, published into the shared playback state *after* a newer track had already
 * started, and stopped that track with a bogus "source error". Cancellation always belongs to
 * the caller, so it is re-thrown from every catch block in this file.
 */
private fun Exception.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

class MusicRepositoryImpl @Inject constructor(
    private val remoteSource: MusicRemoteDataSource,
    private val localAudioSource: com.lunara.app.data.local.LocalAudioDataSource,
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
            e.rethrowIfCancellation()
            Resource.Error(e.localizedMessage ?: "Failed to search songs", e)
        }
    }

    override suspend fun getLocalSongs(): List<Song> {
        val local = localAudioSource.queryDeviceAudioFiles()
        songDao.insertSongs(local.map { it.toEntity() })
        return local.map { it.copy(isFavorite = favoriteDao.isFavorite(it.id)) }
    }

    override fun observeLocalSongs(): Flow<List<Song>> =
        combine(songDao.observeLocalSongs(), favoriteDao.getFavoriteSongs()) { local, favorites ->
            // Favorites are resolved from a set instead of calling `favoriteDao.isFavorite` per
            // row: that call suspends, and one round trip per track inside a flow collector is
            // what stalls a long on-device library.
            val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
            local.map { entity -> entity.toDomain(isFavorite = entity.id in favoriteIds) }
        }

    override fun canReadDeviceAudio(): Boolean = localAudioSource.hasAudioPermission()

    override fun deviceAudioPermission(): String = localAudioSource.requiredPermissions.first()

    override suspend fun searchArtists(query: String): Resource<List<Artist>> {
        return try {
            Resource.Success(remoteSource.searchArtists(query))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Resource.Error(e.localizedMessage ?: "Failed to search artists", e)
        }
    }

    override suspend fun searchAlbums(query: String): Resource<List<Album>> {
        return try {
            Resource.Success(remoteSource.searchAlbums(query))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
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
            e.rethrowIfCancellation()
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
        e.rethrowIfCancellation()
        emptyList()
    }

    override suspend fun getAlbumTracks(albumId: String): Resource<List<Song>> {
        return try {
            Resource.Success(remoteSource.getAlbumTracks(albumId))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Resource.Error(e.localizedMessage ?: "Failed to load album", e)
        }
    }

    /**
     * Opens a track for ExoPlayer: local files and already resolved URLs are returned
     * as-is, YouTube Music tracks are resolved to a signed audio URL on demand by the
     * remote source. Failures surface as [Resource.Error] so the UI can report them.
     */
    override suspend fun resolvePlayableMedia(
        song: Song,
        forceRefresh: Boolean
    ): Resource<PlayableMedia> {
        return try {
            Resource.Success(remoteSource.resolvePlayableMedia(song, forceRefresh))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
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
    private val blazifyLyricsEngine: com.lunara.app.data.remote.lyrics.BlazifyLyricsEngine
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
            e.rethrowIfCancellation()
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
            list.distinctBy { it.id }.map { it.toDomain(isFavorite = true) }
        }
    }

    override suspend fun setFavorite(song: Song, favorite: Boolean): Boolean {
        // Keep the song row around first (it is an upsert, so nothing cascades away), otherwise
        // the favorite row would violate its foreign key.
        songDao.insertSong(song.toEntity())
        if (favorite) {
            favoriteDao.addFavorite(FavoriteEntity(song.id))
        } else {
            favoriteDao.removeFavorite(song.id)
        }
        return favorite
    }

    override suspend fun toggleFavorite(song: Song): Boolean {
        songDao.insertSong(song.toEntity())
        val currentlyFav = favoriteDao.isFavorite(song.id)
        val target = !currentlyFav
        return setFavorite(song, target)
    }

    override suspend fun isFavorite(songId: String): Boolean {
        return favoriteDao.isFavorite(songId)
    }

    override fun getHistory(): Flow<List<Song>> {
        return historyDao.getHistorySongs().map { list ->
            list.distinctBy { it.id }.map { entity ->
                entity.toDomain(isFavorite = favoriteDao.isFavorite(entity.id))
            }
        }
    }

    override suspend fun addToHistory(song: Song) {
        songDao.insertSong(song.toEntity())
        // One row per track: replace the previous entry instead of appending another one,
        // otherwise the join in `getHistorySongs()` repeats the song and the UI receives
        // duplicate list keys ("Key … was already used").
        historyDao.deleteForSong(song.id)
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
    private val realDownloadManager: com.lunara.app.data.local.RealDownloadManager,
    private val remoteSource: com.lunara.app.data.remote.music.MusicRemoteDataSource,
    private val downloadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : DownloadRepository {

    /** One running transfer per song so retries/cancels are exact. */
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /**
     * Every persisted [DownloadEntity] shows up here, even when its song metadata row is
     * missing (e.g. restored on a fresh install). Callers get a playable item either way.
     */
    override fun getDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getAllDownloads().map { downloads ->
            downloads
                .distinctBy { it.songId }
                .map { downloadEntity ->
                    val songEntity = songDao.getSongById(downloadEntity.songId)
                    val song = songEntity?.toDomain()?.copy(
                        localUri = songEntity.localUri ?: downloadEntity.localFilePath,
                        isDownloaded = songEntity.isDownloaded ||
                            downloadEntity.status == DownloadStatus.COMPLETED.name,
                        // Keep the *remote* stream URL. Overwriting it with the local file path
                        // meant that a missing/removed file left the player with a dead
                        // `file://` URL and ExoPlayer answered "source error" instead of
                        // streaming the track. `PlayerManager` prefers a local file when it
                        // really exists and otherwise falls back to this URL.
                        streamUrl = songEntity.streamUrl
                    ) ?: Song(
                        id = downloadEntity.songId,
                        title = "Downloaded track",
                        artistName = "Unknown Artist",
                        localUri = downloadEntity.localFilePath,
                        streamUrl = null,
                        isDownloaded = downloadEntity.status == DownloadStatus.COMPLETED.name
                    )
                    downloadEntity.toDomain(song)
                }
        }
    }

    /**
     * Saves a track for offline listening. The previous implementation queued a
     * [androidx.work.WorkManager] job per song: scheduling delays, foreground promotion and
     * retry backoffs stretched a few seconds of copying into minutes, and the request carried
     * no byte range so YouTube answered 403. Now the save runs immediately in the app process on
     * a closed range with a big buffer — the same direct path that fixes playback.
     */
    override suspend fun startDownload(song: Song) {
        val downloadJob = downloadScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Already on disk: just record it. No network round trip at all.
            realDownloadManager.getSongFile(song.id)?.let { file ->
                markAlreadyLocal(song, Uri.fromFile(file).toString())
                return@launch
            }

            if (song.localUri?.isNotBlank() == true && !song.localUri.startsWith("http")) {
                markAlreadyLocal(song, song.localUri)
                return@launch
            }

            // A `streamUrl` baked into the song row can be an expired signature — YouTube
            // answers 403 to those, which is what made a save spin and then fail. Resolve a
            // fresh URL instead; the remote source caches recent resolutions, so a track that
            // was just played resolves instantly.
            val streamUrl = resolveDownloadUrl(song)
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
                return@launch
            }

            // Upsert keeps the existing song metadata (artwork/titles) and carries no cascade
            // because `songs` no longer uses INSERT OR REPLACE.
            songDao.insertSong(song.copy(streamUrl = streamUrl).toEntity())

            val previous = activeJobs.put(song.id, coroutineContext[Job]!!)
            previous?.cancelAndJoin()

            val scopeJob = coroutineContext[Job]!!
            activeJobs[song.id] = scopeJob
            try {
                downloadDao.insertOrUpdateDownload(
                    DownloadEntity(
                        songId = song.id,
                        status = DownloadStatus.DOWNLOADING.name,
                        progress = 0f,
                        localFilePath = null,
                        totalBytes = 0L,
                        downloadedBytes = 0L
                    )
                )

                var lastProgressEmitMs = 0L
                val file = realDownloadManager.downloadStream(song.id, streamUrl) { downloaded, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastProgressEmitMs >= PROGRESS_EMIT_INTERVAL_MS || downloaded >= total) {
                        lastProgressEmitMs = now
                        val progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        downloadDao.insertOrUpdateDownload(
                            DownloadEntity(
                                songId = song.id,
                                status = DownloadStatus.DOWNLOADING.name,
                                progress = progress,
                                localFilePath = null,
                                totalBytes = total,
                                downloadedBytes = downloaded
                            )
                        )
                    }
                }
                val size = file.length()
                // Whatever came down has to be audio. A playlist, an HTML error page or a stub
                // must never be recorded as a finished download: it would be unplayable offline
                // and would fail as "unsupported audio format" instead of re-fetching. The catch
                // block below deletes the file and marks the attempt failed.
                if (!isPlayableAudioFile(file)) {
                    error("The server did not return playable audio")
                }
                val fileUri = Uri.fromFile(file).toString()
                downloadDao.insertOrUpdateDownload(
                    DownloadEntity(
                        songId = song.id,
                        status = DownloadStatus.COMPLETED.name,
                        progress = 1f,
                        localFilePath = fileUri,
                        totalBytes = size,
                        downloadedBytes = size
                    )
                )
                songDao.insertSong(
                    song.copy(streamUrl = streamUrl, localUri = fileUri, isDownloaded = true).toEntity()
                )
            } catch (e: CancellationException) {
                realDownloadManager.deleteDownloadedFile(song.id)
                downloadDao.deleteDownload(song.id)
                throw e
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                realDownloadManager.deleteDownloadedFile(song.id)
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
            } finally {
                activeJobs.remove(song.id, coroutineContext[Job])
            }
        }

        // The download's outcome is recorded in the `downloads` table by both branches above,
        // so this only has to *wait*. `join()` re-checks the caller's own cancellation and is
        // otherwise silent — a cancelled or failed save can never surface as an exception on
        // the caller's scope (that escape route is what produced the obfuscated
        // "… was cancelled" failure in the UI).
        try {
            downloadJob.join()
        } catch (e: CancellationException) {
            coroutineContext.ensureActive()
        }
    }

    private suspend fun markAlreadyLocal(song: Song, fileUri: String) {
        downloadDao.insertOrUpdateDownload(
            DownloadEntity(
                songId = song.id,
                status = DownloadStatus.COMPLETED.name,
                progress = 1f,
                localFilePath = fileUri,
                totalBytes = 0L,
                downloadedBytes = 0L
            )
        )
        songDao.insertSong(song.copy(localUri = fileUri, isDownloaded = true).toEntity())
    }

    /**
     * Resolves a progressive audio URL for a track that has none yet (YouTube Music rows).
     *
     * Returns null when nothing playable could be found, e.g. while offline.
     *
     * The resolve is asked for progressive audio only: `resolvePlayableMedia` would otherwise
     * prefer an HLS playlist (playlists are not range gated, so they stream best), and saving a
     * playlist produces a few kilobytes of text that ExoPlayer rejects as "unsupported audio
     * format" - which is exactly how offline downloads ended up unplayable.
     */
    private suspend fun resolveDownloadUrl(song: Song): String? = runCatching {
        // Clear any URL stored on the row first: `resolvePlayableMedia` hands a preset
        // `streamUrl` straight back, and an expired signature only ever produces a 403. The
        // saved copy is dropped from the request too, so a re-download gets a fresh URL.
        val media = remoteSource.resolveDownloadableMedia(
            song.copy(streamUrl = null, localUri = null)
        )
        media.mediaUri.takeIf { it.isNotBlank() && !media.isLocal && !media.isHls }
    }.getOrNull()

    override suspend fun cancelDownload(songId: String) {
        activeJobs.remove(songId)?.cancelAndJoin()
        realDownloadManager.deleteDownloadedFile(songId)
        downloadDao.deleteDownload(songId)
        songDao.updateDownloadStatus(songId, false, null)
    }

    override suspend fun removeDownload(songId: String) {
        activeJobs.remove(songId)?.cancelAndJoin()
        realDownloadManager.deleteDownloadedFile(songId)
        downloadDao.deleteDownload(songId)
        songDao.updateDownloadStatus(songId, false, null)
    }

    /**
     * Files that turned out to be unplayable (a playlist saved as audio, a truncated download)
     * are parked as [DownloadStatus.FAILED] so nothing keeps handing them to the player, and the
     * song row stops advertising a local copy.
     */
    override suspend fun markLocalCopyUnusable(songId: String) {
        downloadDao.insertOrUpdateDownload(
            DownloadEntity(
                songId = songId,
                status = DownloadStatus.FAILED.name,
                progress = 0f,
                localFilePath = null,
                totalBytes = 0L,
                downloadedBytes = 0L
            )
        )
        songDao.updateDownloadStatus(songId, false, null)
    }

    override suspend fun isDownloaded(songId: String): Boolean {
        val item = downloadDao.getDownloadById(songId)
        return item?.status == DownloadStatus.COMPLETED.name
    }

    private companion object {
        /** Progress writes to Room at most ~7x per second. */
        const val PROGRESS_EMIT_INTERVAL_MS = 150L
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

    override suspend fun updateResumePlayback(enabled: Boolean) {
        dataStore.setResumePlayback(enabled)
    }

    override suspend fun updateOfflineMode(enabled: Boolean) {
        dataStore.setOfflineMode(enabled)
    }

    override suspend fun updateLyricsFontSize(size: Float) {
        dataStore.setLyricsFontSize(size)
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.setThemeMode(mode)
    }

    override suspend fun updateDynamicColor(enabled: Boolean) {
        dataStore.setDynamicColors(enabled)
    }
}
