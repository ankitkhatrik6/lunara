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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.lunara.app.core.utils.NetworkMonitor
import com.lunara.app.domain.model.BrowseCard
import com.lunara.app.domain.model.BrowseCategory
import com.lunara.app.domain.model.BrowseShelf
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.repository.DownloadRepository
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

/**
 * One live section of the home feed, exactly as YouTube Music named and filled it.
 *
 * A section is either a row of [songs] that plays as it stands, or a row of [cards] that opens a
 * playlist, an album or an artist. Both come from the service, heading included.
 */
data class HomeRail(
    val id: String,
    val title: String,
    val songs: List<Song> = emptyList(),
    val cards: List<BrowseCard> = emptyList(),
    val isLoading: Boolean = true
) {
    /** `true` when there is nothing to draw yet - an empty rail would be a heading over a gap. */
    val isBlank: Boolean
        get() = songs.isEmpty() && cards.isEmpty()
}

private const val MOOD_RAIL_ID = "mood"

/** Rail ids for the offline feed, which is built from local data. */
private const val OFFLINE_DOWNLOADS_ID = "offline_downloads"
private const val OFFLINE_DEVICE_ID = "offline_device"

/** Feed sections are keyed by their position on the page, which is what YouTube's order gives them. */
private fun shelfRailId(index: Int): String = "shelf_$index"

/**
 * A feed section as a rail.
 *
 * A shelf the service sent empty is dropped, and so is one that only holds the moods and genres
 * chooser - those rows become the chips above the mood rail rather than a rail of their own.
 * Either way, a heading over nothing would draw a gap in the feed.
 */
private fun BrowseShelf.toRail(index: Int): HomeRail? = when {
    songs.isEmpty() && cards.isEmpty() -> null
    songs.isNotEmpty() -> HomeRail(
        id = shelfRailId(index),
        title = title,
        songs = songs,
        isLoading = false
    )
    else -> HomeRail(
        id = shelfRailId(index),
        title = title,
        cards = cards,
        isLoading = false
    )
}

