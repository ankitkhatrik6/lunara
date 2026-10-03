package com.lunara.app.features.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.app.core.result.Resource
import com.lunara.app.domain.model.BrowseShelf
import com.lunara.app.domain.model.BrowseCard
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.MusicRepository
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.LunaraArtwork
import com.lunara.app.ui.components.SongListItem
import com.lunara.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseDetailUiState(
    val title: String = "Explore",
    val artworkUrl: String? = null,
    val shelves: List<BrowseShelf> = emptyList(),
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BrowseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId").orEmpty()
    private val _uiState = MutableStateFlow(
        BrowseDetailUiState(
            title = savedStateHandle.get<String>("title") ?: "Explore",
            artworkUrl = savedStateHandle.get<String>("artworkUrl")?.takeIf { it.isNotBlank() }
        )
    )
    val uiState: StateFlow<BrowseDetailUiState> = _uiState.asStateFlow()
    val playbackState = playerManager.playbackState

    init {
        viewModelScope.launch {
            val shelves = (musicRepository.getBrowseShelves(browseId) as? Resource.Success)
                ?.data.orEmpty()
            val shelfSongs = shelves.flatMap { it.songs }.distinctBy { it.id }
            val fallback = if (shelfSongs.isEmpty()) {
                (musicRepository.getAlbumTracks(browseId) as? Resource.Success)?.data.orEmpty()
            } else emptyList()
            _uiState.update {
                it.copy(
                    shelves = shelves,
                    songs = (shelfSongs + fallback).distinctBy { song -> song.id },
                    isLoading = false
                )
            }
        }
    }

    fun play(song: Song) {
        val songs = _uiState.value.songs
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playerManager.playQueue(songs.ifEmpty { listOf(song) }, index)
    }

    fun playAll() {
        _uiState.value.songs.takeIf { it.isNotEmpty() }?.let { playerManager.playQueue(it, 0) }
    }
}

@Composable
fun BrowseDetailScreen(
    onNavigateBack: () -> Unit,
    onSongActionClick: (Song) -> Unit,
    onBrowseCardClick: (BrowseCard) -> Unit,
    viewModel: BrowseDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val playback by viewModel.playbackState.collectAsState()

    Scaffold(
        containerColor = LunaraBackground,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LunaraTextPrimary)
                }
                Text(state.title, style = MaterialTheme.typography.titleLarge, color = LunaraTextPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(LunaraBackground),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    state.artworkUrl?.let { artwork ->
                        LunaraArtwork(
                            url = artwork,
                            contentDescription = state.title,
                            modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
                            cornerRadius = 20.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(state.title, style = MaterialTheme.typography.headlineSmall, color = LunaraTextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (state.songs.isNotEmpty()) {
                        FilledTonalButton(onClick = viewModel::playAll) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play all")
                        }
                    }
                }
            }
            if (state.songs.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = "No playable tracks found on this page.",
                        color = LunaraTextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            items(state.songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = playback.currentSong?.id == song.id && playback.isPlaying,
                    onSongClick = { viewModel.play(song) },
                    onMoreClick = { onSongActionClick(song) }
                )
            }
            state.shelves.filter { it.title.isNotBlank() && it.songs.isEmpty() }.forEach { shelf ->
                item {
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = LunaraTextPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    shelf.cards.forEach { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LunaraArtwork(
                                url = card.artworkUrl,
                                contentDescription = card.title,
                                modifier = Modifier.size(56.dp),
                                cornerRadius = 12.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(card.title, color = LunaraTextPrimary, maxLines = 1)
                                card.subtitle?.let { Text(it, color = LunaraTextSecondary, maxLines = 1) }
                            }
                            TextButton(onClick = { onBrowseCardClick(card) }) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}
