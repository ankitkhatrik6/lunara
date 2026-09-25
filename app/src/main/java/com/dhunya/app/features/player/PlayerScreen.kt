package com.dhunya.app.features.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhunya.app.core.extensions.formatDurationMs
import com.dhunya.app.domain.model.RepeatMode
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.repository.DownloadRepository
import com.dhunya.app.domain.repository.LibraryRepository
import com.dhunya.app.player.PlaybackState
import com.dhunya.app.player.PlayerManager
import com.dhunya.app.ui.components.DhunyaArtwork
import com.dhunya.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun playNext() = playerManager.playNext()
    fun playPrevious() = playerManager.playPrevious()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun toggleShuffle() = playerManager.queueManager.toggleShuffle()
    fun cycleRepeatMode() = playerManager.queueManager.cycleRepeatMode()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(song)
        }
    }

    fun startDownload(song: Song) {
        viewModelScope.launch {
            downloadRepository.startDownload(song)
        }
    }

    fun playQueueIndex(index: Int) {
        val song = playerManager.queueManager.playTrackAt(index)
        if (song != null) {
            playerManager.playSong(song)
        }
    }

    fun removeFromQueue(index: Int) {
        playerManager.queueManager.removeAt(index)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.playbackState.collectAsState()
    val song = state.currentSong

    var showQueueSheet by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.currentPositionMs) {
        if (!isDraggingSlider && state.totalDurationMs > 0) {
            sliderPosition = state.currentPositionMs.toFloat()
        }
    }

    Scaffold(
        containerColor = DhunyaBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = DhunyaTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunyaTextSecondary
                )

                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Playing queue",
                        tint = DhunyaTextPrimary
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            if (song == null) {
                Text(
                    text = "No track playing",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunyaTextSecondary
                )
            } else {
                // Dominant Artwork
                DhunyaArtwork(
                    url = song.artworkUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)),
                    cornerRadius = 20.dp
                )

                // Song Title & Artist with Favorite Icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = DhunyaTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artistName,
                            style = MaterialTheme.typography.titleMedium,
                            color = DhunyaTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) DhunyaAccent else DhunyaTextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Scrubbing Progress Slider & Timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            isDraggingSlider = true
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            viewModel.seekTo(sliderPosition.toLong())
                        },
                        valueRange = 0f..(if (state.totalDurationMs > 0) state.totalDurationMs.toFloat() else 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = DhunyaAccent,
                            activeTrackColor = DhunyaAccent,
                            inactiveTrackColor = DhunyaSurfaceElevated
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sliderPosition.toLong().formatDurationMs(),
                            style = MaterialTheme.typography.labelSmall,
                            color = DhunyaTextSecondary
                        )
                        Text(
                            text = state.totalDurationMs.formatDurationMs(),
                            style = MaterialTheme.typography.labelSmall,
                            color = DhunyaTextSecondary
                        )
                    }
                }

                // Primary Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffleEnabled) DhunyaAccent else DhunyaTextSecondary
                        )
                    }

                    // Previous Track
                    IconButton(
                        onClick = viewModel::playPrevious,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = DhunyaTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play/Pause Center Action
                    Surface(
                        shape = CircleShape,
                        color = DhunyaAccent,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .clickable(onClick = viewModel::togglePlayPause),
                        tonalElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = DhunyaBackground,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = DhunyaBackground,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    // Next Track
                    IconButton(
                        onClick = viewModel::playNext,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next track",
                            tint = DhunyaTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat Button
                    IconButton(onClick = viewModel::cycleRepeatMode) {
                        val icon = when (state.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Repeat mode",
                            tint = if (state.repeatMode != RepeatMode.OFF) DhunyaAccent else DhunyaTextSecondary
                        )
                    }
                }

                // Secondary Quick Actions (Lyrics & Downloads)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onNavigateToLyrics) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = DhunyaAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lyrics", color = DhunyaAccent)
                    }

                    IconButton(onClick = { viewModel.startDownload(song) }) {
                        Icon(
                            imageVector = if (song.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = "Download track",
                            tint = if (song.isDownloaded) DhunyaAccent else DhunyaTextSecondary
                        )
                    }
                }
            }
        }
    }

    // Queue Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = DhunyaSurfaceElevated,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Playing Next",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunyaTextPrimary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(state.queue) { idx, qSong ->
                        val isCurrent = idx == state.queueIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) DhunyaSurfaceHigh else Color.Transparent)
                                .clickable {
                                    viewModel.playQueueIndex(idx)
                                    showQueueSheet = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DhunyaArtwork(
                                url = qSong.artworkUrl,
                                contentDescription = qSong.title,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = qSong.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) DhunyaAccent else DhunyaTextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = qSong.artistName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DhunyaTextSecondary,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { viewModel.removeFromQueue(idx) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from queue",
                                    tint = DhunyaTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
