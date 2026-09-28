package com.lunara.app.features.home

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
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.LibraryRepository
import com.lunara.app.domain.repository.MusicRepository
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.LunaraArtwork
import com.lunara.app.ui.components.SongListItem
import com.lunara.app.ui.theme.*
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
) {

    companion object {
        /** Live search used when the bundled album playlist returns nothing. */
        const val FEATURED_FALLBACK_QUERY = "top hits 2026"
    }
}

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

            // Home must never be empty: a live track search backs the "featured" rail even
            // when the bundled album playlist is region blocked or otherwise unavailable.
            val featured = when (
                val res = musicRepository.getAlbumTracks(_uiState.value.featuredAlbumId)
            ) {
                is com.lunara.app.core.result.Resource.Success -> res.data
                else -> emptyList()
            }.ifEmpty {
                when (val res = musicRepository.searchSongs(HomeUiState.FEATURED_FALLBACK_QUERY)) {
                    is com.lunara.app.core.result.Resource.Success -> res.data
                    else -> emptyList()
                }
            }

            // Single flow: collecting it directly keeps `history` as List<Song>.
            // `combine(flow) { ... }` binds to the vararg overload here and hands the
            // lambda an Array<List<Song>> instead, which broke the types below.
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
            .background(LunaraBackground),
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
                    color = LunaraTextPrimary
                )
                Text(
                    text = "Welcome to Lunara",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LunaraTextSecondary
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
                    iconTint = LunaraAccent,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToFavorites
                )
                LibraryShortcutCard(
                    title = "Downloads",
                    icon = Icons.Default.DownloadDone,
                    iconTint = LunaraAccentSecondary,
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
                    color = LunaraTextPrimary,
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
                    color = LunaraTextPrimary
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
                color = LunaraTextSecondary,
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
        color = LunaraSurfaceElevated,
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
                color = LunaraTextPrimary
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
        LunaraArtwork(
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
            color = LunaraTextPrimary,
            maxLines = 1
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = LunaraTextSecondary,
            maxLines = 1
        )
    }
}
