/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * The "Yours" tab — a personal home of Lunara-styled rails ported from the
 * Flutter LunaraPlayer home: recently played, recommended, browse categories,
 * your playlists (gradient thumbnail cards, dynamic theme), mood playlists and
 * favorite artists. Local library data plus online account playlists & moods.
 */

package com.lunara.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lunara.app.LocalNavController
import com.lunara.app.LocalPlayerAwareWindowInsets
import com.lunara.app.LocalPlayerConnection
import com.lunara.app.R
import com.lunara.app.extensions.toMediaItem
import com.lunara.app.playback.queues.ListQueue
import com.lunara.app.ui.component.LunaraCategory
import com.lunara.app.ui.component.LunaraCategoryGrid
import com.lunara.app.ui.component.LunaraGradientCard
import com.lunara.app.ui.component.LunaraMusicCard
import com.lunara.app.ui.component.LunaraPlaylistCard
import com.lunara.app.ui.component.LunaraPlaylistPalette
import com.lunara.app.ui.component.LunaraSectionHeader
import com.lunara.app.viewmodels.YoursViewModel

/** Distinct glyphs cycled across mood cards. */
private val MoodIcons = listOf(
    R.drawable.graphic_eq,
    R.drawable.radio,
    R.drawable.equalizer,
    R.drawable.star,
    R.drawable.favorite,
    R.drawable.bedtime,
    R.drawable.music_note,
    R.drawable.speed,
)

