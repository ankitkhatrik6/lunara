package com.dhunya.app.features.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.usecase.ManagePlaylistUseCase
import com.dhunya.app.player.PlayerManager
import com.dhunya.app.ui.components.EmptyStateView
import com.dhunya.app.ui.components.SongListItem
import com.dhunya.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlistId: Long = 0,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: 0L

    private val _uiState = MutableStateFlow(PlaylistDetailUiState(playlistId = playlistId))
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    val playbackState = playerManager.playbackState

    init {
        loadSongs()
    }

    private fun loadSongs() {
        viewModelScope.launch {
            managePlaylistUseCase.getPlaylistSongs(playlistId).collect { songs ->
                _uiState.update { it.copy(songs = songs) }
            }
        }
    }

    fun playAll(shuffle: Boolean = false) {
        val songs = _uiState.value.songs
        if (songs.isNotEmpty()) {
            if (shuffle) {
                playerManager.queueManager.setQueue(songs.shuffled(), 0)
            } else {
                playerManager.playQueue(songs, 0)
            }
        }
    }

    fun playSong(song: Song) {
        val songs = _uiState.value.songs
        val idx = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playerManager.playQueue(songs, idx)
    }

    fun removeSong(songId: String) {
        viewModelScope.launch {
            managePlaylistUseCase.removeSong(playlistId, songId)
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    onNavigateBack: () -> Unit,
    onSongActionClick: (Song) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Scaffold(
        containerColor = DhunyaBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = DhunyaTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunyaTextPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header with Play & Shuffle Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.playAll(shuffle = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = DhunyaAccent),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = DhunyaBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", color = DhunyaBackground)
                }

                OutlinedButton(
                    onClick = { viewModel.playAll(shuffle = true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DhunyaTextPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = DhunyaTextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", color = DhunyaTextPrimary)
                }
            }

            if (uiState.songs.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.PlayArrow,
                    title = "Playlist is empty",
                    subtitle = "Add songs from search or library using the overflow menu."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(uiState.songs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                            onSongClick = { viewModel.playSong(song) },
                            onMoreClick = { onSongActionClick(song) }
                        )
                    }
                }
            }
        }
    }
}
