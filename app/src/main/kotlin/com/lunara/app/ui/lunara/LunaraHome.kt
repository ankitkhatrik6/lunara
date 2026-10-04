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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunara.app.LocalNavController
import com.lunara.app.LocalPlayerAwareWindowInsets
import com.lunara.app.LocalPlayerConnection
import com.lunara.app.R
import com.lunara.app.db.entities.Song
import com.lunara.app.extensions.toMediaItem
import com.lunara.app.playback.queues.ListQueue
import com.lunara.app.ui.screens.Screens
import com.lunara.app.ui.screens.search.SEARCH_FOCUS_ON_OPEN
import com.lunara.app.ui.theme.LunaraGradientEnd
import com.lunara.app.ui.theme.LunaraThemeColor
import com.lunara.app.viewmodels.HomeViewModel
import com.lunara.innertube.models.AlbumItem
import com.lunara.innertube.models.ArtistItem
import com.lunara.innertube.models.PlaylistItem
import com.lunara.innertube.models.SongItem
import com.lunara.innertube.models.YTItem
import com.lunara.innertube.pages.MoodAndGenres

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
    val dailyDiscover by viewModel.dailyDiscover.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val communityPlaylists by viewModel.communityPlaylists.collectAsStateWithLifecycle()

    val recentSongs = remember(keepListening) {
        keepListening.orEmpty().filterIsInstance<Song>()
    }

    // Jump back in is the six most recent things, topped up from quick picks so
    // the grid is never half empty on a first run.
    val tiles = remember(recentSongs, quickPicks) {
        (recentSongs + quickPicks.orEmpty())
            .distinctBy { it.id }
            .take(6)
    }

    // Trending is the daily-discover picks: one song per taste the app knows
    // about, which is what makes the shelf change from day to day.
    val trending = remember(dailyDiscover) {
        dailyDiscover.orEmpty().mapNotNull { it.recommendation as? SongItem }
    }

    val newReleases = remember(explorePage) { explorePage?.newReleaseAlbums.orEmpty() }
    val moods = remember(explorePage) { explorePage?.moodAndGenres.orEmpty() }

    val community = remember(communityPlaylists) {
        communityPlaylists.orEmpty().map { it.playlist }
    }

    // Everything the catalogue sent, minus the two shelves that are lifted out
    // and given a name of their own further up the page.
    val catalogue = remember(homePage) {
        homePage?.sections.orEmpty().filter { it.items.isNotEmpty() }
    }
    val indian = remember(catalogue) {
        catalogue.firstOrNull { it.title.mentions(*LUNARA_INDIAN_WORDS) }
    }
    val chill = remember(catalogue) {
        catalogue.firstOrNull { it.title.mentions(*LUNARA_CHILL_WORDS) }
    }
    val lifted = remember(indian, chill) {
        setOfNotNull(indian?.title, chill?.title)
    }
    val more = remember(catalogue, lifted) {
        catalogue.filterNot { it.title in lifted }
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

    fun openOnline(item: YTItem, list: List<YTItem>) {
        when (item) {
            is SongItem -> playOnline(list, item.id)
            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            else -> Unit
        }
    }
    // Resolved here, not in the shelf calls below: the LazyColumn content lambda
    // is a plain LazyListScope receiver, so it cannot host a composable read.
    val jumpBackInTitle = stringResource(R.string.jump_back_in)
    val quickPicksTitle = stringResource(R.string.lunara_quick_picks)
    val newReleaseTitle = stringResource(R.string.lunara_new_release)
    val trendingTitle = stringResource(R.string.lunara_trending_for_you)
    val indianTitle = stringResource(R.string.lunara_indian_music)
    val chillTitle = stringResource(R.string.lunara_chill_out)
    val moodsTitle = stringResource(R.string.lunara_mood_and_genres)
    val moreTitle = stringResource(R.string.lunara_more_for_you)
    val forgottenFavouritesTitle = stringResource(R.string.lunara_forgotten_favourites)

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
                Column(verticalArrangement = Arrangement.spacedBy(LunaraSpacing.lg)) {
                    LunaraHomeHeader(
                        onOpenSettings = { navController.navigate("settings") },
                    )
                    LunaraHomeSearchBar(
                        onClick = {
                            navController.navigate(Screens.Search.route)
                            runCatching {
                                navController
                                    .getBackStackEntry(Screens.Search.route)
                                    .savedStateHandle[SEARCH_FOCUS_ON_OPEN] = true
                            }
                        },
                    )
                }
            }

            if (tiles.isNotEmpty()) {
                item(key = "lunara_home_jump_back_title") {
                    LunaraSectionHeader(title = jumpBackInTitle)
                }
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

            songShelf(
                key = "lunara_quick_picks",
                title = quickPicksTitle,
                songs = quickPicks.orEmpty(),
                onPlay = { list, id -> playSongs(list, id) },
            )

            catalogueShelf(
                key = "lunara_new_release",
                title = newReleaseTitle,
                entries = newReleases,
                onOpen = { item, list -> openOnline(item, list) },
            )

            catalogueShelf(
                key = "lunara_trending",
                title = trendingTitle,
                entries = trending,
                onOpen = { item, list -> openOnline(item, list) },
            )

            // Indian music is a shelf the catalogue keeps for itself most of the
            // time, so it is looked up by name. When it is not there, the
            // community playlists are the next best thing in the same spirit.
            if (indian != null) {
                catalogueShelf(
                    key = "lunara_indian",
                    title = indianTitle,
                    entries = indian.items,
                    onOpen = { item, list -> openOnline(item, list) },
                )
            } else {
                catalogueShelf(
                    key = "lunara_indian_community",
                    title = indianTitle,
                    entries = community,
                    onOpen = { item, list -> openOnline(item, list) },
                )
            }

            if (chill != null) {
                catalogueShelf(
                    key = "lunara_chill",
                    title = chillTitle,
                    entries = chill.items,
                    onOpen = { item, list -> openOnline(item, list) },
                )
            }

            if (moods.isNotEmpty()) {
                item(key = "lunara_moods_title") {
                    LunaraSectionHeader(title = moodsTitle)
                }
                item(key = "lunara_moods") {
                    LunaraMoodGrid(
                        moods = moods,
                        onOpen = { mood ->
                            navController.navigate(
                                "youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}",
                            )
                        },
                    )
                }
            }

            if (more.isNotEmpty()) {
                item(key = "lunara_more_title") {
                    LunaraSectionHeader(title = moreTitle)
                }
                more.forEachIndexed { index, section ->
                    item(key = "lunara_more_$index") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = LunaraSpacing.screenEdge),
                            horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.md),
                        ) {
                            items(section.items, key = { it.id }) { item ->
                                LunaraShelfCard(
                                    title = item.title,
                                    artworkUrl = item.thumbnail,
                                    subtitle = item.subtitleOrNull(),
                                    onClick = { openOnline(item, section.items) },
                                )
                            }
                        }
                    }
                }
            }

            songShelf(
                key = "lunara_forgotten",
                title = forgottenFavouritesTitle,
                songs = forgottenFavorites.orEmpty(),
                onPlay = { list, id -> playSongs(list, id) },
            )

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

