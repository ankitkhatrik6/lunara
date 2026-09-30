package com.lunara.app.features.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.app.R
import com.lunara.app.core.result.Resource
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.LibraryRepository
import com.lunara.app.domain.repository.MusicRepository
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.LunaraArtwork
import com.lunara.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** One live section of the home feed. */
data class HomeRail(
    val id: String,
    val title: String,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Query behind a home rail.
 *
 * The feed is fetched from YouTube Music on every launch, so it reflects what is actually
 * popular now. Nothing in it is bundled with the app.
 */
private data class RailSpec(val id: String, val title: String, val query: String)

private val HOME_RAILS = listOf(
    RailSpec("trending", "Trending now", "trending songs"),
    RailSpec("quick_picks", "Quick picks", "top hits playlist"),
    RailSpec("new_releases", "New releases", "new music releases"),
    RailSpec("popular", "Popular right now", "popular music hits")
)

private const val MOOD_RAIL_ID = "mood"

private val DEFAULT_MOODS = listOf(
    "Chill", "Focus", "Party", "Workout", "Sleep", "Romantic", "Study", "Drive"
)

data class HomeUiState(
    val greeting: String = "Welcome",
    val recentlyPlayed: List<Song> = emptyList(),
    val rails: List<HomeRail> = HOME_RAILS.map { HomeRail(id = it.id, title = it.title) },
    val moods: List<String> = DEFAULT_MOODS,
    val selectedMood: String? = null,
    val moodRail: HomeRail? = null
) {
    /** True while every rail is still empty and none has finished loading. */
    val isInitialLoad: Boolean
        get() = rails.isNotEmpty() && rails.all { it.isLoading && it.songs.isEmpty() }

    /**
     * True when the catalogue answered nothing and nothing is pending: offline, rate limited or
     * otherwise unreachable. The screen offers a retry instead of silently showing empty space.
     */
    val isFeedEmpty: Boolean
        get() = rails.isNotEmpty() && rails.none { it.isLoading } && rails.all { it.songs.isEmpty() }
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
        observeHistory()
        refresh()
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

    /**
     * History is collected as its own flow so a slow network feed can never hold back the
     * "Recently played" row.
     */
    private fun observeHistory() {
        viewModelScope.launch {
            libraryRepository.getHistory().collect { history ->
                // `distinctBy` is belt-and-braces: the query is unique per song now, but a
                // duplicate id here would crash the row with a repeated key.
                _uiState.update { state ->
                    state.copy(recentlyPlayed = history.distinctBy { song -> song.id }.take(8))
                }
            }
        }
    }

    /** Reloads every rail. Each resolves on its own, so the feed fills in progressively. */
    fun refresh() {
        _uiState.update { state ->
            state.copy(rails = HOME_RAILS.map { HomeRail(id = it.id, title = it.title) })
        }

        HOME_RAILS.forEach { spec ->
            viewModelScope.launch {
                val songs = fetchSongs(spec.query)
                _uiState.update { state ->
                    state.copy(
                        rails = state.rails.map { rail ->
                            if (rail.id == spec.id) {
                                rail.copy(songs = songs, isLoading = false)
                            } else {
                                rail
                            }
                        }
                    )
                }
            }
        }

        // Moods default to the first chip so that section is never empty on a fresh launch.
        selectMood(_uiState.value.selectedMood ?: DEFAULT_MOODS.first())
    }

    /** Loads the songs of one mood. Switching moods mid-load cannot apply a stale answer. */
    fun selectMood(mood: String) {
        val title = "$mood picks"
        _uiState.update { state ->
            state.copy(
                selectedMood = mood,
                moodRail = HomeRail(id = MOOD_RAIL_ID, title = title)
            )
        }
        viewModelScope.launch {
            val songs = fetchSongs("$mood music")
            _uiState.update { state ->
                if (state.selectedMood != mood) {
                    // The user picked another mood while this one was loading.
                    state
                } else {
                    state.copy(
                        moodRail = HomeRail(
                            id = MOOD_RAIL_ID,
                            title = title,
                            songs = songs,
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun fetchSongs(query: String): List<Song> =
        when (val result = musicRepository.searchSongs(query)) {
            is Resource.Success -> result.data.distinctBy { song -> song.id }
            else -> emptyList()
        }

    /**
     * Starts [song] inside the list it was tapped from.
     *
     * The previous version always used one hard-coded album as the queue, no matter which
     * section was tapped: tapping a recently played track that is not on that album made
     * `indexOfFirst` return -1, `.coerceAtLeast(0)` turned that into 0, and playback started at
     * the album opener — the reason every recent song played the same track.
     */
    fun playSong(song: Song, queue: List<Song>) {
        viewModelScope.launch {
            libraryRepository.addToHistory(song)
            val playable = queue.ifEmpty { listOf(song) }
            val index = playable.indexOfFirst { it.id == song.id }
            if (index >= 0) {
                playerManager.playQueue(playable, index)
            } else {
                // Song is not part of that section any more (e.g. history was trimmed):
                // play the track itself rather than jumping to an unrelated list entry.
                playerManager.playQueue(listOf(song), 0)
            }
        }
    }

    fun playRecent(song: Song) = playSong(song, _uiState.value.recentlyPlayed)

    /** Plays [song] (or the first track) inside [rail]. */
    fun playRail(rail: HomeRail, song: Song? = null) {
        val queue = rail.songs
        if (queue.isEmpty()) return
        playSong(song ?: queue.first(), queue)
    }
}

@Composable
fun HomeScreen(
    onNavigateToFavorites: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSongActionClick: (Song) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val playingId = playbackState.currentSong?.id?.takeIf { playbackState.isPlaying }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LunaraBackground),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item { BrandHeader(onNavigateToSettings = onNavigateToSettings) }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = uiState.greeting,
                    style = MaterialTheme.typography.displaySmall,
                    color = LunaraTextPrimary
                )
                Text(
                    text = "Welcome to ${stringResource(R.string.app_name)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LunaraTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                SearchPill(onClick = onNavigateToSearch)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
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
        }

        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                RailSection(
                    title = "Recently played",
                    songs = uiState.recentlyPlayed,
                    playingId = playingId,
                    onSongClick = { song -> viewModel.playRecent(song) },
                    onSongActionClick = onSongActionClick
                )
            }
        }

        if (uiState.isFeedEmpty) {
            item { EmptyFeedCard(onRetry = viewModel::refresh) }
        }

        items(uiState.rails, key = { rail -> rail.id }) { rail ->
            if (rail.isLoading || rail.songs.isNotEmpty()) {
                RailSection(
                    title = rail.title,
                    songs = rail.songs,
                    isLoading = rail.isLoading,
                    playingId = playingId,
                    onSongClick = { song -> viewModel.playRail(rail, song) },
                    onSongActionClick = onSongActionClick
                )
            }
        }

        item {
            MoodChips(
                moods = uiState.moods,
                selected = uiState.selectedMood,
                onSelect = viewModel::selectMood
            )
        }

        uiState.moodRail?.let { moodRail ->
            item {
                RailSection(
                    title = moodRail.title,
                    songs = moodRail.songs,
                    isLoading = moodRail.isLoading,
                    playingId = playingId,
                    onPlayAll = { viewModel.playRail(moodRail) },
                    onSongClick = { song -> viewModel.playRail(moodRail, song) },
                    onSongActionClick = onSongActionClick
                )
            }
        }
    }
}

/**
 * Brand bar: the Lunara mark and wordmark centred, settings on the right.
 *
 * The logo is the same PNG the launcher and splash use, so the app keeps one identity from the
 * first frame onwards.
 */
@Composable
private fun BrandHeader(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val logo = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.lunara_logo)?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = LunaraTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = LunaraTextPrimary
            )
        }
    }
}

