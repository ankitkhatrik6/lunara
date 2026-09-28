package com.lunara.app.core.state

import com.lunara.app.domain.model.DownloadStatus
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.DownloadRepository
import com.lunara.app.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App wide "did the user love / save this track" state.
 *
 * Two things made the heart glitch before: screens read the flag off a `Song` object captured at
 * composition time, and the Room flow kept emitting the *old* list while a write was in flight,
 * which flipped the icon straight back. This holder fixes both:
 *
 *  - [favoriteIds] / [downloadStatus] are the single source of truth for the UI, and
 *  - writes are recorded in the pending maps *before* they hit Room, so an in-flight value always
 *    wins over a stale emission until the database confirms it.
 *
 * Writes are idempotent (`setFavorite(song, true/false)`) rather than toggles, so double taps
 * converge on the last choice instead of fighting each other.
 */
@Singleton
class SongStates @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Favourite ids as they currently exist in the database. */
    private val dbFavorites = MutableStateFlow<Set<String>>(emptySet())

    /** Favourite values being written right now, keyed by song id. */
    private val pendingFavorites = ConcurrentHashMap<String, Boolean>()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    /** Download rows as they currently exist in the database. */
    private val dbDownloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())

    /** Downloads queued locally before their Room row exists. */
    private val pendingDownloads = ConcurrentHashMap<String, DownloadStatus>()

    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus.asStateFlow()

    init {
        scope.launch {
            libraryRepository.getFavorites()
                .catch { }
                .collect { songs ->
                    dbFavorites.value = songs.map { it.id }.toSet()
                    publishFavorites()
                }
        }
        scope.launch {
            downloadRepository.getDownloads()
                .catch { }
                .collect { items ->
                    dbDownloads.value = items.associate { it.song.id to it.status }
                    publishDownloads()
                }
        }
    }

    fun isFavorite(songId: String): Boolean = songId in _favoriteIds.value

    fun statusOf(songId: String): DownloadStatus? = _downloadStatus.value[songId]

    /** `true` once the file really is on the device (falls back to the song flag). */
    fun isDownloaded(song: Song): Boolean =
        statusOf(song.id) == DownloadStatus.COMPLETED || song.isDownloaded

    fun isDownloading(song: Song): Boolean = when (statusOf(song.id)) {
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> true
        else -> false
    }

    fun toggleFavorite(song: Song) = setFavorite(song, !isFavorite(song.id))

    /**
     * Flips the heart immediately and keeps the database in sync. The optimistic value is held in
     * [pendingFavorites] until Room confirms it, so the icon never flickers back mid-write and
     * never lies about the stored state.
     */
    fun setFavorite(song: Song, favorite: Boolean) {
        pendingFavorites[song.id] = favorite
        publishFavorites()
        scope.launch {
            val stored = runCatching { libraryRepository.setFavorite(song, favorite) }.getOrNull()
            // Only drop the override while it is still the value we wrote: a newer tap wins.
            pendingFavorites.remove(song.id, favorite)
            if (stored == null) {
                publishFavorites()
            } else {
                dbFavorites.value = if (stored) dbFavorites.value + song.id
                else dbFavorites.value - song.id
                publishFavorites()
            }
        }
    }

    /** Saves the track for offline playback (no-op while a download is already running). */
    fun download(song: Song) {
        if (isDownloading(song) || isDownloaded(song)) return
        pendingDownloads[song.id] = DownloadStatus.QUEUED
        publishDownloads()
        scope.launch {
            runCatching { downloadRepository.startDownload(song) }
            // The repository has written its own row by now, so the pending entry is redundant.
            pendingDownloads.remove(song.id)
            publishDownloads()
        }
    }

    fun removeDownload(songId: String) {
        pendingDownloads.remove(songId)
        dbDownloads.value = dbDownloads.value - songId
        publishDownloads()
        scope.launch { runCatching { downloadRepository.removeDownload(songId) } }
    }

    private fun publishFavorites() {
        val merged = dbFavorites.value.toMutableSet()
        pendingFavorites.forEach { (id, favorite) ->
            if (favorite) merged.add(id) else merged.remove(id)
        }
        _favoriteIds.value = merged
    }

    private fun publishDownloads() {
        val merged = dbDownloads.value.toMutableMap()
        // Pending only fills gaps: once Room has a row it is authoritative (it knows about
        // completion, failure and cancellation).
        pendingDownloads.forEach { (id, status) ->
            if (!merged.containsKey(id)) merged[id] = status
        }
        _downloadStatus.value = merged
    }
}
