/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * The Lunara home page.
 *
 * Built the way the page is actually read: a glow of colour at the top, then
 * two columns of small tiles for the handful of things you had on a moment ago,
 * then shelves. No hero card, no greeting, no carousel that advances itself -
 * every one of those costs a swipe before the first piece of music is reachable.
 */

package com.lunara.app.ui.lunara

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunara.app.LocalNavController
import com.lunara.app.LocalPlayerAwareWindowInsets
import com.lunara.app.LocalPlayerConnection
import com.lunara.app.R
import com.lunara.app.db.entities.Album
import com.lunara.app.db.entities.Artist
import com.lunara.app.db.entities.LocalItem
import com.lunara.app.db.entities.Playlist
import com.lunara.app.db.entities.Song
import com.lunara.app.extensions.toMediaItem
import com.lunara.app.playback.queues.ListQueue
import com.lunara.app.ui.theme.LunaraGradientEnd
import com.lunara.app.ui.theme.LunaraThemeColor
import com.lunara.app.viewmodels.HomeViewModel
import com.lunara.innertube.models.AlbumItem
import com.lunara.innertube.models.ArtistItem
import com.lunara.innertube.models.PlaylistItem
import com.lunara.innertube.models.SongItem
import com.lunara.innertube.models.YTItem

@Composable
fun LunaraHome(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val listState = rememberLazyListState()

    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()

    val recentSongs = remember(keepListening) {
        keepListening.orEmpty().filterIsInstance<Song>()
    }

    // Six things, not a hero: whatever was on recently, topped up from quick
    // picks so the grid is never half empty.
    val tiles = remember(recentSongs, quickPicks) {
        (recentSongs + quickPicks.orEmpty())
            .distinctBy { it.id }
            .take(6)
    }

    fun playSongs(songs: List<Song>, startAt: String) {
        if (songs.isEmpty()) return
        val index = songs.indexOfFirst { it.id == startAt }.coerceAtLeast(0)
        playerConnection.playQueue(
            ListQueue(
                title = null,
                items = songs.map { it.toMediaItem() },
                startIndex = index,
            ),
        )
    }

    fun playOnline(items: List<YTItem>, startAt: String) {
        val songs = items.filterIsInstance<SongItem>()
        if (songs.isEmpty()) return
        val index = songs.indexOfFirst { it.id == startAt }.coerceAtLeast(0)
        playerConnection.playQueue(
            ListQueue(
                title = null,
                items = songs.map { it.toMediaItem() },
                startIndex = index,
            ),
        )
    }

    fun openLocal(item: LocalItem, list: List<LocalItem>) {
        when (item) {
            is Song -> playSongs(list.filterIsInstance<Song>(), item.id)
            is Album -> navController.navigate("album/${item.id}")
            is Artist -> navController.navigate("artist/${item.id}")
            is Playlist -> navController.navigate("local_playlist/${item.id}")
            else -> Unit
        }
    }

    fun openOnline(item: YTItem, list: List<YTItem>) {
        when (item) {
            is SongItem -> playOnline(list, item.id)
            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            else -> Unit
        }
    }
Box(modifier = Modifier.fillMaxSize()) {
        // The colour of the page, before any of the content. It fades out well
        // above the first shelf, so nothing ever sits on top of it.
        LunaraAmbient(
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopCenter),
            height = 260.dp,
        )

        LazyColumn(
            state = listState,
            contentPadding = windowInsets.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(LunaraSpacing.xxl),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "lunara_home_header") {
                LunaraHomeHeader(
                    onOpenSettings = { navController.navigate("settings") },
                )
            }

            if (tiles.isNotEmpty()) {
                item(key = "lunara_home_tiles") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(LunaraSpacing.sm),
                        modifier = Modifier.padding(horizontal = LunaraSpacing.screenEdge),
                    ) {
                        tiles.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.sm)) {
                                pair.forEach { song ->
                                    LunaraQuickTile(
                                        title = song.title,
                                        artworkUrl = song.thumbnailUrl,
                                        onClick = { playSongs(tiles, song.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            localShelf(
                key = "jump_back_in",
                title = stringResource(R.string.jump_back_in),
                items = keepListening.orEmpty(),
                onOpen = ::openLocal,
            )

            songShelf(
                key = "made_for_you",
                title = stringResource(R.string.lunara_made_for_you),
                songs = quickPicks.orEmpty(),
                onPlay = ::playSongs,
            )

            songShelf(
                key = "forgotten_favourites",
                title = stringResource(R.string.lunara_forgotten_favourites),
                songs = forgottenFavorites.orEmpty(),
                onPlay = ::playSongs,
            )

            homePage?.sections.orEmpty().forEachIndexed { index, section ->
                if (section.items.isNotEmpty()) {
                    item(key = "lunara_section_title_$index") {
                        LunaraSectionHeader(title = section.title)
                    }
                    item(key = "lunara_section_$index") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = LunaraSpacing.screenEdge),
                            horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.md),
                        ) {
                            items(section.items, key = { it.id }) { item ->
                                LunaraShelfCard(
                                    title = item.title,
                                    artworkUrl = item.thumbnail,
                                    onClick = { openOnline(item, section.items) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "lunara_home_footer") {
                Spacer(Modifier.height(LunaraSpacing.giant))
            }
        }
    }
}
/** The wordmark and the one control the page needs. */
@Composable
private fun LunaraHomeHeader(
    onOpenSettings: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = LunaraSpacing.screenEdge,
                end = LunaraSpacing.screenEdge,
                top = LunaraSpacing.md,
            ),
    ) {
        Text(
            text = "Lunara",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(LunaraThemeColor, LunaraGradientEnd)),
            ),
            modifier = Modifier.weight(1f),
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(LunaraSizes.touchTarget)
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings),
        ) {
            Icon(
                painter = painterResource(R.drawable.settings),
                contentDescription = stringResource(R.string.settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A shelf of library songs, played as a queue in the order drawn. */
private fun LazyListScope.songShelf(
    key: String,
    title: String,
    songs: List<Song>,
    onPlay: (List<Song>, String) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "${key}_title") {
        LunaraSectionHeader(title = title)
    }
    item(key = key) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = LunaraSpacing.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.md),
        ) {
            items(songs, key = { it.id }) { song ->
                LunaraShelfCard(
                    title = song.title,
                    artworkUrl = song.thumbnailUrl,
                    subtitle = song.artists.joinToString(", ") { it.name },
                    onClick = { onPlay(songs, song.id) },
                )
            }
        }
    }
}

/** A shelf that may hold songs, albums, artists or playlists. */
private fun LazyListScope.localShelf(
    key: String,
    title: String,
    items: List<LocalItem>,
    onOpen: (LocalItem, List<LocalItem>) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "${key}_title") {
        LunaraSectionHeader(title = title)
    }
    item(key = key) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = LunaraSpacing.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.md),
        ) {
            items(items, key = { it.id }) { item ->
                LunaraShelfCard(
                    title = item.title,
                    artworkUrl = item.thumbnailUrl,
                    onClick = { onOpen(item, items) },
                )
            }
        }
    }
}