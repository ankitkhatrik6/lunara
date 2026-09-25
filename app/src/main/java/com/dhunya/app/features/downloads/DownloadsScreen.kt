package com.dhunya.app.features.downloads

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhunya.app.domain.model.DownloadItem
import com.dhunya.app.domain.model.DownloadStatus
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.usecase.ManageDownloadsUseCase
import com.dhunya.app.player.PlayerManager
import com.dhunya.app.ui.components.DhunyaArtwork
import com.dhunya.app.ui.components.EmptyStateView
import com.dhunya.app.ui.components.SongListItem
import com.dhunya.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val manageDownloadsUseCase: ManageDownloadsUseCase,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val playbackState = playerManager.playbackState

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            manageDownloadsUseCase.getDownloads().collect { list ->
                _uiState.update { it.copy(downloads = list) }
            }
        }
    }

    fun removeDownload(songId: String) {
        viewModelScope.launch {
            manageDownloadsUseCase.remove(songId)
        }
    }

    fun playSong(song: Song) {
        val songs = _uiState.value.downloads.map { it.song }
        val idx = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playerManager.playQueue(songs, idx)
    }
}

@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    onSongActionClick: (Song) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
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
                    text = "Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunyaTextPrimary
                )
            }
        }
    ) { padding ->
        if (uiState.downloads.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.DownloadDone,
                title = "No downloads yet",
                subtitle = "Tracks saved for offline listening will appear here.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(uiState.downloads, key = { it.song.id }) { item ->
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        // In-progress item
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DhunyaArtwork(url = item.song.artworkUrl, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.song.title, style = MaterialTheme.typography.titleMedium, color = DhunyaTextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    color = DhunyaAccent,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Downloading... ${(item.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DhunyaAccent
                                )
                            }
                        }
                    } else {
                        // Completed item
                        SongListItem(
                            song = item.song,
                            isPlaying = playbackState.currentSong?.id == item.song.id && playbackState.isPlaying,
                            onSongClick = { viewModel.playSong(item.song) },
                            onMoreClick = { onSongActionClick(item.song) }
                        )
                    }
                }
            }
        }
    }
}