/** A shelf of whatever the catalogue sent: songs, albums, playlists, artists. */
private fun LazyListScope.catalogueShelf(
    key: String,
    title: String,
    entries: List<YTItem>,
    onOpen: (YTItem, List<YTItem>) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "${key}_title") {
        LunaraSectionHeader(title = title)
    }
    item(key = key) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = LunaraSpacing.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.md),
        ) {
            items(entries, key = { it.id }) { item ->
                LunaraShelfCard(
                    title = item.title,
                    artworkUrl = item.thumbnail,
                    subtitle = item.subtitleOrNull(),
                    onClick = { onOpen(item, entries) },
                )
            }
        }
    }
}

/** A second line for a card, when the item has something worth saying. */
private fun YTItem.subtitleOrNull(): String? =
    when (this) {
        is SongItem -> artists.joinToString(", ") { it.name }.ifBlank { null }
        is AlbumItem -> artists?.joinToString(", ") { it.name }?.ifBlank { null }
        is PlaylistItem -> author?.name ?: songCountText
        else -> null
    }

/** Whether a shelf title is one of [words]. How a catalogue shelf is found by name. */
private fun String.mentions(vararg words: String): Boolean {
    val lower = lowercase()
    return words.any { lower.contains(it) }
}

/** What a shelf about the subcontinent tends to be called. */
private val LUNARA_INDIAN_WORDS =
    arrayOf("indian", "hindi", "bollywood", "desi", "punjabi", "tamil", "telugu")

/** What a shelf for winding down tends to be called. */
private val LUNARA_CHILL_WORDS =
    arrayOf("chill", "relax", "calm", "sleep", "lofi", "lo-fi", "peaceful")

/** The field at the top of Home: a door to search rather than a place to type. */
@Composable
private fun LunaraHomeSearchBar(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LunaraRadius.pill)
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LunaraSpacing.screenEdge)
            .height(LunaraSizes.field)
            .clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = LunaraAlpha.glassStrong))
            .border(1.dp, scheme.outlineVariant.copy(alpha = LunaraAlpha.hairline), shape)
            .lunaraPressScale(interaction, pressedScale = 0.99f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = LunaraSpacing.lg),
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(LunaraSpacing.md))
        Text(
            text = stringResource(R.string.lunara_search_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant.copy(alpha = LunaraAlpha.muted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Mood and genres, as a page of coloured doors.
 *
 * The colour is the catalogue's own stripe colour for the mood, which is the one
 * place in the app a colour comes from somewhere other than the theme. It earns
 * that: the doors have to be told apart at a glance, and a row of identical grey
 * rectangles tells you nothing.
 */
@Composable
private fun LunaraMoodGrid(
    moods: List<MoodAndGenres.Item>,
    onOpen: (MoodAndGenres.Item) -> Unit,
) {
    val distinct = remember(moods) { moods.distinctBy { it.title } }
    Column(
        verticalArrangement = Arrangement.spacedBy(LunaraSpacing.md),
        modifier = Modifier.padding(horizontal = LunaraSpacing.screenEdge),
    ) {
        distinct.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.md)) {
                pair.forEach { mood ->
                    LunaraMoodCard(
                        title = mood.title,
                        stripe = Color(mood.stripeColor),
                        onClick = { onOpen(mood) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One door in the mood grid. */
@Composable
private fun LunaraMoodCard(
    title: String,
    stripe: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(LunaraRadius.md)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(stripe.copy(alpha = 0.22f))
            .border(1.dp, stripe.copy(alpha = LunaraAlpha.hairline), shape)
            .lunaraPressScale(interaction, pressedScale = 0.97f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LunaraSpacing.lg),
        )
    }
}