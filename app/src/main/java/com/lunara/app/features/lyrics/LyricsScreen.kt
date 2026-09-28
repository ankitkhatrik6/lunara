package com.lunara.app.features.lyrics

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.app.core.result.Resource
import com.lunara.app.domain.model.Lyrics
import com.lunara.app.domain.usecase.GetLyricsUseCase
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.EmptyStateView
import com.lunara.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsUiState(
    val lyrics: Lyrics? = null,
    val isLoading: Boolean = false,
    val currentLineIndex: Int = -1,
    val errorMessage: String? = null
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val getLyricsUseCase: GetLyricsUseCase,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    val playbackState = playerManager.playbackState

    init {
        observePlaybackForLyrics()
    }

    private fun observePlaybackForLyrics() {
        var lastSongId: String? = null

        viewModelScope.launch {
            playbackState.collect { state ->
                val currentSong = state.currentSong
                if (currentSong != null && currentSong.id != lastSongId) {
                    lastSongId = currentSong.id
                    loadLyricsForSong(currentSong.title, currentSong.artistName, currentSong.albumName, (currentSong.durationMs / 1000).toInt())
                }

                // Calculate current synchronized lyric line index
                val lyrics = _uiState.value.lyrics
                if (lyrics != null && lyrics.isSynced && lyrics.syncedLyrics.isNotEmpty()) {
                    val pos = state.currentPositionMs
                    val idx = lyrics.syncedLyrics.indexOfLast { it.timestampMs <= pos }
                    if (idx != _uiState.value.currentLineIndex) {
                        _uiState.update { it.copy(currentLineIndex = idx) }
                    }
                }
            }
        }
    }

    fun loadLyricsForSong(title: String, artist: String, album: String? = null, durationSec: Int? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = getLyricsUseCase(title, artist, album, durationSec)
            when (res) {
                is Resource.Success -> {
                    _uiState.update { it.copy(lyrics = res.data, isLoading = false) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(lyrics = null, isLoading = false, errorMessage = res.message) }
                }
                else -> Unit
            }
        }
    }

    fun seekToLyric(timestampMs: Long) {
        playerManager.seekTo(timestampMs)
    }
}

@Composable
fun LyricsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LyricsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val listState = rememberLazyListState()

    // Smoothly follow current lyric line as playback advances
    LaunchedEffect(uiState.currentLineIndex) {
        if (uiState.currentLineIndex >= 0) {
            val targetScroll = (uiState.currentLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Scaffold(
        containerColor = LunaraBackground,
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
                        tint = LunaraTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playbackState.currentSong?.title ?: "Lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        color = LunaraTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = playbackState.currentSong?.artistName ?: "LRCLIB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LunaraTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LunaraAccent)
                }
            } else if (uiState.lyrics == null) {
                EmptyStateView(
                    icon = Icons.Default.Lyrics,
                    title = "No lyrics found",
                    subtitle = "Lyrics are not available for this track yet."
                )
            } else {
                val lyrics = uiState.lyrics!!

                if (lyrics.isSynced) {
                    // Synchronized dynamic lyrics
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(vertical = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        itemsIndexed(lyrics.syncedLyrics) { index, line ->
                            val isCurrent = index == uiState.currentLineIndex
                            val isPast = index < uiState.currentLineIndex

                            Text(
                                text = line.text.ifBlank { "♪" },
                                fontSize = if (isCurrent) 26.sp else 20.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) LunaraAccent else LunaraTextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (isCurrent) 1.0f else if (isPast) 0.4f else 0.65f)
                                    .clickable { viewModel.seekToLyric(line.timestampMs) }
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                    // Plain static lyrics
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(vertical = 40.dp)
                    ) {
                        item {
                            Text(
                                text = lyrics.plainLyrics,
                                style = MaterialTheme.typography.bodyLarge,
                                color = LunaraTextPrimary,
                                lineHeight = 32.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
