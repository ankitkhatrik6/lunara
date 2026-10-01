package com.lunara.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lunara.app.core.result.Resource
import com.lunara.app.core.utils.NetworkMonitor
import com.lunara.app.core.utils.audioMimeTypeOf
import com.lunara.app.core.utils.isPlayableAudioFile
import com.lunara.app.data.remote.innertube.InnerTubeClients
import com.lunara.app.domain.model.DownloadStatus
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.DownloadRepository
import com.lunara.app.domain.repository.MusicRepository
import com.lunara.app.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface LunaraPlayer {
    val playbackState: StateFlow<PlaybackState>
    fun playSong(song: Song)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val queueManager: QueueManager,
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val snapshotStore: PlaybackSnapshotStore
) : LunaraPlayer {

    /**
     * Mirrors [NetworkMonitor]. Playback must never wait on a network round trip it cannot make,
     * and an offline tap has to be answered with a plain explanation instead of a failed request.
     */
    @Volatile
    private var isOnline = true

    /** Mirrors the "Offline mode only" setting: play saved files even while a network is up. */
    @Volatile
    private var offlineOnly = false

    /**
     * Nothing here may reach the uncaught-exception handler: an unhandled throwable inside a
     * fire-and-forget coroutine takes the whole process down. Failures are surfaced through
     * [playbackState] instead.
     */
    private val scope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            _playbackState.value = _playbackState.value.copy(
                isLoading = false,
                isPlaying = false,
                errorMessage = throwable.localizedMessage ?: "Playback error"
            )
        }
    )
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var resolveJob: Job? = null

    /** The id of the track whose resolve is allowed to touch the shared playback state. */
    private var resolvingSongId: String? = null

    /** Spare resolved URLs for the current track, used to recover from CDN failures. */
    private var currentFallbackUris: List<String> = emptyList()

    /** URLs that already failed for the current track, so a swap never loops. */
    private val failedUris = mutableSetOf<String>()

    /** Tracks that already had one forced re-resolve, so recovery can never spin. */
    private val reResolvedSongIds = mutableSetOf<String>()

    /**
     * Tracks whose last URL died on the CDN. The next attempt for them bypasses the resolver's
     * cache, because that cache would otherwise hand back the very signature that just failed.
     */
    private val staleStreamSongIds = mutableSetOf<String>()

    /** The URL currently loaded in ExoPlayer, tracked so a failure can be attributed to it. */
    private var currentStreamUri: String? = null

    /** Id of the track [currentStreamUri] belongs to, so a re-tap resumes instead of reloading. */
    private var currentStreamSongId: String? = null

    /**
     * The session restore that is still waiting to be heard: [startPlayback] seeks here once the
     * restored track's stream is actually loaded. The seek cannot be issued while restoring -
     * ExoPlayer discards a position set against a media item that is replaced right after - so the
     * playhead travels with the track until its item is in place.
     */
    private var pendingResumeSongId: String? = null
    private var pendingResumePositionMs = 0L

    /** Writes the playhead to disk while music plays, so a relaunch can resume mid-track. */
    private var snapshotJob: Job? = null

    /** Debounces the two snapshot writers; scrubbing is not one write per pixel. */
    private var playheadSaveJob: Job? = null
    private var queueSnapshotJob: Job? = null

    /**
     * `true` once this process has held a queue of its own.
     *
     * It separates "the queue is empty because Lunara just started" - where whatever is on disk is
     * the session that is *about to be restored* and must be left alone - from "the queue is empty
     * because the user emptied it", where the stored session is stale and has to go.
     */
    private var hadQueueThisSession = false

    /**
     * `true` while the empty queue in front of Lunara is the one [stopPlayback] deliberately left
     * behind.
     *
     * A swipe out of recents saves the session and only then clears the player, and the queue
     * observer wakes up afterwards to see an empty queue - which on its own reads as "the user
     * emptied it" and would wipe the session the swipe just wrote. This flag says which empty
     * queue it is, and is cleared as soon as a real queue exists again.
     */
    private var sessionPreservedOnStop = false

    /**
     * The "Autoplay similar tracks" preference. On (the default) Lunara keeps the music going
     * with YouTube Music's own recommendations for the track that is playing once the queue the
     * user built has run out; off ends playback at the end of the queue, as before.
     */
    @Volatile
    private var autoplayEnabled = true

    /**
     * The track the current mix was seeded from, plus the token that pages through the rest of
     * it. Both belong to *that* seed: another track is another mix, and only a token YouTube
     * minted for this mix may be replayed.
     */
    private var radioSeedSongId: String? = null
    private var radioContinuation: String? = null

    /** The in-flight radio fetch. Only one runs at a time, so "next" cannot double-append. */
    private var radioFetchJob: Job? = null

    /**
     * Set when the queue had run out and "next" was pressed while a fetch for the mix was
     * already on its way: the batch that lands is appended *and* started, instead of that press
     * being dropped on the floor.
     */
    private var pendingRadioAdvance = false

    /**
     * A seed whose mix came back with nothing more to give. Asked once and remembered, so a
     * queue that really is over does not retry the radio track after track.
     */
    private var radioExhaustedSeedId: String? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        initializePlayer()
        observeQueue()
        observeNetworkAndSettings()
        restoreLastSession()
    }

    /**
     * Connectivity and the offline-mode preference both decide whether streaming is allowed, so
     * they are watched once here instead of being queried per playback attempt.
     */
    private fun observeNetworkAndSettings() {
        scope.launch {
            networkMonitor.isOnline.collect { online -> isOnline = online }
        }
        scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                offlineOnly = settings.offlineModeOnly
                // The "Autoplay similar tracks" switch is what decides whether the queue is
                // allowed to grow with YouTube Music's mix once the user's own picks run out.
                autoplayEnabled = settings.autoPlay
            }
        }
    }

    /**
     * Rebuilds the session the previous run left behind, standing on the second it stopped.
     *
     * Nothing auto-plays: opening Lunara and being sung at is a surprise, and every other music app
     * restores quietly with the mini player showing where the music is, one tap from continuing.
     *
     * The restore also stands down whenever a queue already exists - a tap, a deep link or an
     * autoplay that happened while the stored session was still being read is by definition newer
     * than the process that wrote it, and the newer intent wins.
     */
    private fun restoreLastSession() {
        scope.launch {
            val enabled = runCatching {
                settingsRepository.settingsFlow.first().resumePlayback
            }.getOrDefault(true)
            if (!enabled) return@launch

            val snapshot = runCatching { snapshotStore.load() }.getOrNull() ?: return@launch
            val song = snapshot.currentSong ?: return@launch
            if (queueManager.queue.value.isNotEmpty() || currentStreamSongId != null) return@launch

            queueManager.restore(
                songs = snapshot.songs,
                currentIndex = snapshot.currentIndex,
                shuffle = snapshot.shuffle,
                repeatMode = snapshot.repeatMode
            )
            hadQueueThisSession = true

            // The playhead is held back rather than seeked: the media item that has to carry it
            // does not exist yet, and ExoPlayer drops a position set before its item is in place.
            // `startPlayback` picks it up when the restored track is actually loaded.
            pendingResumeSongId = song.id
            pendingResumePositionMs = snapshot.positionMs

            _playbackState.value = _playbackState.value.copy(
                currentSong = song,
                currentPositionMs = snapshot.positionMs,
                totalDurationMs = if (song.durationMs > 0) song.durationMs else 0L,
                isLoading = false,
                isPlaying = false
            )

            Log.i(TAG, "Restored session: ${song.title} at ${snapshot.positionMs} ms")
        }
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        // Fail fast: a stalled CDN connection has to hand over to the next resolved URL (or
        // report the failure) within seconds, not after a minute of silence.
        //
        // One HTTP data source per user agent, created lazily. A YouTube stream URL only serves
        // bytes to the client identity it was signed for (`&c=` in the URL), and the resolved
        // fallback URLs usually come from *different* identities, so the agent has to follow the
        // URL being fetched instead of being fixed for the session.
        val httpDataSourceFactories = ConcurrentHashMap<String, DataSource.Factory>()
        val dataSourceFactory = RangedHttpDataSourceFactory(
            upstreamFactoryFor = { userAgent ->
                httpDataSourceFactories.getOrPut(userAgent) {
                    // Stock data source stack - `DefaultDataSource` (files, `content://`, assets)
                    // in front of a plain `DefaultHttpDataSource`, exactly what every
                    // InnerTune-derived player uses - wrapped so that requests against YouTube
                    // always carry a *closed* byte range. Without that wrapper the CDN throttles
                    // them to ~31 KB/s and playback only ever buffers; the measurements are in
                    // [RangedHttpDataSourceFactory]'s documentation.
                    DefaultDataSource.Factory(
                        context,
                        DefaultHttpDataSource.Factory()
                            .setUserAgent(userAgent)
                            .setConnectTimeoutMs(15_000)
                            .setReadTimeoutMs(20_000)
                            .setAllowCrossProtocolRedirects(true)
                    )
                }
            }
        )

        // Blazify-style tuning: start on a very small cushion so the first note is instant,
        // keep a generous ceiling so long tracks never re-buffer, and prefer time over bytes.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // WAKE_MODE_NETWORK, not LOCAL: the local mode only holds a wake lock once the
            // player is ready, so with the screen off a buffering network stream let the CPU
            // suspend mid-load, which left playback stuck on "buffering" for good.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Snapping to the nearest sync sample makes scrubbing (and the seek before a
        // resume) land immediately instead of walking the container.
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        player.setPauseAtEndOfMediaItems(false)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _playbackState.value = _playbackState.value.copy(isLoading = true)
                    }
                    Player.STATE_READY -> {
                        _playbackState.value = _playbackState.value.copy(
                            isLoading = false,
                            totalDurationMs = player.duration.coerceAtLeast(0L),
                            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                        )
                    }
                    Player.STATE_ENDED -> {
                        playNext()
                    }
                    Player.STATE_IDLE -> {
                        _playbackState.value = _playbackState.value.copy(isLoading = false)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                    startSnapshotTracker()
                } else {
                    stopProgressTracker()
                    stopSnapshotTracker()
                    // A pause is the moment most likely to be followed by the app going away
                    // (screen off, swipe out of recents), so the playhead is committed now instead
                    // of waiting for the next tick of the interval timer.
                    savePlayhead()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // A resolved URL can die mid playback (expired signature, throttled edge,
                // PO-token gate). Swapping to the next resolved format keeps the music going
                // instead of dropping out with "source error".
                val song = _playbackState.value.currentSong
                val failedTag = InnerTubeClients.streamTagOf(
                    currentStreamUri?.let { Uri.parse(it) }
                )
                Log.e(
                    TAG,
                    "Playback failed (code ${error.errorCode}) for " +
                        "\"${song?.title}\" from ${currentStreamUri?.substringBefore('?')} " +
                        "(client ${failedTag ?: "unknown"})",
                    error
                )
                currentStreamUri?.let { failedUris.add(it) }
                // Remember that this track's URL is dead so the next attempt asks for a new
                // signature instead of being served the expired one from the resolver cache.
                song?.id?.let { staleStreamSongIds.add(it) }

                // A *local* file the player cannot parse is a damaged saved copy: a playlist that
                // was stored as audio, or a truncated transfer. It is parked as failed so the next
                // attempt streams instead of repeating the same error - and while offline that is
                // said plainly, because no retry can help until the connection is back.
                val localFailure = currentStreamUri?.let { uri ->
                    uri.startsWith("file:") || uri.startsWith("content:")
                } == true
                if (localFailure) {
                    val localSong = song
                    if (localSong != null) {
                        val localSongId = localSong.id
                        val localTitle = localSong.title
                        scope.launch {
                            runCatching { downloadRepository.markLocalCopyUnusable(localSongId) }
                        }
                        if (!isOnline) {
                            reportError(
                                "\"$localTitle\" has no playable saved copy. " +
                                    "Reconnect to download it again."
                            )
                            return
                        }
                    }
                }

                // Prefer a fallback minted by a *different* client identity. When one identity's
                // URLs stop being served (signature binding, PO-token gate) the others normally
                // still work, so this is what makes the swap worthwhile - and the same identity
                // would just fail again.
                val candidates = currentFallbackUris.filter { it !in failedUris }
                val nextUri = candidates.firstOrNull {
                    InnerTubeClients.streamTagOf(Uri.parse(it)) != failedTag
                } ?: candidates.firstOrNull()
                if (song != null && nextUri != null) {
                    startPlayback(player, song, nextUri, mimeTypeOf(nextUri, null))
                    return
                }

                // Every pre-resolved URL is exhausted. YouTube signatures expire after a while,
                // so ask for a completely fresh set before giving up — this is the difference
                // between a track that "sometimes errors" and one that recovers by itself.
                if (song != null && !reResolvedSongIds.contains(song.id)) {
                    reResolvedSongIds.add(song.id)
                    _playbackState.value = _playbackState.value.copy(isLoading = true)
                    scope.launch {
                        // Both parts matter: `forceRefresh` skips the resolver's own cache and
                        // the stripped target keeps it from handing back a stored URL, so the
                        // retry is guaranteed to try a *new* signature instead of the one that
                        // just died.
                        val result = runCatching {
                            musicRepository.resolvePlayableMedia(
                                freshResolveTarget(song),
                                forceRefresh = true
                            )
                        }
                        val media = (result.getOrNull() as? Resource.Success)?.data
                        if (media != null && media.mediaUri.isNotBlank()) {
                            currentFallbackUris = emptyList()
                            failedUris.clear()
                            startPlayback(
                                player,
                                media.song.copy(streamUrl = media.mediaUri),
                                media.mediaUri,
                                media.mimeType,
                                media.contentLength
                            )
                        } else {
                            reportError(describePlaybackError(error))
                        }
                    }
                    return
                }

                reportError(describePlaybackError(error))
            }
        })

        exoPlayer = player
    }

    /**
     * Turns a [PlaybackException] into something the listener can act on.
     *
     * ExoPlayer's own message for the failures that matter here is just "Source error", which
     * told neither the user nor us what actually went wrong. The numeric code always does, so
     * it is appended to the plain English reason and kept in the UI's error text.
     */
    private fun describePlaybackError(error: PlaybackException): String {
        val reason = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network problem - check your connection"

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "YouTube refused the audio stream (HTTP error)"

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "This track is no longer available on YouTube"

            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                "YouTube cut the stream short"

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "Unsupported audio format"

            else -> error.localizedMessage ?: "Playback failed"
        }
        return "$reason (code ${error.errorCode})"
    }

    private fun reportError(message: String) {
        // The loaded item is no longer trustworthy, so the next tap has to resolve again
        // instead of calling `play()` on the failed media item.
        currentStreamSongId = null
        _playbackState.value = _playbackState.value.copy(
            isLoading = false,
            isPlaying = false,
            errorMessage = message
        )
    }

    fun getExoPlayer(): ExoPlayer {
        if (exoPlayer == null) initializePlayer()
        return exoPlayer!!
    }

    private fun observeQueue() {
        scope.launch {
            queueManager.queue.collect { q ->
                _playbackState.value = _playbackState.value.copy(queue = q)
                if (q.isNotEmpty()) {
                    hadQueueThisSession = true
                    // Something is queued again, so the session saved by the last stop is no longer
                    // the only copy of it: the empty-queue guard can go.
                    sessionPreservedOnStop = false
                }
                // Queue edits - add, remove, reorder, clear - are the part of the session that has
                // to survive a restart, so every one of them schedules a write. The write itself is
                // debounced: `setQueue` changes the queue, the index, shuffle and repeat in one go.
                scheduleQueueSnapshot()
            }
        }
        scope.launch {
            queueManager.currentIndex.collect { idx ->
                _playbackState.value = _playbackState.value.copy(
                    queueIndex = idx,
                    currentSong = queueManager.currentSong
                )
                // The stored queue carries the current index, so moving through the queue is a
                // change worth persisting too.
                scheduleQueueSnapshot()
            }
        }
        scope.launch {
            queueManager.isShuffle.collect { shuffle ->
                _playbackState.value = _playbackState.value.copy(shuffleEnabled = shuffle)
                scheduleQueueSnapshot()
            }
        }
        scope.launch {
            queueManager.repeatMode.collect { mode ->
                _playbackState.value = _playbackState.value.copy(repeatMode = mode)
                scheduleQueueSnapshot()
            }
        }
    }

    override fun playSong(song: Song) {
        val player = exoPlayer ?: return

        // Fresh track: forget the previous track's failover bookkeeping.
        currentFallbackUris = emptyList()
        failedUris.clear()

        // Tapping the track that is already loaded must not reload it: resuming the prepared
        // item is what keeps the queue row, the mini player and the notification in sync with
        // no audible gap.
        if (song.id == currentStreamSongId && player.mediaItemCount > 0) {
            resolveJob?.cancel()
            resolvingSongId = null
            _playbackState.value = _playbackState.value.copy(currentSong = song, errorMessage = null)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
            return
        }

        // A downloaded file (or any other local URI that still exists) starts straight away.
        val localUri = usableLocalUri(song.localUri)
        if (localUri != null) {
            resolveJob?.cancel()
            resolvingSongId = null
            startPlayback(player, song, localUri)
            return
        }

        // YouTube Music tracks only carry metadata: the signed audio URL has to be
        // resolved on demand (InnerTube `player` endpoint) before ExoPlayer can open it.
        resolveJob?.cancel()
        // Any URL such a track still carries is a *signed* one that YouTube invalidates
        // within minutes. Reusing it (or handing it back from the recovery path below) is what
        // turned a transient CDN hiccup into a permanent "source error", because every retry
        // asked for the same dead URL. It is therefore dropped here; the resolver serves the
        // same URL from its own cache while it is still valid and refreshes it when it is not.
        // Only this track's resolve may publish into the shared playback state. Without the
        // guard a cancelled request for the *previous* track could still land its result —
        // or its cancellation message — on top of the track that is now starting.
        resolvingSongId = song.id
        _playbackState.value = _playbackState.value.copy(
            currentSong = song,
            isLoading = true,
            isPlaying = false,
            errorMessage = null,
            totalDurationMs = song.durationMs
        )
        // A visible new attempt deserves a fresh recovery budget: the one-shot guard in
        // `onPlayerError` is only there to stop one failure cascade from spinning.
        reResolvedSongIds.remove(song.id)
        resolveJob = scope.launch {
            // A queue row built from search results carries no local URI even when the file is
            // already on disk, so the downloads table is consulted before spending a network
            // round trip on a track that can be played from storage.
            val savedUri = savedFileUri(song)
            if (savedUri != null) {
                if (resolvingSongId != song.id) return@launch
                startPlayback(player, song.copy(localUri = savedUri, isDownloaded = true), savedUri)
                return@launch
            }

            // Nothing saved and no usable connection (or offline mode switched on): say so instead
            // of firing a request that can only fail. Tapping the track again, once the user is
            // back online, simply retries.
            if (!isOnline || offlineOnly) {
                if (resolvingSongId != song.id) return@launch
                reportError(offlineMessage(song))
                return@launch
            }

            val result = try {
                // A wedged resolve has to surface as an error the user can retry, never as a
                // spinner that never stops: every client identity costs a blocking round trip.
                withTimeout(RESOLVE_TIMEOUT_MS) {
                    musicRepository.resolvePlayableMedia(
                        freshResolveTarget(song),
                        // `remove` also consumes the flag: one forced refresh per CDN failure.
                        forceRefresh = staleStreamSongIds.remove(song.id) != null
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Resource.Error("Timed out resolving the audio stream", e)
            } catch (e: CancellationException) {
                // Superseded by a newer tap: leave the new track's state alone.
                throw e
            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Could not resolve audio stream", e)
            }

            // A newer song took over while we were resolving — drop this result silently.
            if (resolvingSongId != song.id) return@launch

            val media = (result as? Resource.Success)?.data
            val uri = media?.mediaUri
            if (media == null || uri.isNullOrBlank()) {
                reportError(
                    (result as? Resource.Error)?.message
                        ?: "Could not resolve audio stream for \"${song.title}\""
                )
                return@launch
            }
            val playableSong = media.song.copy(streamUrl = uri)
            queueManager.updateSong(playableSong)
            // Keep the other resolved formats at hand for a mid-track CDN failure.
            currentFallbackUris = media.fallbackUris
            startPlayback(player, playableSong, uri, media.mimeType, media.contentLength)
        }
    }

    /**
     * Returns [uri] when it points at a file that really exists, otherwise `null` so the
     * caller can fall back to streaming.
     *
     * Saved tracks are stored under `filesDir/lunara_media`. If the file was deleted (app data
     * cleared, storage trimmed) the stored `file://` URI would otherwise be handed to
     * ExoPlayer, which fails it with a "source error" instead of playing the song online.
     */
    private fun usableLocalUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        if (!uri.startsWith("file:")) return uri
        // `isPlayableAudioFile` rejects a playlist or a stub that was saved as audio as well as a
        // file that is gone: those are answered by ExoPlayer with "unsupported audio format"
        // instead of music, so streaming is the better answer.
        return if (isPlayableAudioFile(uri)) uri else null
    }

    /**
     * The saved file for [song], if there is one.
     *
     * [Song.localUri] is only set on objects that came from the database, so a track tapped in
     * search results, a rail or a queue built from them would otherwise look streamable-only and
     * fail offline even though the download is sitting in `filesDir/lunara_media`.
     */
    private suspend fun savedFileUri(song: Song): String? {
        usableLocalUri(song.localUri)?.let { return it }
        val savedPath = runCatching {
            downloadRepository.getDownloads().first()
                .firstOrNull { item ->
                    item.song.id == song.id && item.status == DownloadStatus.COMPLETED
                }
                ?.localFilePath
        }.getOrNull()
        return usableLocalUri(savedPath)
    }

    /** Explains why nothing can play right now, in terms the user can act on. */
    private fun offlineMessage(song: Song): String = if (offlineOnly) {
        "\"${song.title}\" is not downloaded. Offline mode is on in Settings."
    } else {
        "\"${song.title}\" is not downloaded. Connect to the internet to stream it."
    }

    /**
     * Hands [uri] to ExoPlayer.
     *
     * [mimeType] is the mime type the InnerTube format declared (e.g.
     * `audio/webm; codecs="opus"`); passing it through avoids ExoPlayer sniffing a
     * single chunk and settling on the wrong extractor, which stalls on "buffering".
     *
     * [contentLength] is the size that format declared. The data source closes its byte
     * ranges with it, which is what keeps YouTube's CDN from throttling the stream.
     */
    private fun startPlayback(
        player: ExoPlayer,
        song: Song,
        uri: String,
        mimeType: String? = null,
        contentLength: Long = 0L
    ) {
        StreamSizes.remember(uri, contentLength)

        // A different track: allow its own one-shot recovery re-resolve.
        if (_playbackState.value.currentSong?.id != song.id) {
            reResolvedSongIds.remove(song.id)
        }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artistName)
            .setAlbumTitle(song.albumName)
            .setArtworkUri(song.artworkUrl?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(Uri.parse(uri))
            .setMimeType(mimeTypeOf(uri, mimeType))
            .setMediaMetadata(mediaMetadata)
            .build()

        currentStreamUri = uri
        currentStreamSongId = song.id
        player.setMediaItem(mediaItem)

        // A restored session starts where it stopped: the seek is issued *after* the item is set (a
        // position applied to the item that is being replaced is discarded) and *before* `play()`,
        // so the first audible second is the one the user pressed pause on. Every other track -
        // including one the restore was not waiting for - starts at zero.
        val resumePositionMs = if (song.id == pendingResumeSongId) pendingResumePositionMs else 0L
        pendingResumeSongId = null
        pendingResumePositionMs = 0L

        player.prepare()
        if (resumePositionMs > 0L) player.seekTo(resumePositionMs)
        player.play()

        _playbackState.value = _playbackState.value.copy(
            currentSong = song,
            isLoading = true,
            errorMessage = null,
            currentPositionMs = resumePositionMs,
            totalDurationMs = song.durationMs
        )

        prefetchUpcoming()
    }

    /**
     * Resolves the following track in the background while the current one plays.
     *
     * Resolving a YouTube stream takes a few hundred milliseconds (player endpoint + probe);
     * doing it up front is the difference between Blazify-style instant skips and a silent
     * gap every time the user presses next.
     */
    private fun prefetchUpcoming() {
        // Prefetching is a network operation: skip it while offline, and skip it entirely when
        // the user has asked Lunara to keep to downloaded music.
        if (!isOnline || offlineOnly) return

        val ahead = queueManager.upcomingSongs()
        if (ahead.isEmpty()) {
            // Nothing left after the current track. The mix is fetched *here*, one track early,
            // so that the end of a playlist carries on as music instead of hitting a wall that
            // only the next press would reveal.
            topUpQueueWithRadio()
            return
        }

        // Two tracks ahead, so "next" is instant even when the queue is being skipped quickly.
        val unresolved = ahead
            .filter { it.localUri.isNullOrBlank() && it.streamUrl.isNullOrBlank() }
            .take(PREFETCH_AHEAD)
        if (unresolved.isEmpty()) return
        scope.launch {
            unresolved.forEach { song ->
                runCatching { musicRepository.resolvePlayableMedia(song) }
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        queueManager.setQueue(songs, startIndex)
        val selected = queueManager.currentSong ?: songs.getOrNull(startIndex) ?: return
        playSong(selected)
    }

    /**
     * `true` while a "next" press at the end of the queue would keep the music going, i.e. while
     * a mix can still be fetched for the track that is playing.
     *
     * The media notification asks this before greying out its skip button. The button has to stay
     * usable one track *before* the mix arrives, otherwise the radio is unreachable from the lock
     * screen - and greying it out is exactly the "the queue is over" signal this feature removes.
     */
    val canContinueWithRadio: Boolean
        get() = autoplayEnabled && isOnline && !offlineOnly

    /**
     * Starts YouTube Music's mix of similar tracks *for* [song] - the "Start radio" / "Start mix"
     * action of the official app.
     *
     * [song] becomes the whole queue and the mix is pulled in straight away, so similar songs show
     * up in the queue immediately instead of only once the track ends. This is an explicit
     * request, so the "Autoplay similar tracks" preference does not apply to it.
     */
    fun startRadio(song: Song) {
        resetRadioState()
        queueManager.setQueue(listOf(song), startIndex = 0)
        playSong(song)
        requestRadio(song, advanceWhenReady = false, respectAutoplayPreference = false)
    }

    /**
     * Asks for one batch of the mix seeded by [seed] and appends it to the queue.
     *
     * [advanceWhenReady] is what a "next" press at the end of the queue asks for: the batch is
     * appended *and* its first track starts. The prefetch path passes `false` and only grows the
     * queue, which is what makes the end of a playlist seamless instead of a wall.
     *
     * Only one fetch runs at a time, so a burst of skip presses cannot append the same batch
     * twice; the batch already on its way answers any press that lands while it is in flight.
     */
    private fun requestRadio(
        seed: Song,
        advanceWhenReady: Boolean,
        respectAutoplayPreference: Boolean = true
    ) {
        if (radioFetchJob?.isActive == true) {
            if (advanceWhenReady) pendingRadioAdvance = true
            return
        }
        radioFetchJob = scope.launch {
            val added = extendQueueWithRadio(seed, respectAutoplayPreference)
            val advance = advanceWhenReady || pendingRadioAdvance
            pendingRadioAdvance = false
            if (!advance) return@launch
            val next = if (added > 0) queueManager.next() else null
            if (next != null) {
                playSong(next)
            } else {
                // No mix for this track (or nothing left in it): stop at the end of the queue
                // rather than replaying the track that just finished.
                pause()
                seekTo(0L)
            }
        }
    }

    /**
     * Fetches one batch of YouTube Music's own "up next" for [seed] and appends it, returning how
     * many tracks were added.
     *
     * A batch is filled from up to [RADIO_PAGES_PER_BATCH] of YouTube's pages, because a radio
     * repeats itself as it goes on and a single page far into a mix can be almost all songs the
     * queue already holds.
     *
     * `0` means nothing could be added - offline, autoplay switched off, no mix for this track, or
     * every suggestion is already in the queue - and leaves playback untouched.
     */
    private suspend fun extendQueueWithRadio(
        seed: Song,
        respectAutoplayPreference: Boolean
    ): Int {
        if (respectAutoplayPreference && !autoplayEnabled) return 0
        if (!isOnline || offlineOnly) return 0

        // Another track is another mix: the previous seed's paging token does not belong to it,
        // and a mix that gave all it had deserves to be asked again for this new seed.
        if (radioSeedSongId != seed.id) {
            radioSeedSongId = seed.id
            radioContinuation = null
            radioExhaustedSeedId = null
        } else if (radioExhaustedSeedId == seed.id) {
            return 0
        }

        val alreadyQueued = queueManager.queue.value.mapTo(mutableSetOf()) { it.id }
        val fresh = ArrayList<Song>(RADIO_BATCH_SIZE)
        val picked = HashSet<String>(RADIO_BATCH_SIZE)

        var pages = 0
        while (pages < RADIO_PAGES_PER_BATCH) {
            pages++
            val page = when (val result = musicRepository.getUpNext(seed, radioContinuation)) {
                is Resource.Success -> result.data
                // Offline, rate limited or a track YouTube has no mix for: neither the queue nor
                // the UI says anything about it. The music simply has nothing more to follow it.
                else -> break
            }

            radioContinuation = page.continuation
            // No token at all means YouTube considers the mix played out: it has no next page.
            if (page.continuation.isNullOrBlank()) radioExhaustedSeedId = seed.id

            val batch = RadioQueue.pick(
                candidates = page.songs,
                seedSongId = seed.id,
                alreadyQueued = alreadyQueued + picked,
                limit = RADIO_BATCH_SIZE - fresh.size
            )
            fresh += batch
            picked += batch.map { it.id }

            // Full enough to start playing, or nothing left to walk.
            if (radioContinuation.isNullOrBlank() || fresh.size >= RADIO_BATCH_SIZE) break
        }

        if (fresh.isEmpty()) return 0
        queueManager.appendSongs(fresh)
        return fresh.size
    }

    /** Grows the queue with the mix for whatever is playing, when nothing is left after it. */
    private fun topUpQueueWithRadio() {
        if (!canContinueWithRadio) return
        val seed = _playbackState.value.currentSong ?: queueManager.currentSong ?: return
        requestRadio(seed, advanceWhenReady = false)
    }

    /** Forgets the running mix, so the next one starts from its own seed. */
    private fun resetRadioState() {
        radioFetchJob?.cancel()
        radioFetchJob = null
        radioSeedSongId = null
        radioContinuation = null
        radioExhaustedSeedId = null
        pendingRadioAdvance = false
    }

    fun playNext() {
        val nextSong = queueManager.next()
        if (nextSong != null) {
            playSong(nextSong)
            return
        }

        // End of the queue. YouTube Music never stops there - it keeps playing the mix it built
        // around the track - and neither does Lunara any more: pausing and rewinding is what made
        // "next" at the end of a queue look like the very same song starting over.
        val seed = _playbackState.value.currentSong ?: queueManager.currentSong
        if (seed != null && canContinueWithRadio) {
            requestRadio(seed, advanceWhenReady = true)
            return
        }
        pause()
        seekTo(0L)
    }

    fun playPrevious() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000L) {
            seekTo(0L)
        } else {
            val prevSong = queueManager.previous()
            if (prevSong != null) {
                playSong(prevSong)
            } else {
                seekTo(0L)
            }
        }
    }

    override fun play() {
        val player = exoPlayer ?: return
        // A restored session is a queue and a current track with nothing loaded into ExoPlayer
        // yet. The first press of play therefore has to *load* that track - and `startPlayback`
        // seeks it to the stored playhead on the way. Without this the player would sit there
        // looking ready and doing nothing.
        if (player.mediaItemCount == 0) {
            val song = _playbackState.value.currentSong
            if (song != null) {
                playSong(song)
                return
            }
        }
        // A bare `play()` does nothing in these two states, which is what made resume look
        // broken: after an error or an explicit stop the player is IDLE with an item still
        // loaded, and at the end of the last track it is ENDED.
        when (player.playbackState) {
            Player.STATE_IDLE -> if (player.mediaItemCount > 0) player.prepare()
            Player.STATE_ENDED -> player.seekTo(0L)
        }
        player.play()
    }

    override fun pause() {
        exoPlayer?.pause()
        // Pausing is usually followed by the screen going off or the app being swiped away, so the
        // playhead is written out here rather than at the next tick of the interval timer.
        savePlayhead()
    }

    /**
     * Stops playback completely: no item stays prepared, the queue and the UI state are
     * cleared, and the service that hosted the notification can be torn down. Used when the
     * user swipes Lunara out of recents.
     */
    fun stopPlayback() {
        resolveJob?.cancel()
        resolveJob = null
        resolvingSongId = null
        stopProgressTracker()
        stopSnapshotTracker()
        // The session is deliberately *kept* here: swiping Lunara out of recents is how apps are
        // closed, not a request to forget the music. This is the last moment the queue and the
        // playhead are still readable - `player.stop()` resets the position and `clearQueue()`
        // empties the queue - so both are read out and committed first.
        saveQueueSnapshot()
        savePlayhead()
        sessionPreservedOnStop = true
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        queueManager.clearQueue()
        // Nothing is playing any more, so the mix goes with it: a restored session must not
        // continue somebody else's radio, and a stale paging token is worth nothing.
        resetRadioState()
        currentFallbackUris = emptyList()
        failedUris.clear()
        reResolvedSongIds.clear()
        staleStreamSongIds.clear()
        currentStreamUri = null
        currentStreamSongId = null
        _playbackState.value = PlaybackState()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pause()
            return
        }
        // Nothing loaded at all (fresh process, or the queue was just rebuilt): start the
        // current track again rather than silently doing nothing.
        if (player.mediaItemCount == 0) {
            val song = _playbackState.value.currentSong
            if (song != null) {
                playSong(song)
                return
            }
        }
        play()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        // A drag on the seek bar emits one call per pixel of movement; only where it stopped is
        // worth writing to disk.
        schedulePlayheadSave()
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                        totalDurationMs = if (player.duration > 0) player.duration else _playbackState.value.totalDurationMs
                    )
                }
                delay(300L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * Writes the playhead to disk every few seconds while music plays.
     *
     * The interval *is* the error a restore can make: coming back up to five seconds early is more
     * precise than anyone notices, whereas writing the position more often would mean a disk
     * transaction per second for a number that only matters at the next cold start.
     */
    private fun startSnapshotTracker() {
        stopSnapshotTracker()
        snapshotJob = scope.launch {
            while (isActive) {
                delay(SNAPSHOT_INTERVAL_MS)
                val songId = currentStreamSongId ?: continue
                val positionMs = exoPlayer?.currentPosition ?: continue
                runCatching { snapshotStore.savePosition(songId, positionMs.coerceAtLeast(0L)) }
            }
        }
    }

    private fun stopSnapshotTracker() {
        snapshotJob?.cancel()
        snapshotJob = null
    }

    /**
     * Commits the playhead as it is now.
     *
     * Both values are read *before* the coroutine is launched. This scope dispatches on the main
     * loop, so a save triggered by [stopPlayback] would otherwise wake up after the player had
     * already been stopped and read a position of zero.
     */
    private fun savePlayhead() {
        val songId = currentStreamSongId ?: _playbackState.value.currentSong?.id ?: return
        val positionMs = (exoPlayer?.currentPosition ?: _playbackState.value.currentPositionMs)
            .coerceAtLeast(0L)
        scope.launch {
            runCatching { snapshotStore.savePosition(songId, positionMs) }
        }
    }

    /** A drag on the seek bar is one write, not one per pixel. */
    private fun schedulePlayheadSave() {
        playheadSaveJob?.cancel()
        playheadSaveJob = scope.launch {
            delay(PLAYHEAD_SAVE_DEBOUNCE_MS)
            val songId = currentStreamSongId ?: return@launch
            val positionMs = (exoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L)
            runCatching { snapshotStore.savePosition(songId, positionMs) }
        }
    }

    /**
     * Persists the queue a beat after it changes.
     *
     * [QueueManager.setQueue] fires the queue, the index, the shuffle flag and the repeat mode in
     * one go, so debouncing turns four writes of the same JSON payload into one.
     */
    private fun scheduleQueueSnapshot() {
        queueSnapshotJob?.cancel()
        queueSnapshotJob = scope.launch {
            delay(QUEUE_SAVE_DEBOUNCE_MS)
            writeQueueSnapshot()
        }
    }

    /** Writes the queue immediately, e.g. while the last copy of it is still in memory. */
    private fun saveQueueSnapshot() {
        val songs = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        val shuffle = queueManager.isShuffle.value
        val repeatMode = queueManager.repeatMode.value
        scope.launch {
            runCatching { snapshotStore.saveQueue(songs, currentIndex, shuffle, repeatMode) }
        }
    }

    private suspend fun writeQueueSnapshot() {
        val songs = queueManager.queue.value
        // An empty queue means one of two things. On the first emissions of a new process it is
        // simply Lunara starting up, and the session on disk is the very one `restoreLastSession`
        // is about to read - clearing it here would delete the music the user came back for. Only
        // once this process has held a queue of its own does an empty one mean the user emptied it,
        // and then the stored session is stale and goes with it.
        if (songs.isEmpty() && (!hadQueueThisSession || sessionPreservedOnStop)) return
        runCatching {
            snapshotStore.saveQueue(
                songs = songs,
                currentIndex = queueManager.currentIndex.value,
                shuffle = queueManager.isShuffle.value,
                repeatMode = queueManager.repeatMode.value
            )
        }
    }

    override fun release() {
        stopProgressTracker()
        resolveJob?.cancel()
        resolveJob = null
        exoPlayer?.release()
        exoPlayer = null
        scope.cancel()
    }

    /**
     * HLS playlists must be handed to ExoPlayer's HLS source; every other URL is progressive
     * audio and is left to [mimeTypeOf].
     */
    private fun hlsMimeTypeOrNull(uri: String): String? = when {
        uri.contains(".m3u8") ||
            uri.contains("/hls_playlist/") ||
            uri.contains("manifest/hls") ||
            uri.contains("manifest.googlevideo.com") -> MimeTypes.APPLICATION_M3U8
        else -> null
    }

    /**
     * Drops the playback hints stored on a YouTube Music track so the resolver is guaranteed to
     * hand back a freshly signed URL.
     *
     * A `streamUrl` is a *signed* URL that YouTube invalidates within minutes, and any
     * `localUri` left at this point is already known to point at a deleted file, so keeping
     * either one only reproduces the failure that got us here.
     */
    private fun freshResolveTarget(song: Song): Song =
        if (song.id.startsWith(YOUTUBE_ID_PREFIX)) {
            song.copy(localUri = null, streamUrl = null)
        } else {
            song
        }

    /**
     * Explicit MIME type for the resolved stream. YouTube Music hands out Opus in WebM
     * (itag 251/250/249) and AAC in MP4 (itag 140/139); ExoPlayer's sniffing only
     * checks the first bytes of a chunk, so a wrong or missing type stalls on
     * "buffering" forever instead of playing.
     */
    @OptIn(UnstableApi::class)
    private fun mimeTypeOf(uri: String, resolved: String?): String? {
        // HLS needs its type up front; keep the detection below untouched.
        hlsMimeTypeOrNull(uri)?.let { return it }
        // Saved tracks: the file itself is asked first, because the stored extension is only a
        // guess made from the download URL. Anything unrecognised falls back to the extension
        // hint, and an unknown extension stays `null` so ExoPlayer can sniff it.
        if (!uri.startsWith("http")) {
            // A saved download can carry the wrong extension - an Opus/WebM stream stored as
            // `.m4a` is rejected by the MP4 extractor with "unsupported audio format" - and magic
            // bytes never lie.
            audioMimeTypeOf(uri)?.let { return it }
            return when (uri.substringAfterLast('.', "").lowercase()) {
                "m4a", "mp4", "aac", "m4b" -> MimeTypes.AUDIO_MP4
                "webm", "opus" -> MimeTypes.AUDIO_WEBM
                "mp3" -> MimeTypes.AUDIO_MPEG
                "ogg", "oga" -> MimeTypes.AUDIO_OGG
                "flac" -> MimeTypes.AUDIO_FLAC
                "wav" -> MimeTypes.AUDIO_WAV
                // Local files with an unknown/absent extension must never force a
                // container: `null` lets ExoPlayer sniff the real one.
                else -> null
            }
        }
        val mime = resolved.orEmpty()
        return when {
            mime.startsWith("audio/mp4") || mime.startsWith("video/mp4") -> MimeTypes.AUDIO_MP4
            mime.startsWith("audio/webm") || mime.startsWith("video/webm") -> MimeTypes.AUDIO_WEBM
            mime.startsWith("audio/mp3") || mime.startsWith("audio/mpeg") -> MimeTypes.AUDIO_MPEG
            mime.startsWith("application/x-mpegurl") ||
                mime.startsWith("application/vnd.apple.mpegurl") -> MimeTypes.APPLICATION_M3U8
            uri.contains("mime=audio%2Fmp4") || uri.contains("mime=audio/mp4") -> MimeTypes.AUDIO_MP4
            uri.contains("mime=audio%2Fwebm") || uri.contains("mime=audio/webm") -> MimeTypes.AUDIO_WEBM
            // Unknown: let ExoPlayer sniff. Forcing WebM here is what handed m4a bytes to the
            // Matroska extractor and produced a "source error" on otherwise fine streams.
            else -> null
        }
    }

    private companion object {
        /** Logcat tag for playback failures (`adb logcat -s LunaraPlayer`). */
        const val TAG = "LunaraPlayer"

        /**
         * Playback cushion. A tiny start buffer is what makes play/pause/next feel instant;
         * the large ceiling absorbs CDN hiccups without a visible re-buffer.
         */
        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 120_000
        const val BUFFER_FOR_PLAYBACK_MS = 500
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000

        /** How many upcoming tracks get their stream resolved ahead of time. */
        const val PREFETCH_AHEAD = 2

        /**
         * How many of YouTube's recommendations are appended to the queue at a time.
         *
         * Large enough that a mix keeps going for a long stretch without another round trip,
         * small enough that the queue sheet stays a queue and not a dump of a whole 50-track
         * radio.
         */
        const val RADIO_BATCH_SIZE = 25

        /**
         * How many of YouTube's pages one batch may be filled from.
         *
         * The first page of a mix is almost all new songs, but further into a radio the same
         * tracks come round again and a page can be nearly all repeats. Walking one page further
         * is what keeps a batch worth appending; the ceiling keeps a single press from reading
         * the whole mix in one go.
         */
        const val RADIO_PAGES_PER_BATCH = 2

        /**
         * How often the playhead is written to disk while music plays. This interval is the worst
         * case error of a restore, and it is deliberately larger than the 300 ms UI tick: the
         * position only has to be right when the app is next opened, not while it is running.
         */
        const val SNAPSHOT_INTERVAL_MS = 5_000L

        /** Long enough that the writes a drag on the seek bar starts collapse into one. */
        const val PLAYHEAD_SAVE_DEBOUNCE_MS = 600L

        /** Long enough that `setQueue`'s four state changes collapse into one queue write. */
        const val QUEUE_SAVE_DEBOUNCE_MS = 800L

        /**
         * Hard ceiling on one stream resolve. Without it a request that never answers left the
         * UI spinning on "buffering" for as long as the socket stayed open.
         */
        const val RESOLVE_TIMEOUT_MS = 30_000L

        /** Lunara stores every YouTube Music track under a `yt_<videoId>` id. */
        const val YOUTUBE_ID_PREFIX = "yt_"
    }
}
