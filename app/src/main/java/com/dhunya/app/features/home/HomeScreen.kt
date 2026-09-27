package com.dhunya.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.repository.LibraryRepository
import com.dhunya.app.domain.repository.MusicRepository
import com.dhunya.app.player.PlayerManager
import com.dhunya.app.ui.components.DhunyaArtwork
import com.dhunya.app.ui.components.SongListItem
import com.dhunya.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "Welcome",
    val featuredAlbumTitle: String = "Whole Lotta Red",
    val featuredAlbumId: String = "OLAK5uy_lW6cMszmMtqMeepM6dSApqU1K2meB4ajE",
    val featuredTracks: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val playbackState = playerManager.playbackState

    init {
        determineGreeting()
        loadHomeData()
    }

    private fun determineGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        _uiState.update { it.copy(greeting = greeting) }
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val featured = when (val res = musicRepository.getAlbumTracks(_uiState.value.featuredAlbumId)) {
                    is com.dhunya.app.core.result.Resource.Success -> res.data
                    else -> emptyList()
                }

            libraryRepository.getHistory().collect { history ->
                _uiState.update {
                    it.copy(
                        recentlyPlayed = history.take(8),
                        featuredTracks = featured,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            libraryRepository.addToHistory(song)
            val queue = _uiState.value.featuredTracks.ifEmpty { listOf(song) }
            val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            playerManager.playQueue(queue, index)
        }
    }

    fun playFeaturedAlbum() {
        val tracks = _uiState.value.featuredTracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.firstOrNull()?.let { libraryRepository.addToHistory(it) }
            playerManager.playQueue(tracks, 0)
        }
    }
}

@Composable
fun HomeScreen(
    onNavigateToFavorites: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onSongActionClick: (Song) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DhunyaBackground),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top Greeting Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = uiState.greeting,
                    style = MaterialTheme.typography.displayLarge,
                    color = DhunyaTextPrimary
                )
                Text(
                    text = "Welcome to Dhunya",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DhunyaTextSecondary
                )
            }
        }

        // Library Quick Shortcuts (Favorites & Downloads)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibraryShortcutCard(
                    title = "Favorites",
                    icon = Icons.Default.Favorite,
                    iconTint = DhunyaAccent,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToFavorites
                )
                LibraryShortcutCard(
                    title = "Downloads",
                    icon = Icons.Default.DownloadDone,
                    iconTint = DhunyaAccentSecondary,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToDownloads
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Recently Played Section
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                Text(
                    text = "Recently played",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunyaTextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    items(uiState.recentlyPlayed, key = { it.id }) { song ->
                        RecentlyPlayedCard(
                            song = song,
                            onClick = { viewModel.playSong(song) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Featured album section (Whole Lotta Red, live from YouTube Music)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uiState.featuredAlbumTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunyaTextPrimary
                )
                if (uiState.featuredTracks.isNotEmpty()) {
                    FilledTonalButton(onClick = { viewModel.playFeaturedAlbum() }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play album")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play")
                    }
                }
            }
            Text(
                text = uiState.featuredTracks.firstOrNull()?.let { first ->
                    val artist = first.artistName.takeIf { it.isNotBlank() } ?: "YouTube Music"
                    "${uiState.featuredTracks.size} tracks · $artist"
                } ?: "Loading album…",
                style = MaterialTheme.typography.bodyMedium,
                color = DhunyaTextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        items(uiState.featuredTracks, key = { it.id }) { song ->
            SongListItem(
                song = song,
                isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                onSongClick = { viewModel.playSong(song) },
                onMoreClick = { onSongActionClick(song) }
            )
        }
    }
}

@Composable
private fun LibraryShortcutCard(
    title: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = DhunyaSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = DhunyaTextPrimary
            )
        }
    }
}

@Composable
private fun RecentlyPlayedCard(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        DhunyaArtwork(
            url = song.artworkUrl,
            contentDescription = song.title,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            color = DhunyaTextPrimary,
            maxLines = 1
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunyaTextSecondary,
            maxLines = 1
        )
    }
}