/** Search entry point. Tapping it opens the search screen, keyboard ready. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        color = LunaraSurfaceElevated,
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = LunaraTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Search songs, artists and albums",
                style = MaterialTheme.typography.bodyLarge,
                color = LunaraTextMuted
            )
        }
    }
}

/**
 * One horizontal feed rail. Skeleton cards are shown while the rails are still loading, so the
 * feed never collapses to nothing on a slow connection.
 */
@Composable
private fun RailSection(
    title: String,
    songs: List<Song>,
    isLoading: Boolean = false,
    playingId: String? = null,
    onPlayAll: (() -> Unit)? = null,
    onSongClick: (Song) -> Unit,
    onSongActionClick: (Song) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = LunaraTextPrimary
            )
            if (onPlayAll != null && songs.isNotEmpty()) {
                FilledTonalButton(onClick = onPlayAll) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading && songs.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(4) { RailSkeletonCard() }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(songs, key = { song -> "${title}_${song.id}" }) { song ->
                    RailSongCard(
                        song = song,
                        isPlaying = song.id == playingId,
                        onClick = { onSongClick(song) },
                        onMoreClick = { onSongActionClick(song) }
                    )
                }
            }
        }
    }
}

/** Card used by every song rail: artwork, title, artist and an overflow menu. */
@Composable
private fun RailSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            LunaraArtwork(
                url = song.artworkUrl,
                contentDescription = song.title,
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LunaraBackground.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = LunaraAccent,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = LunaraTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            color = LunaraTextPrimary,
            maxLines = 1
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = LunaraTextSecondary,
            maxLines = 1
        )
    }
}

/** Placeholder shown while a rail is loading. */
@Composable
private fun RailSkeletonCard() {
    Column(modifier = Modifier.width(130.dp)) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LunaraSurfaceElevated)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(LunaraSurfaceElevated)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(LunaraSurfaceElevated)
        )
    }
}

/** Mood / genre chooser; the rail below it follows the selection. */
@Composable
private fun MoodChips(
    moods: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = "Moods and genres",
            style = MaterialTheme.typography.titleLarge,
            color = LunaraTextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(moods, key = { mood -> mood }) { mood ->
                FilterChip(
                    selected = mood == selected,
                    onClick = { onSelect(mood) },
                    label = { Text(mood) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = LunaraSurfaceElevated,
                        labelColor = LunaraTextSecondary,
                        selectedContainerColor = LunaraAccent,
                        selectedLabelColor = LunaraBackground
                    )
                )
            }
        }
    }
}

/**
 * Shown when every rail came back empty (offline, blocked or rate limited) so the screen explains
 * itself instead of showing a blank feed.
 */
@Composable
private fun EmptyFeedCard(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = LunaraSurface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nothing loaded",
                style = MaterialTheme.typography.titleMedium,
                color = LunaraTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Lunara could not reach YouTube Music. Check your connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = LunaraTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LunaraAccent,
                    contentColor = LunaraBackground
                )
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LibraryShortcutCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
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





