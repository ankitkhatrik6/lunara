package com.dhunya.app.features.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
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
import com.dhunya.app.core.constants.AppConstants
import com.dhunya.app.core.result.Resource
import com.dhunya.app.domain.model.Album
import com.dhunya.app.domain.model.Artist
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.repository.LibraryRepository
import com.dhunya.app.domain.repository.MusicRepository
import com.dhunya.app.domain.usecase.SearchResult
import com.dhunya.app.player.PlayerManager
import com.dhunya.app.ui.components.DhunyaArtwork
import com.dhunya.app.ui.components.EmptyStateView
import com.dhunya.app.ui.components.SongListItem
import com.dhunya.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val searchResult: SearchResult = SearchResult(),
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    val playbackState = playerManager.playbackState

    init {
        loadRecentSearches()
        setupDebouncedSearch()
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            val recents = musicRepository.getRecentSearches()
            _uiState.update { it.copy(recentSearches = recents) }
        }
    }

    private fun setupDebouncedSearch() {
        viewModelScope.launch {
            queryFlow
                .debounce(AppConstants.SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(searchResult = SearchResult(), isLoading = false) }
                    } else {
                        performSearch(query)
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        queryFlow.value = newQuery
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            musicRepository.saveRecentSearch(query)
            
            val songsRes = musicRepository.searchSongs(query)
            val artistsRes = musicRepository.searchArtists(query)
            val albumsRes = musicRepository.searchAlbums(query)

            val songs = if (songsRes is Resource.Success) songsRes.data else emptyList()
            val artists = if (artistsRes is Resource.Success) artistsRes.data else emptyList()
            val albums = if (albumsRes is Resource.Success) albumsRes.data else emptyList()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    searchResult = SearchResult(songs, artists, albums)
                )
            }
            loadRecentSearches()
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            musicRepository.clearRecentSearches()
            _uiState.update { it.copy(recentSearches = emptyList()) }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            libraryRepository.addToHistory(song)
            val songs = _uiState.value.searchResult.songs.ifEmpty { listOf(song) }
            val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            playerManager.playQueue(songs, index)
        }
    }
}

@Composable
fun SearchScreen(
    onSongActionClick: (Song) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DhunyaBackground)
    ) {
        // Search Input Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = DhunyaSurfaceElevated,
            shape = RoundedCornerShape(14.dp)
        ) {
            TextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        "Search songs, artists, albums...",
                        color = DhunyaTextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = DhunyaAccent
                    )
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearQuery) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = DhunyaTextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = DhunyaAccent,
                    focusedTextColor = DhunyaTextPrimary,
                    unfocusedTextColor = DhunyaTextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Search Content or Recents
        if (uiState.query.isBlank()) {
            // Recent searches view
            if (uiState.recentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleMedium,
                        color = DhunyaTextPrimary
                    )
                    TextButton(onClick = viewModel::clearRecentSearches) {
                        Text("Clear", color = DhunyaAccent)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(uiState.recentSearches) { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onQueryChange(recent) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = DhunyaTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = recent,
                                style = MaterialTheme.typography.bodyLarge,
                                color = DhunyaTextPrimary
                            )
                        }
                    }
                }
            } else {
                EmptyStateView(
                    icon = Icons.Default.Search,
                    title = "Explore music",
                    subtitle = "Search for tracks, creators, and albums."
                )
            }
        } else {
            // Results list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DhunyaAccent)
                }
            } else {
                val hasResults = uiState.searchResult.songs.isNotEmpty() ||
                        uiState.searchResult.artists.isNotEmpty() ||
                        uiState.searchResult.albums.isNotEmpty()

                if (!hasResults) {
                    EmptyStateView(
                        icon = Icons.Default.Search,
                        title = "No results found",
                        subtitle = "We couldn't find anything matching '${uiState.query}'"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        // Songs Section
                        if (uiState.searchResult.songs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Songs",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = DhunyaTextPrimary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                            }
                            items(uiState.searchResult.songs, key = { it.id }) { song ->
                                SongListItem(
                                    song = song,
                                    isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                                    onSongClick = { viewModel.playSong(song) },
                                    onMoreClick = { onSongActionClick(song) }
                                )
                            }
                        }

                        // Artists Section
                        if (uiState.searchResult.artists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Artists",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = DhunyaTextPrimary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.searchResult.artists, key = { it.id }) { artist ->
                                        ArtistCard(artist = artist)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(artist: Artist) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        DhunyaArtwork(
            url = artist.imageUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp)) // Circular artist avatar
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunyaTextPrimary,
            maxLines = 1
        )
    }
}
