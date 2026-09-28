package com.lunara.app.features.share

import com.lunara.app.domain.model.Song
import com.lunara.app.domain.usecase.GetLyricsUseCase
import com.lunara.app.player.PlayerManager
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Renders a song's Instagram style poster in the background and emits it through
 * [posterToShare]. The UI that collects it is responsible for opening the system share sheet
 * (see [SharePosterEffect]) and clearing the event via [onPosterConsumed].
 */
@HiltViewModel
class ShareSongViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val getLyricsUseCase: GetLyricsUseCase,
    private val posterGenerator: SongPosterGenerator
) : androidx.lifecycle.ViewModel() {

    private val _posterToShare = MutableStateFlow<File?>(null)
    val posterToShare: StateFlow<File?> = _posterToShare.asStateFlow()

    private val _isPreparingShare = MutableStateFlow(false)
    val isPreparingShare: StateFlow<Boolean> = _isPreparingShare.asStateFlow()

    /** Shares [song], or the currently playing track when [song] is omitted. */
    fun share(song: Song? = null) {
        val target = song ?: playerManager.playbackState.value.currentSong ?: return
        if (_isPreparingShare.value) return
        viewModelScope.launch {
            _isPreparingShare.value = true
            val playing = playerManager.playbackState.value.currentSong
            val positionMs = if (playing?.id == target.id) {
                playerManager.playbackState.value.currentPositionMs
            } else {
                0L
            }
            val file = runCatching {
                posterGenerator.generate(target, positionMs) {
                    getLyricsUseCase(
                        target.title,
                        target.artistName,
                        target.albumName,
                        (target.durationMs / 1000).toInt()
                    )
                }
            }.getOrNull()
            _posterToShare.value = file
            _isPreparingShare.value = false
        }
    }

    fun onPosterConsumed() {
        _posterToShare.value = null
    }
}