data class HomeUiState(
    val greeting: String = "Welcome",
    /** The line under the greeting; it follows the time of day exactly like the greeting does. */
    val greetingMessage: String = "Let's find something good to play.",
    val recentlyPlayed: List<Song> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
    /** YouTube Music's own sections, in the order the service draws them. */
    val rails: List<HomeRail> = emptyList(),
    /** The service's moods and genres chooser, kept with the params that pick each one. */
    val moods: List<BrowseCategory> = emptyList(),
    val selectedMood: String? = null,
    val moodRail: HomeRail? = null,
    /** `true` while the feed is still on its way from YouTube Music. */
    val isLoading: Boolean = true,
    /** True when the device has no usable connection; the feed then shows local music only. */
    val isOffline: Boolean = false
) {
    /**
     * True when the catalogue answered nothing and nothing is pending: offline, rate limited or
     * otherwise unreachable. The screen offers a retry instead of silently showing empty space.
     */
    val isFeedEmpty: Boolean
        get() = !isLoading && rails.none { it.isLoading } && rails.all { it.isBlank }

    /**
     * Offline with nothing saved either: no downloads, no music on the device, no history. That is
     * the moment to tell the user how to fill the feed for next time.
     */
    val isOfflineAndEmpty: Boolean
        get() = isOffline && rails.isEmpty() && recentlyPlayed.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val playerManager: PlayerManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val playbackState = playerManager.playbackState

    init {
        determineGreeting()
        observeHistory()
        observeFavorites()
        observeConnectivity()
    }

    /**
     * The feed follows the connection.
     *
     * Online it is YouTube Music's rails; offline it becomes what can actually play - the
     * downloads and the music already on the device - instead of a spinner that can never finish.
     * `isOnline` emits the current state as soon as it is collected, so the first load happens
     * here and there is no separate initial `refresh()`.
     */
    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val wasOffline = _uiState.value.isOffline
                _uiState.update { state -> state.copy(isOffline = !online) }
                when {
                    !online -> if (!wasOffline) loadOfflineRails()
                    else -> refresh()
                }
            }
        }
    }

    /** Downloads plus the device's own audio: everything that plays with no connection at all. */
    private fun loadOfflineRails() {
        viewModelScope.launch {
            val downloads = runCatching {
                downloadRepository.getDownloads().first()
                    .filter { item -> item.localFilePath != null }
                    .map { item -> item.song }
                    .distinctBy { song -> song.id }
            }.getOrDefault(emptyList())

            val onDevice = runCatching {
                musicRepository.getLocalSongs().distinctBy { song -> song.id }
            }.getOrDefault(emptyList())

            _uiState.update { state ->
                state.copy(
                    rails = listOfNotNull(
                        downloads.takeIf { it.isNotEmpty() }?.let { songs ->
                            HomeRail(
                                id = OFFLINE_DOWNLOADS_ID,
                                title = "Your downloads",
                                songs = songs,
                                isLoading = false
                            )
                        },
                        onDevice.takeIf { it.isNotEmpty() }?.let { songs ->
                            HomeRail(
                                id = OFFLINE_DEVICE_ID,
                                title = "On this device",
                                songs = songs,
                                isLoading = false
                            )
                        }
                    ),
                    selectedMood = null,
                    moodRail = null,
                    isLoading = false
                )
            }
        }
    }

    private fun determineGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (greeting, message) = when (hour) {
            in 5..11 -> "Good morning" to "Ease into the day with something good."
            in 12..16 -> "Good afternoon" to "A little music for the afternoon?"
            else -> "Good evening" to "Winding down? Put something on."
        }
        _uiState.update { it.copy(greeting = greeting, greetingMessage = message) }
    }

    /** History is collected as the personalization signal for the Jump back in rail. */
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

    private fun observeFavorites() {
        viewModelScope.launch {
            libraryRepository.getFavorites().collect { favorites ->
                _uiState.update { it.copy(favoriteSongs = favorites.distinctBy { song -> song.id }) }
            }
        }
    }

    /**
     * Reloads every rail. Each resolves on its own, so the feed fills in progressively.
     *
     * While offline the network rails are pointless, so the local feed is rebuilt instead.
     */
    fun refresh() {
        if (_uiState.value.isOffline) {
            loadOfflineRails()
            return
        }
        _uiState.update { state -> state.copy(isLoading = true) }

        viewModelScope.launch {
            val history = libraryRepository.getHistory().first().distinctBy { it.id }.take(8)
            val favorites = libraryRepository.getFavorites().first().distinctBy { it.id }
            _uiState.update { it.copy(recentlyPlayed = history, favoriteSongs = favorites) }
            val shelves = (musicRepository.getHomeShelves() as? Resource.Success)?.data.orEmpty()
            val personalizedRails = buildPersonalizedRails()
            _uiState.update { state ->
                state.copy(
                    rails = personalizedRails + shelves.mapIndexedNotNull { index, shelf -> shelf.toRail(index) },
                    // The chooser is not a rail to scroll: it is the row of chips, and its rows
                    // carry the params that pick each mood out of the page they all share.
                    moods = shelves.flatMap { shelf -> shelf.categories }
                        .distinctBy { category -> category.title },
                    isLoading = false
                )
            }
        }
    }

    /**
     * Uses the listener's own signals before falling back to catalogue discovery. History drives
     * recency, favourites reinforce taste, and YouTube Music's radio supplies the actual artist
     * recommendation rather than a locally invented genre match.
     */
    private suspend fun buildPersonalizedRails(): List<HomeRail> {
        val state = _uiState.value
        val liked = (state.favoriteSongs + state.recentlyPlayed).distinctBy { it.id }
        if (liked.isEmpty()) return emptyList()

        val rails = mutableListOf<HomeRail>()
        if (state.recentlyPlayed.isNotEmpty()) {
            rails += HomeRail(
                id = "personal_jump_back",
                title = "Jump back in",
                songs = state.recentlyPlayed.take(10),
                isLoading = false
            )
        }

        val albumSongs = liked
            .filter { !it.albumName.isNullOrBlank() }
            .groupBy { it.albumName }
            .maxByOrNull { (_, songs) -> songs.size }
            ?.value
            .orEmpty()
        if (albumSongs.isNotEmpty()) {
            rails += HomeRail(
                id = "personal_album",
                title = "Album featuring songs you like",
                songs = albumSongs.take(10),
                isLoading = false
            )
        }

        val topArtist = liked.groupingBy { it.artistName }.eachCount().maxByOrNull { it.value }?.key
        val artistSeed = liked.firstOrNull { it.artistName == topArtist }
        if (artistSeed != null && !topArtist.isNullOrBlank()) {
            val radio = (musicRepository.getUpNext(artistSeed) as? Resource.Success)
                ?.data?.songs
                ?.filterNot { candidate -> liked.any { it.id == candidate.id } }
                ?.distinctBy { it.id }
                .orEmpty()
            if (radio.isNotEmpty()) {
                rails += HomeRail(
                    id = "personal_artist_${topArtist.lowercase()}",
                    title = "Because you like $topArtist",
                    songs = radio.take(10),
                    isLoading = false
                )
            }
        }
        return rails
    }

    /**
     * Loads the shelves behind one mood or genre.
     *
     * The chip carries the params that pick it out of the shared page, so what comes back is the
     * service's own selection for it - not a search for the word on the chip.
     *
     * Switching moods mid-load cannot apply a stale answer.
     */
    fun selectMood(category: BrowseCategory) {
        // The chooser is part of the online feed; ignore stray taps while offline.
        if (_uiState.value.isOffline) return
        val title = category.title
        _uiState.update { state ->
            state.copy(
                selectedMood = title,
                moodRail = HomeRail(id = MOOD_RAIL_ID, title = title)
            )
        }
        viewModelScope.launch {
            val shelves = (
                musicRepository.getBrowseShelves(category.browseId, category.params)
                    as? Resource.Success
                )?.data.orEmpty()
            _uiState.update { state ->
                if (state.selectedMood != title) {
                    // The user picked another mood while this one was loading.
                    state
                } else {
                    state.copy(
                        moodRail = HomeRail(
                            id = MOOD_RAIL_ID,
                            title = title,
                            songs = shelves.flatMap { it.songs }.distinctBy { it.id },
                            cards = shelves.flatMap { it.cards }.distinctBy { it.browseId },
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    /**
     * Opens a tile of the feed: a playlist, an album or an artist.
     *
     * Its browse id is the whole address of the page, so the page is simply read and its songs
     * become the queue. An album or playlist lists them straight away; an artist page answers with
     * shelves instead, and its most-played songs are what starts.
     */
    fun openCard(card: BrowseCard) {
        viewModelScope.launch {
            val songs = songsOfPage(card.browseId)
            if (songs.isNotEmpty()) playerManager.playQueue(songs, 0)
        }
    }

    private suspend fun songsOfPage(browseId: String): List<Song> {
        val fromShelves = (musicRepository.getBrowseShelves(browseId) as? Resource.Success)
            ?.data?.flatMap { shelf -> shelf.songs }?.distinctBy { song -> song.id }.orEmpty()
        if (fromShelves.isNotEmpty()) return fromShelves
        return (musicRepository.getAlbumTracks(browseId) as? Resource.Success)?.data.orEmpty()
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

    /** Plays [song] (or the first track) inside [rail]. */
    fun playRail(rail: HomeRail, song: Song? = null) {
        val queue = rail.songs
        if (queue.isEmpty()) return
        playSong(song ?: queue.first(), queue)
    }
}

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onSongActionClick: (Song) -> Unit,
    onBrowseCardClick: (BrowseCard) -> Unit,
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

        if (uiState.isOffline) {
            item { OfflineBanner() }
        }

        item {
            GreetingCard(
                greeting = uiState.greeting,
                message = uiState.greetingMessage
            )
        }

        if (uiState.isOfflineAndEmpty) {
            item { EmptyFeedCard(offline = true, onRetry = viewModel::refresh) }
        } else if (uiState.isFeedEmpty) {
            item { EmptyFeedCard(offline = false, onRetry = viewModel::refresh) }
        }

        // The feed arrives in one response, so the first moment is a single row of placeholders
        // rather than a page of empty headings.
        if (uiState.isLoading && uiState.rails.isEmpty()) {
            item { FeedSkeleton() }
        }

        items(uiState.rails, key = { rail -> rail.id }) { rail ->
            when {
                rail.songs.isNotEmpty() -> RailSection(
                    title = rail.title,
                    songs = rail.songs,
                    playingId = playingId,
                    onSongClick = { song -> viewModel.playRail(rail, song) },
                    onSongActionClick = onSongActionClick
                )
                // A shelf of tiles: a playlist, an album, an artist. Tapping one opens that page.
                rail.cards.isNotEmpty() -> CardRailSection(
                    title = rail.title,
                    cards = rail.cards,
                    onCardClick = onBrowseCardClick
                )
            }
        }

        // The chooser itself comes from YouTube Music, so it belongs to the online feed only.
        if (!uiState.isOffline && uiState.moods.isNotEmpty()) {
            item {
                MoodChips(
                    moods = uiState.moods,
                    selected = uiState.selectedMood,
                    onSelect = viewModel::selectMood
                )
            }
        }

        if (!uiState.isOffline) {
            uiState.moodRail?.let { moodRail ->
                item {
                    when {
                        moodRail.cards.isNotEmpty() -> CardRailSection(
                            title = moodRail.title,
                            cards = moodRail.cards,
                            onCardClick = onBrowseCardClick
                        )
                        moodRail.songs.isNotEmpty() -> RailSection(
                            title = moodRail.title,
                            songs = moodRail.songs,
                            playingId = playingId,
                            onPlayAll = { viewModel.playRail(moodRail) },
                            onSongClick = { song -> viewModel.playRail(moodRail, song) },
                            onSongActionClick = onSongActionClick
                        )
                        moodRail.isLoading -> RailSection(
                            title = moodRail.title,
                            songs = emptyList(),
                            isLoading = true,
                            playingId = playingId,
                            onSongClick = {},
                            onSongActionClick = onSongActionClick
                        )
                    }
                }
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

/**
 * The hello at the top of the feed.
 *
 * Opening straight into a wall of rails reads like a database, so Home starts with a note to the
 * listener instead: the time of day, a line to match it, and the Lunara note drawn in the accent
 * colour. The soft wash behind the card and the rounded badge are what make it feel like a card
 * rather than another header - and every colour is read from the theme, so the whole thing follows
 * the wallpaper when Material You is on.
 */
@Composable
private fun GreetingCard(greeting: String, message: String) {
    val accent = LunaraAccent
    val sparkle = LunaraAccentSecondary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = LunaraSurfaceElevated,
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent)
                )
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LunaraTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = sparkle,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LunaraTextSecondary
                    )
                }
            }
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
        song.albumName?.takeIf { it.isNotBlank() }?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.labelSmall,
                color = LunaraTextMuted,
                maxLines = 1
            )
        }
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

/** The feed's first moment: one row of placeholders while the shelves are on their way. */
@Composable
private fun FeedSkeleton() {
    LazyRow(
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(4) { RailSkeletonCard() }
    }
}

/**
 * A shelf of tiles - a playlist, an album or an artist - each of which opens its own page.
 *
 * The byline under a tile is the service's own ("Single • John Rai", "2.27M subscribers"), so it
 * says what the tile is without the app having to guess at a label.
 */
@Composable
private fun CardRailSection(
    title: String,
    cards: List<BrowseCard>,
    onCardClick: (BrowseCard) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = LunaraTextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(cards, key = { card -> "${title}_${card.browseId}" }) { card ->
                BrowseCardTile(card = card, onClick = { onCardClick(card) })
            }
        }
    }
}

