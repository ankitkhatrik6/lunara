package com.lunara.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lunara.app.core.result.Resource
import com.lunara.app.data.remote.innertube.InnerTubeClients
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
    private val musicRepository: MusicRepository
) : LunaraPlayer {

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

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        initializePlayer()
        observeQueue()
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(InnerTubeClients.STREAM_USER_AGENT)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)

        // Stock data source stack - `DefaultDataSource` (files, `content://`, assets) in front
        // of a plain `DefaultHttpDataSource`, exactly what every InnerTune-derived player uses -
        // wrapped so that requests against YouTube always carry a *closed* byte range. Without
        // that wrapper the CDN throttles them to ~31 KB/s and playback only ever buffers; the
        // measurements are in [RangedHttpDataSourceFactory]'s documentation.
        val dataSourceFactory = RangedHttpDataSourceFactory(
            DefaultDataSource.Factory(context, httpDataSourceFactory)
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
            .setWakeMode(C.WAKE_MODE_LOCAL)
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
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // A resolved URL can die mid playback (expired signature, throttled edge,
                // PO-token gate). Swapping to the next resolved format keeps the music going
                // instead of dropping out with "source error".
                val song = _playbackState.value.currentSong
                currentStreamUri?.let { failedUris.add(it) }
                // Remember that this track's URL is dead so the next attempt asks for a new
                // signature instead of being served the expired one from the resolver cache.
                song?.id?.let { staleStreamSongIds.add(it) }

                val nextUri = currentFallbackUris.firstOrNull { it !in failedUris }
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
                            reportError(error.localizedMessage ?: "Playback failed")
                        }
                    }
                    return
                }

                reportError(error.localizedMessage ?: "Playback error occurred")
            }
        })

        exoPlayer = player
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
            }
        }
        scope.launch {
            queueManager.currentIndex.collect { idx ->
                _playbackState.value = _playbackState.value.copy(
                    queueIndex = idx,
                    currentSong = queueManager.currentSong
                )
            }
        }
        scope.launch {
            queueManager.isShuffle.collect { shuffle ->
                _playbackState.value = _playbackState.value.copy(shuffleEnabled = shuffle)
            }
        }
        scope.launch {
            queueManager.repeatMode.collect { mode ->
                _playbackState.value = _playbackState.value.copy(repeatMode = mode)
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
            val result = try {
                musicRepository.resolvePlayableMedia(
                    freshResolveTarget(song),
                    // `remove` also consumes the flag: one forced refresh per CDN failure.
                    forceRefresh = staleStreamSongIds.remove(song.id) != null
                )
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
        val path = runCatching { Uri.parse(uri).path }.getOrNull() ?: return null
        return if (File(path).let { it.exists() && it.length() > 0L }) uri else null
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
        player.prepare()
        player.play()

        _playbackState.value = _playbackState.value.copy(
            currentSong = song,
            isLoading = true,
            errorMessage = null,
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
        // Two tracks ahead, so "next" is instant even when the queue is being skipped quickly.
        val upcoming = queueManager.upcomingSongs()
            .filter { it.localUri.isNullOrBlank() && it.streamUrl.isNullOrBlank() }
            .take(PREFETCH_AHEAD)
        if (upcoming.isEmpty()) return
        scope.launch {
            upcoming.forEach { song ->
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

    fun playNext() {
        val nextSong = queueManager.next()
        if (nextSong != null) {
            playSong(nextSong)
        } else {
            pause()
            seekTo(0L)
        }
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
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        queueManager.clearQueue()
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
        // Saved tracks: the container is encoded in the stored extension, which is a far
        // safer hint than guessing. Only unambiguous extensions are used; anything else
        // stays `null` so ExoPlayer can sniff it.
        if (!uri.startsWith("http")) {
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

        /** Lunara stores every YouTube Music track under a `yt_<videoId>` id. */
        const val YOUTUBE_ID_PREFIX = "yt_"
    }
}