@Composable
fun YoursScreen(
    viewModel: YoursViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current

    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val recommended by viewModel.recommended.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteArtists by viewModel.favoriteArtists.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val moodAndGenres by viewModel.moodAndGenres.collectAsStateWithLifecycle()
    val trendingSongs by viewModel.trendingSongs.collectAsStateWithLifecycle()
    val trendingArtists by viewModel.trendingArtists.collectAsStateWithLifecycle()

    val seed = MaterialTheme.colorScheme.primary

    fun playSongsAt(title: String, songs: List<com.lunara.app.db.entities.Song>, index: Int) {
        playerConnection?.playQueue(
            ListQueue(
                title = title,
                items = songs.map { it.toMediaItem() },
                startIndex = index,
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        // ---- Recently Played ----
        if (recentlyPlayed.isNotEmpty()) {
            item("recent_header") {
                LunaraSectionHeader(
                    title = stringResource(R.string.recently_played),
                    onSeeMore = { navController.navigate("history") },
                )
            }
            item("recent_rail") {
                val railTitle = stringResource(R.string.recently_played)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(recentlyPlayed, key = { _, s -> "recent_${s.id}" }) { index, song ->
                        LunaraMusicCard(
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnailUrl = song.thumbnailUrl,
                            onClick = { playSongsAt(railTitle, recentlyPlayed, index) },
                        )
                    }
                }
            }
        }

        // ---- Recommended for You ----
        if (recommended.isNotEmpty()) {
            item("reco_header") {
                LunaraSectionHeader(title = stringResource(R.string.recommended_for_you))
            }
            item("reco_rail") {
                val railTitle = stringResource(R.string.recommended_for_you)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(recommended, key = { _, s -> "reco_${s.id}" }) { index, song ->
                        LunaraMusicCard(
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnailUrl = song.thumbnailUrl,
                            onClick = { playSongsAt(railTitle, recommended, index) },
                        )
                    }
                }
            }
        }

        // ---- Browse Categories ----
        item("cat_header") {
            LunaraSectionHeader(title = stringResource(R.string.browse_categories))
        }
        item("cat_grid") {
            LunaraCategoryGrid(
                categories = listOf(
                    LunaraCategory(stringResource(R.string.songs), R.drawable.music_note, Color(0xFFFF6B6B)) {
                        navController.navigate("yours/songs")
                    },
                    LunaraCategory(stringResource(R.string.albums), R.drawable.album, Color(0xFF4ECDC4)) {
                        navController.navigate("yours/albums")
                    },
                    LunaraCategory(stringResource(R.string.artists), R.drawable.artist, Color(0xFFFFBE0B)) {
                        navController.navigate("yours/artists")
                    },
                    LunaraCategory(stringResource(R.string.playlists), R.drawable.playlist_play, Color(0xFF8B5CF6)) {
                        navController.navigate("yours/playlists")
                    },
                    LunaraCategory(stringResource(R.string.downloads), R.drawable.download, Color(0xFFEC4899)) {
                        navController.navigate("auto_playlist/downloaded")
                    },
                    LunaraCategory(stringResource(R.string.favorites), R.drawable.favorite, Color(0xFFEF4444)) {
                        navController.navigate("auto_playlist/liked")
                    },
                ),
            )
        }

        // ---- Your Playlists (gradient thumbnail cards, dynamic theme) ----
        val hasPlaylists = playlists.isNotEmpty() || !accountPlaylists.isNullOrEmpty() || likedSongs.isNotEmpty()
        if (hasPlaylists) {
            item("pl_header") {
                LunaraSectionHeader(
                    title = stringResource(R.string.your_playlists),
                    onSeeMore = { navController.navigate("yours/playlists") },
                )
            }
            item("pl_rail") {
                val favLabel = stringResource(R.string.favorites)
                val songsWord = stringResource(R.string.songs)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Favorites always leads the rail.
                    item(key = "pl_favorites") {
                        LunaraGradientCard(
                            title = favLabel,
                            subtitle = "${likedSongs.size} ${songsWord.lowercase()}",
                            thumbnailUrl = likedSongs.firstOrNull()?.thumbnailUrl,
                            seedColor = seed,
                            iconRes = R.drawable.favorite,
                            onClick = { navController.navigate("auto_playlist/liked") },
                        )
                    }
                    items(playlists, key = { "pl_local_${it.id}" }) { playlist ->
                        LunaraGradientCard(
                            title = playlist.title,
                            subtitle = "${playlist.songCount} ${songsWord.lowercase()}",
                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                            seedColor = seed,
                            onClick = { navController.navigate("local_playlist/${playlist.id}") },
                        )
                    }
                    items(accountPlaylists.orEmpty(), key = { "pl_online_${it.id}" }) { playlist ->
                        LunaraGradientCard(
                            title = playlist.title,
                            subtitle = playlist.songCountText ?: "",
                            thumbnailUrl = playlist.thumbnail,
                            seedColor = seed,
                            onClick = {
                                navController.navigate("online_playlist/${playlist.id.removePrefix("VL")}")
                            },
                        )
                    }
                }
            }
        }

        // ---- Trending (most-played songs + artists, interleaved) ----
        if (trendingSongs.isNotEmpty() || trendingArtists.isNotEmpty()) {
            item("trending_header") {
                LunaraSectionHeader(
                    title = stringResource(R.string.trending),
                    onSeeMore = { navController.navigate("stats") },
                )
            }
            item("trending_rail") {
                val railTitle = stringResource(R.string.trending)
                val songsWord = stringResource(R.string.songs).lowercase()
                // Interleave: song, artist, song, artist …
                val mixed = buildList {
                    val n = maxOf(trendingSongs.size, trendingArtists.size)
                    for (i in 0 until n) {
                        trendingSongs.getOrNull(i)?.let { add(it) }
                        trendingArtists.getOrNull(i)?.let { add(it) }
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        mixed,
                        key = { item ->
                            when (item) {
                                is com.lunara.app.db.entities.Song -> "t_song_${item.id}"
                                is com.lunara.app.db.entities.Artist -> "t_artist_${item.id}"
                                else -> item.hashCode()
                            }
                        },
                    ) { item ->
                        when (item) {
                            is com.lunara.app.db.entities.Song -> LunaraMusicCard(
                                title = item.title,
                                subtitle = item.artists.joinToString { it.name },
                                thumbnailUrl = item.thumbnailUrl,
                                onClick = {
                                    playSongsAt(railTitle, trendingSongs, trendingSongs.indexOf(item).coerceAtLeast(0))
                                },
                            )
                            is com.lunara.app.db.entities.Artist -> LunaraMusicCard(
                                title = item.title,
                                subtitle = if (item.songCount > 0) "${item.songCount} $songsWord" else "",
                                thumbnailUrl = item.thumbnailUrl,
                                isCircular = true,
                                fallbackIcon = R.drawable.artist,
                                onClick = { navController.navigate("artist/${item.id}") },
                            )
                            else -> {}
                        }
                    }
                }
            }
        }

        // ---- Mood Playlists ----
        val moodItems = moodAndGenres.orEmpty().flatMap { it.items }
        if (moodItems.isNotEmpty()) {
            item("mood_header") {
                LunaraSectionHeader(
                    title = stringResource(R.string.mood_playlists),
                    onSeeMore = { navController.navigate("mood_and_genres") },
                )
            }
            item("mood_rail") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        moodItems.take(15),
                        key = { _, it -> "mood_${it.endpoint.browseId}_${it.title}" },
                    ) { index, mood ->
                        LunaraGradientCard(
                            title = mood.title,
                            subtitle = "",
                            thumbnailUrl = null,
                            seedColor = Color(mood.stripeColor or 0xFF000000L),
                            width = 138.dp,
                            height = 208.dp,
                            iconRes = MoodIcons[index % MoodIcons.size],
                            onClick = {
                                navController.navigate(
                                    "youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}",
                                )
                            },
                        )
                    }
                }
            }
        }

        // ---- Favorite Artists ----
        if (favoriteArtists.isNotEmpty()) {
            item("artist_header") {
                LunaraSectionHeader(
                    title = stringResource(R.string.favorite_artists),
                    onSeeMore = { navController.navigate("yours/artists") },
                )
            }
            item("artist_rail") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(favoriteArtists, key = { "artist_${it.id}" }) { artist ->
                        LunaraMusicCard(
                            title = artist.title,
                            // Nothing played or liked yet: show just the name, not "0 songs".
                            subtitle = if (artist.songCount > 0) {
                                pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount)
                            } else {
                                ""
                            },
                            thumbnailUrl = artist.thumbnailUrl,
                            isCircular = true,
                            fallbackIcon = R.drawable.artist,
                            onClick = { navController.navigate("artist/${artist.id}") },
                        )
                    }
                }
            }
        }

        item("bottom_spacer") {
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * LunaraPlayer-style playlists grid: wide colour+artwork gradient cards for
 * Favorites, Downloads, your local playlists and (if signed in) your account
 * playlists. Tapping opens the playlist's own detail page.
 */
@Composable
fun YoursPlaylistsGrid(
    navController: NavController,
    viewModel: YoursViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val songsWord = stringResource(R.string.songs).lowercase()

    data class PlaylistCard(
        val title: String,
        val subtitle: String,
        val thumbnails: List<String>,
        val iconRes: Int?,
        val route: String,
    )

    // Favorites & Downloads intentionally excluded here — they are their own
    // browse-category tiles; this grid shows real playlists only.
    val cards = buildList {
        playlists.forEach { pl ->
            add(
                PlaylistCard(
                    title = pl.title,
                    subtitle = "${pl.songCount} $songsWord",
                    thumbnails = pl.thumbnails.take(4),
                    iconRes = null,
                    route = "local_playlist/${pl.id}",
                ),
            )
        }
        accountPlaylists.orEmpty().forEach { pl ->
            add(
                PlaylistCard(
                    title = pl.title,
                    subtitle = pl.songCountText ?: "",
                    thumbnails = listOfNotNull(pl.thumbnail),
                    iconRes = null,
                    route = "online_playlist/${pl.id.removePrefix("VL")}",
                ),
            )
        }
    }

    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 8.dp,
            bottom = insets.calculateBottomPadding() + 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItemsIndexed(cards, key = { _, c -> c.route }) { index, card ->
            LunaraPlaylistCard(
                title = card.title,
                subtitle = card.subtitle,
                thumbnails = card.thumbnails,
                seedColor = LunaraPlaylistPalette[index % LunaraPlaylistPalette.size],
                iconRes = card.iconRes,
                onClick = { navController.navigate(card.route) },
            )
        }
    }
}

/**
 * Hosts a reused library list/grid as a pushed screen with its own back button,
 * so browse-category tiles open a dedicated page and Back returns to Yours
 * (instead of switching to the Library tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoursCategoryScreen(
    navController: NavController,
    titleRes: Int,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        TopAppBar(
            title = { Text(stringResource(titleRes)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
            actions = actions,
        )
    }
}
