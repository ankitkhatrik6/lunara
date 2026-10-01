package com.lunara.app.features.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.app.domain.model.Playlist
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.LibraryRepository
import com.lunara.app.domain.repository.MusicRepository
import com.lunara.app.domain.usecase.ManagePlaylistUseCase
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.LunaraArtwork
import com.lunara.app.ui.components.EmptyStateView
import com.lunara.app.ui.components.SongListItem
import com.lunara.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab {
    FAVORITES,
    PLAYLISTS,
    HISTORY,
    ON_DEVICE
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.FAVORITES,
    val favorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<Song> = emptyList(),
    /** The device's own audio files, as stored by the last MediaStore sync. */
    val localSongs: List<Song> = emptyList(),
    /** False until the user grants the audio read permission, which is what gates a scan. */
    val canReadDeviceAudio: Boolean = false,
    /** The permission to request on this OS version; empty only before the first state update. */
    val deviceAudioPermission: String = "",
    val isScanningDevice: Boolean = false,
    /** One line about the last scan, shown as a banner and cleared after a few seconds. */
    val deviceScanMessage: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val musicRepository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val playbackState = playerManager.playbackState

    init {
        observeLibraryData()
        refreshDeviceAudioPermission()
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
        viewModelScope.launch {
            musicRepository.observeLocalSongs().collect { songs ->
                _uiState.update { it.copy(localSongs = songs) }
            }
        }
    }

    /**
     * Re-reads the permission state.
     *
     * Called on every Library open: the user can revoke a permission in system settings while
     * Lunara is merely paused, and a screen that still believes it may scan would then just
     * report "no music found".
     */
    fun refreshDeviceAudioPermission() {
        _uiState.update {
            it.copy(
                canReadDeviceAudio = musicRepository.canReadDeviceAudio(),
                deviceAudioPermission = musicRepository.deviceAudioPermission()
            )
        }
    }

    /** Mirrors the device's audio library into the local database and reports what it found. */
    fun loadLocalMusic() {
        if (_uiState.value.isScanningDevice) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningDevice = true, deviceScanMessage = null) }

            val scan = runCatching { musicRepository.getLocalSongs() }
            val found = scan.getOrDefault(emptyList()).size

            _uiState.update {
                it.copy(
                    isScanningDevice = false,
                    canReadDeviceAudio = musicRepository.canReadDeviceAudio(),
                    deviceScanMessage = if (scan.isSuccess) {
                        when (found) {
                            0 -> "No music files found on this device."
                            1 -> "1 track loaded from this device."
                            else -> "$found tracks loaded from this device."
                        }
                    } else {
                        scan.exceptionOrNull()?.localizedMessage
                            ?: "Could not read this device's music."
                    }
                )
            }
        }
    }

    /**
     * The answer to the system permission dialog.
     *
     * A granted permission is followed straight by a scan, because asking and then making the
     * user tap "Load" a second time is one tap of pure ceremony.
     */
    fun onDeviceAudioPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(canReadDeviceAudio = musicRepository.canReadDeviceAudio()) }
        if (granted) {
            loadLocalMusic()
        } else {
            _uiState.update {
                it.copy(deviceScanMessage = "Lunara needs access to your audio files to load them.")
            }
        }
    }

    fun dismissDeviceScanMessage() {
        _uiState.update { it.copy(deviceScanMessage = null) }
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
            // Playing a device track queues the rest of the device's music, so the next track is
            // whatever comes after it instead of a queue of one.
            LibraryTab.ON_DEVICE -> _uiState.value.localSongs
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

    // Re-read the permission whenever the Library is opened: it can have been revoked in system
    // settings while Lunara was merely in the background.
    LaunchedEffect(Unit) { viewModel.refreshDeviceAudioPermission() }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onDeviceAudioPermissionResult(granted) }

    // The result line about a scan is transient: it says what just happened, then gets out of
    // the way rather than sitting above the list for the rest of the session.
    LaunchedEffect(uiState.deviceScanMessage) {
        if (uiState.deviceScanMessage != null) {
            delay(5000)
            viewModel.dismissDeviceScanMessage()
        }
    }

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

        // Tab Selector Row - scrolls, because four chips no longer fit a narrow phone in one line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
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
            LibraryTabChip(
                label = "On device (${uiState.localSongs.size})",
                selected = uiState.selectedTab == LibraryTab.ON_DEVICE,
                onClick = { viewModel.selectTab(LibraryTab.ON_DEVICE) }
            )
        }

        uiState.deviceScanMessage?.let { message ->
            DeviceScanBanner(
                message = message,
                onDismiss = { viewModel.dismissDeviceScanMessage() }
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

            LibraryTab.ON_DEVICE -> {
                when {
                    // No permission yet: nothing to scan, so the tab explains itself and offers
                    // the one tap that changes it instead of showing an empty list.
                    !uiState.canReadDeviceAudio -> DeviceAudioPermissionCard(
                        onAllowClick = {
                            // Guarded because `launch` throws on a blank permission, and the state
                            // is only ever blank for the instant before the ViewModel's first
                            // update.
                            val permission = uiState.deviceAudioPermission
                            if (permission.isNotEmpty()) {
                                audioPermissionLauncher.launch(permission)
                            }
                        }
                    )

                    uiState.isScanningDevice -> DeviceScanProgress()

                    uiState.localSongs.isEmpty() -> Column(modifier = Modifier.fillMaxSize()) {
                        EmptyStateView(
                            icon = Icons.Outlined.LibraryMusic,
                            title = "Nothing here yet",
                            subtitle = "Lunara will look through your Music folder, Downloads and SD card. Nothing leaves this phone.",
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.loadLocalMusic() },
                            colors = ButtonDefaults.buttonColors(containerColor = LunaraAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        ) {
                            Text("Load local music", color = LunaraBackground)
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        item(key = "device_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.localSongs.size} on this device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LunaraTextSecondary
                                )
                                TextButton(onClick = { viewModel.loadLocalMusic() }) {
                                    Text("Rescan", color = LunaraAccent)
                                }
                            }
                        }
                        items(uiState.localSongs, key = { "device_${it.id}" }) { song ->
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

/**
 * Shown on the "On device" tab until Android lets Lunara read the phone's audio library.
 *
 * It spells out what the permission is for and what is *not* done with it, because "allow access
 * to your files" is exactly the kind of dialog people are right to refuse.
 */
@Composable
private fun DeviceAudioPermissionCard(onAllowClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = LunaraAccent,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Play your own music",
            style = MaterialTheme.typography.titleLarge,
            color = LunaraTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Lunara can play the audio files already on this phone - your Music folder, " +
                "Downloads and SD card. Android asks for permission first. They are only read " +
                "here: nothing is uploaded and nothing is copied.",
            style = MaterialTheme.typography.bodyMedium,
            color = LunaraTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAllowClick,
            colors = ButtonDefaults.buttonColors(containerColor = LunaraAccent)
        ) {
            Text("Allow access", color = LunaraBackground)
        }
    }
}

/** The MediaStore scan in progress. A long library takes a moment, and silence looks like a bug. */
@Composable
private fun DeviceScanProgress() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = LunaraAccent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Looking through this device\u2026",
            style = MaterialTheme.typography.bodyMedium,
            color = LunaraTextSecondary
        )
    }
}

/** The one-line result of the last scan ("24 tracks loaded from this device."). */
@Composable
private fun DeviceScanBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LunaraSurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LunaraTextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = LunaraTextSecondary
                )
            }
        }
    }
}
