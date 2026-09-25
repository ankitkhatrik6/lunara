package com.dhunya.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import com.dhunya.app.domain.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface DhunyaPlayer {
    val playbackState: StateFlow<PlaybackState>
    fun playSong(song: Song)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

@Singleton
class PlayerManager @Inject constructor(
    private val context: Context,
    val queueManager: QueueManager
) : DhunyaPlayer {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        initializePlayer()
        observeQueue()
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        val player = ExoPlayer.Builder(context)
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
                _playbackState.value = _playbackState.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Playback error occurred"
                )
            }
        })

        exoPlayer = player
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
        val uri = song.localUri ?: song.streamUrl ?: return

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artistName)
            .setAlbumTitle(song.albumName)
            .setArtworkUri(song.artworkUrl?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(mediaMetadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        _playbackState.value = _playbackState.value.copy(
            currentSong = song,
            isLoading = true,
            errorMessage = null,
            totalDurationMs = song.durationMs
        )
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
        exoPlayer?.play()
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
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
        exoPlayer?.release()
        exoPlayer = null
        scope.cancel()
    }
}
