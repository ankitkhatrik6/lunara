package com.lunara.app.features.player

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
import com.lunara.app.core.extensions.formatDurationMs
import com.lunara.app.core.state.SongStates
import com.lunara.app.domain.model.DownloadStatus
import com.lunara.app.domain.model.RepeatMode
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.DownloadRepository
import com.lunara.app.domain.repository.LibraryRepository
import com.lunara.app.features.share.SharePosterEffect
import com.lunara.app.features.share.ShareSongViewModel
import com.lunara.app.player.PlaybackState
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.AnimatedDownloadIcon
import com.lunara.app.ui.components.AnimatedFavoriteButton
import com.lunara.app.ui.components.LunaraArtwork
import com.lunara.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val songStates: SongStates
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    /** Ids of every loved track; drives the animated heart. */
    val favoriteIds: StateFlow<Set<String>> = songStates.favoriteIds

    /** Download status per song id, so the button can show progress and completion. */
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = songStates.downloadStatus

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun playNext() = playerManager.playNext()
    fun playPrevious() = playerManager.playPrevious()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun toggleShuffle() = playerManager.queueManager.toggleShuffle()
    fun cycleRepeatMode() = playerManager.queueManager.cycleRepeatMode()

    fun toggleFavorite(song: Song) = songStates.toggleFavorite(song)

    fun download(song: Song) = songStates.download(song)

    fun removeDownload(songId: String) = songStates.removeDownload(songId)

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
    viewModel: PlayerViewModel = hiltViewModel(),
    shareViewModel: ShareSongViewModel = hiltViewModel()
) {
    val state by viewModel.playbackState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val downloadStates by viewModel.downloadStatus.collectAsState()
    val isPreparingShare by shareViewModel.isPreparingShare.collectAsState()
    val song = state.currentSong

    // Renders the Instagram style poster in the background and opens the share sheet.
    SharePosterEffect(viewModel = shareViewModel)

    var showQueueSheet by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.currentPositionMs) {
        if (!isDraggingSlider && state.totalDurationMs > 0) {
            sliderPosition = state.currentPositionMs.toFloat()
        }
    }

    Scaffold(
        containerColor = LunaraBackground,
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
                        tint = LunaraTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = LunaraTextSecondary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { shareViewModel.share(song) },
                        enabled = song != null && !isPreparingShare
                    ) {
                        if (isPreparingShare) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = LunaraAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share this song",
                                tint = LunaraTextPrimary
                            )
                        }
                    }

                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Playing queue",
                            tint = LunaraTextPrimary
                        )
                    }
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
                    color = LunaraTextSecondary
                )
            } else {
                // Dominant Artwork
                LunaraArtwork(
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
                            color = LunaraTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artistName,
                            style = MaterialTheme.typography.titleMedium,
                            color = LunaraTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    AnimatedFavoriteButton(
                        isFavorite = song.id in favoriteIds,
                        onToggle = { viewModel.toggleFavorite(song) },
                        iconSize = 28.dp
                    )
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
                            thumbColor = LunaraAccent,
                            activeTrackColor = LunaraAccent,
                            inactiveTrackColor = LunaraSurfaceElevated
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
                            color = LunaraTextSecondary
                        )
                        Text(
                            text = state.totalDurationMs.formatDurationMs(),
                            style = MaterialTheme.typography.labelSmall,
                            color = LunaraTextSecondary
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
                            tint = if (state.shuffleEnabled) LunaraAccent else LunaraTextSecondary
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
                            tint = LunaraTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play/Pause Center Action
                    Surface(
                        shape = CircleShape,
                        color = LunaraAccent,
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
                                    color = LunaraBackground,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = LunaraBackground,
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
                            tint = LunaraTextPrimary,
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
                            tint = if (state.repeatMode != RepeatMode.OFF) LunaraAccent else LunaraTextSecondary
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
                            tint = LunaraAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lyrics", color = LunaraAccent)
                    }

                    // Download toggle: morphs into a spinner while the file is fetched and
                    // into a tick once it is stored on the device.
                    val downloadStatus = downloadStates[song.id]
                    val isDownloaded =
                        downloadStatus == DownloadStatus.COMPLETED || song.isDownloaded
                    IconButton(
                        onClick = {
                            if (isDownloaded) viewModel.removeDownload(song.id)
                            else viewModel.download(song)
                        }
                    ) {
                        AnimatedDownloadIcon(
                            status = downloadStatus,
                            isDownloaded = isDownloaded,
                            iconSize = 24.dp
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
            containerColor = LunaraSurfaceElevated,
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
                    color = LunaraTextPrimary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(state.queue) { idx, qSong ->
                        val isCurrent = idx == state.queueIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) LunaraSurfaceHigh else Color.Transparent)
                                .clickable {
                                    viewModel.playQueueIndex(idx)
                                    showQueueSheet = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LunaraArtwork(
                                url = qSong.artworkUrl,
                                contentDescription = qSong.title,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = qSong.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) LunaraAccent else LunaraTextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = qSong.artistName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LunaraTextSecondary,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { viewModel.removeFromQueue(idx) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from queue",
                                    tint = LunaraTextMuted,
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
