package com.dhunya.app.features.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhunya.app.domain.model.Playlist
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.usecase.ManagePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for [PlaylistPickerSheet]: the playlists plus the name of the one the current song
 * was just added to (shown as a one-shot checkmark).
 */
@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(
    private val managePlaylistUseCase: ManagePlaylistUseCase
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _justAddedTo = MutableStateFlow<String?>(null)
    val justAddedTo: StateFlow<String?> = _justAddedTo.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        viewModelScope.launch {
            managePlaylistUseCase.getPlaylists().collect { lists ->
                _playlists.value = lists
            }
        }
    }

    fun addToPlaylist(song: Song, playlist: Playlist) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            runCatching { managePlaylistUseCase.addSong(playlist.id, song) }
                .onSuccess { _justAddedTo.value = playlist.name }
            _isSaving.value = false
        }
    }

    fun createAndAdd(song: Song, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            runCatching {
                val id = managePlaylistUseCase.create(trimmed)
                managePlaylistUseCase.addSong(id, song)
            }.onSuccess { _justAddedTo.value = trimmed }
            _isSaving.value = false
        }
    }

    fun onConfirmationShown() {
        _justAddedTo.value = null
    }
}
