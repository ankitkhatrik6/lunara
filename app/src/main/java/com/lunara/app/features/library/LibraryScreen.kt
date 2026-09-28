package com.lunara.app.features.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.app.domain.model.Playlist
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.LibraryRepository
import com.lunara.app.domain.usecase.ManagePlaylistUseCase
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.LunaraArtwork
import com.lunara.app.ui.components.EmptyStateView
import com.lunara.app.ui.components.SongListItem
import com.lunara.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab {
    FAVORITES,
    PLAYLISTS,
    HISTORY
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.FAVORITES,
    val favorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<Song> = emptyList()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val playbackState = playerManager.playbackState

    init {
        observeLibraryData()
    }

    private fun observeLibraryData() {
        viewModelScope.launch {
            libraryRepository.getFavorites().collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
        viewModelScope.launch {
            managePlaylistUseCase.getPlaylists().collect { lists ->
                _uiState.update { it.copy(playlists = lists) }
            }
        }
        viewModelScope.launch {
            libraryRepository.getHistory().collect { hist ->
                _uiState.update { it.copy(history = hist) }
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            managePlaylistUseCase.create(name.trim())
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            managePlaylistUseCase.delete(playlistId)
        }
    }

    fun playSong(song: Song) {
        val currentList = when (_uiState.value.selectedTab) {
            LibraryTab.FAVORITES -> _uiState.value.favorites
            LibraryTab.HISTORY -> _uiState.value.history
            else -> listOf(song)
        }
        // `indexOfFirst` is -1 when the tapped song is no longer in the visible list;
        // `coerceAtLeast(0)` would then silently start an unrelated track instead.
        val idx = currentList.indexOfFirst { it.id == song.id }
        if (idx >= 0) {
            playerManager.playQueue(currentList, idx)
        } else {
            playerManager.playQueue(listOf(song), 0)
        }
    }
}

@Composable
fun LibraryScreen(
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onSongActionClick: (Song) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LunaraBackground)
    ) {
        // Library Title and Action Icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.displayLarge,
                color = LunaraTextPrimary
            )
            Row {
                IconButton(onClick = onNavigateToDownloads) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Downloads",
                        tint = LunaraAccentSecondary
                    )
                }
                IconButton(onClick = { showNewPlaylistDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = LunaraAccent
                    )
                }
            }
        }

        // Tab Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryTabChip(
                label = "Favorites (${uiState.favorites.size})",
                selected = uiState.selectedTab == LibraryTab.FAVORITES,
                onClick = { viewModel.selectTab(LibraryTab.FAVORITES) }
            )
            LibraryTabChip(
                label = "Playlists (${uiState.playlists.size})",
                selected = uiState.selectedTab == LibraryTab.PLAYLISTS,
                onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) }
            )
            LibraryTabChip(
                label = "History",
                selected = uiState.selectedTab == LibraryTab.HISTORY,
                onClick = { viewModel.selectTab(LibraryTab.HISTORY) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Contents
        when (uiState.selectedTab) {
            LibraryTab.FAVORITES -> {
                if (uiState.favorites.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.FavoriteBorder,
                        title = "No favorites yet",
                        subtitle = "Songs you favorite will appear here."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(uiState.favorites, key = { "fav_${it.id}" }) { song ->
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

            LibraryTab.PLAYLISTS -> {
                if (uiState.playlists.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.QueueMusic,
                        title = "No playlists created",
                        subtitle = "Tap '+' to create your first personal playlist."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(uiState.playlists, key = { "playlist_${it.id}" }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToPlaylist(playlist.id) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LunaraArtwork(
                                    url = playlist.coverArtworkUrl,
                                    contentDescription = playlist.name,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LunaraTextPrimary
                                    )
                                    Text(
                                        text = "${playlist.songCount} songs",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LunaraTextSecondary
                                    )
                                }
                                IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete playlist",
                                        tint = LunaraTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibraryTab.HISTORY -> {
                if (uiState.history.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.History,
                        title = "No listening history",
                        subtitle = "Tracks you play will show up here."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(uiState.history, key = { "history_${it.id}" }) { song ->
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

    // Create New Playlist Dialog
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = {
                Text("New playlist", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LunaraAccent,
                        unfocusedBorderColor = LunaraBorder,
                        focusedTextColor = LunaraTextPrimary,
                        unfocusedTextColor = LunaraTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showNewPlaylistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LunaraAccent)
                ) {
                    Text("Create", color = LunaraBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text("Cancel", color = LunaraTextSecondary)
                }
            },
            containerColor = LunaraSurfaceElevated
        )
    }
}

@Composable
private fun LibraryTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) LunaraAccent else LunaraSurfaceElevated,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) LunaraBackground else LunaraTextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