/** One tile of a [CardRailSection]: its artwork, its title and the byline YouTube gave it. */
@Composable
private fun BrowseCardTile(card: BrowseCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        LunaraArtwork(
            url = card.artworkUrl,
            contentDescription = card.title,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            color = LunaraTextPrimary,
            maxLines = 1
        )
        card.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LunaraTextSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * The moods and genres chooser, read from the feed.
 *
 * Every chip is a row of YouTube Music's own chooser, and tapping one browses the page behind it
 * with the params that pick that mood out.
 */
@Composable
private fun MoodChips(
    moods: List<BrowseCategory>,
    selected: String?,
    onSelect: (BrowseCategory) -> Unit
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
            items(moods, key = { category -> category.title }) { category ->
                FilterChip(
                    selected = category.title == selected,
                    onClick = { onSelect(category) },
                    label = { Text(category.title) },
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
 * Shown when the feed cannot be filled.
 *
 * Offline is not an error: it is the moment the saved music matters, so the card says that warmly
 * and points at the one thing that helps - downloading. A retry button would only ever fail.
 */
@Composable
private fun EmptyFeedCard(offline: Boolean, onRetry: () -> Unit) {
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
                text = if (offline) "Nothing saved yet" else "Nothing loaded",
                style = MaterialTheme.typography.titleMedium,
                color = LunaraTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (offline) {
                    "You're offline, and no music lives on this device yet. " +
                        "Download a few tracks while you have signal and they'll be waiting here."
                } else {
                    "Lunara could not reach YouTube Music. Check your connection and try again."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LunaraTextSecondary,
                textAlign = TextAlign.Center
            )
            if (!offline) {
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
}

/**
 * The offline hello.
 *
 * Losing signal is common and a little annoying; this card makes it feel handled rather than
 * broken, and it names the music that still plays so the user knows where to tap next.
 */
@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = LunaraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = LunaraAccent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "You're offline",
                    style = MaterialTheme.typography.titleMedium,
                    color = LunaraTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "No signal, no problem - here is the music you saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LunaraTextSecondary
                )
            }
        }
    }
}
