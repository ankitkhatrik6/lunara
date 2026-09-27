package com.dhunya.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.dhunya.app.core.result.Resource
import com.dhunya.app.data.remote.innertube.InnerTubeClients
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    val queueManager: QueueManager,
    private val musicRepository: MusicRepository
) : DhunyaPlayer {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var resolveJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        initializePlayer()
        observeQueue()
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        // YouTube's CDN serves plain byte ranges (the probe showed open ended ranges
        // answer 206), so a stock HTTP source works. Rewriting every read into 1 MiB
        // closed chunks made ExoPlayer re-request mid playback and stall forever on
        // high bitrate audio, which surfaced as "music never starts/plays".
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(InnerTubeClients.STREAM_USER_AGENT)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = RangedHttpDataSourceFactory(
            DefaultDataSource.Factory(context, httpDataSourceFactory)
        )

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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

        // Locally stored / already resolved tracks can start straight away.
        val directUri = song.localUri ?: song.streamUrl
        if (!directUri.isNullOrBlank()) {
            startPlayback(player, song, directUri)
            return
        }

        // YouTube Music tracks only carry metadata: the signed audio URL has to be
        // resolved on demand (InnerTube `player` endpoint) before ExoPlayer can open it.
        resolveJob?.cancel()
        _playbackState.value = _playbackState.value.copy(
            currentSong = song,
            isLoading = true,
            isPlaying = false,
            errorMessage = null,
            totalDurationMs = song.durationMs
        )
        resolveJob = scope.launch {
            val result = musicRepository.resolvePlayableMedia(song)
            val media = (result as? Resource.Success)?.data
            if (media == null) {
                _playbackState.value = _playbackState.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = (result as? Resource.Error)?.message
                        ?: "Could not resolve audio stream for \"${song.title}\""
                )
                return@launch
            }
            val uri = media.mediaUri
            if (uri.isBlank()) {
                _playbackState.value = _playbackState.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = "No playable audio stream for \"${song.title}\""
                )
                return@launch
            }
            val playableSong = media.song.copy(streamUrl = uri)
            queueManager.updateSong(playableSong)
            startPlayback(player, playableSong, uri, media.mimeType)
        }
    }

    /**
     * Hands [uri] to ExoPlayer.
     *
     * [mimeType] is the mime type the InnerTube format declared (e.g.
     * `audio/webm; codecs="opus"`); passing it through avoids ExoPlayer sniffing a
     * single chunk and settling on the wrong extractor, which stalls on "buffering".
     */
    private fun startPlayback(
        player: ExoPlayer,
        song: Song,
        uri: String,
        mimeType: String? = null
    ) {
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
        resolveJob?.cancel()
        resolveJob = null
        exoPlayer?.release()
        exoPlayer = null
        scope.cancel()
    }

    /**
     * Explicit MIME type for the resolved stream. YouTube Music hands out Opus in WebM
     * (itag 251/250/249) and AAC in MP4 (itag 140/139); ExoPlayer's sniffing only
     * checks the first bytes of a chunk, so a wrong or missing type stalls on
     * "buffering" forever instead of playing.
     */
    @OptIn(UnstableApi::class)
    private fun mimeTypeOf(uri: String, resolved: String?): String {
        streamMimeTypeOrNull(uri)?.let { return it }
        val mime = resolved.orEmpty()
        return when {
            mime.startsWith("audio/mp4") || mime.startsWith("video/mp4") -> MimeTypes.AUDIO_MP4
            mime.startsWith("audio/webm") || mime.startsWith("video/webm") -> MimeTypes.AUDIO_WEBM
            mime.startsWith("audio/mp3") || mime.startsWith("audio/mpeg") -> MimeTypes.AUDIO_MPEG
            mime.startsWith("application/x-mpegurl") ||
                mime.startsWith("application/vnd.apple.mpegurl") -> MimeTypes.APPLICATION_M3U8
            uri.contains("mime=audio%2Fmp4") || uri.contains("mime=audio/mp4") -> MimeTypes.AUDIO_MP4
            uri.contains("mime=audio%2Fwebm") || uri.contains("mime=audio/webm") -> MimeTypes.AUDIO_WEBM
            else -> MimeTypes.AUDIO_WEBM
        }
    }
}
