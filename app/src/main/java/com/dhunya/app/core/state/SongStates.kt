package com.dhunya.app.core.state

import com.dhunya.app.domain.model.DownloadStatus
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.repository.DownloadRepository
import com.dhunya.app.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App wide "did the user love / save this track" state.
 *
 * The heart and the download badge used to be read straight off the `Song` object that was
 * captured when a screen was composed, so tapping "Add to favorites" wrote to the database
 * while the icon kept showing the old value. This holder mirrors the Room tables as flows and
 * flips optimistically *before* the write, so every heart in the app (player, sheet, list
 * rows) animates the moment it is tapped.
 */
@Singleton
class SongStates @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus.asStateFlow()

    init {
        scope.launch {
            libraryRepository.getFavorites()
                .catch { }
                .collect { songs -> _favoriteIds.value = songs.map { it.id }.toSet() }
        }
        scope.launch {
            downloadRepository.getDownloads()
                .catch { }
                .collect { items ->
                    _downloadStatus.value = items.associate { it.song.id to it.status }
                }
        }
    }

    fun isFavorite(songId: String): Boolean = songId in _favoriteIds.value

    fun statusOf(songId: String): DownloadStatus? = _downloadStatus.value[songId]

    /** `true` once the file really is on the device (falls back to the song flag). */
    fun isDownloaded(song: Song): Boolean =
        _downloadStatus.value[song.id] == DownloadStatus.COMPLETED || song.isDownloaded

    fun isDownloading(song: Song): Boolean = when (_downloadStatus.value[song.id]) {
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> true
        else -> false
    }

    /**
     * Flips the heart immediately and keeps the database in sync. On failure the optimistic
     * flip is rolled back so the icon never lies about the stored state.
     */
    fun toggleFavorite(song: Song) {
        val wasFavorite = isFavorite(song.id)
        setFavoriteLocally(song.id, !wasFavorite)
        scope.launch {
            val nowFavorite = runCatching { libraryRepository.toggleFavorite(song) }.getOrNull()
            if (nowFavorite == null) {
                setFavoriteLocally(song.id, wasFavorite)
            } else {
                setFavoriteLocally(song.id, nowFavorite)
            }
        }
    }

    /** Saves the track for offline playback (no-op while a download is already running). */
    fun download(song: Song) {
        if (isDownloading(song) || isDownloaded(song)) return
        _downloadStatus.value = _downloadStatus.value + (song.id to DownloadStatus.QUEUED)
        scope.launch {
            runCatching { downloadRepository.startDownload(song) }
                .onFailure { _downloadStatus.value = _downloadStatus.value - song.id }
        }
    }

    fun removeDownload(songId: String) {
        _downloadStatus.value = _downloadStatus.value - songId
        scope.launch { runCatching { downloadRepository.removeDownload(songId) } }
    }

    private fun setFavoriteLocally(songId: String, favorite: Boolean) {
        _favoriteIds.value = if (favorite) _favoriteIds.value + songId
        else _favoriteIds.value - songId
    }
}
