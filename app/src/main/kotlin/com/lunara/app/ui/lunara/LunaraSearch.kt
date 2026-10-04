/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * The Lunara search page.
 *
 * Search used to open on a text field sitting in a coloured bar, with the
 * keyboard already up and the page underneath it invisible. That is a page for
 * people who already know what they want. Most of the time the answer is one of
 * two things: something you looked for before, or one of the six places with
 * new music in them. So the field waits to be asked, and what is under it is
 * those two things - the last searches as chips, and a grid of somewhere to go.
 *
 * The moment you type, all of that is replaced by results, because from then on
 * the only thing on the page worth having is the answer.
 */

package com.lunara.app.ui.lunara

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunara.app.LocalDatabase
import com.lunara.app.LocalIsPlayerExpanded
import com.lunara.app.LocalNavController
import com.lunara.app.LocalPlayerAwareWindowInsets
import com.lunara.app.LocalPlayerConnection
import com.lunara.app.R
import com.lunara.app.constants.PauseSearchHistoryKey
import com.lunara.app.db.entities.SearchHistory
import com.lunara.app.playback.queues.YouTubeQueue
import com.lunara.app.ui.screens.search.OnlineSearchScreen
import com.lunara.app.ui.screens.search.SEARCH_FOCUS_ON_OPEN
import com.lunara.app.utils.SearchRoutes
import com.lunara.app.utils.rememberPreference
import com.lunara.innertube.models.WatchEndpoint
import com.lunara.innertube.utils.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One square in the browse grid. */
private data class LunaraBrowseEntry(
    @StringRes val titleId: Int,
    @DrawableRes val iconRes: Int,
    val route: String,
)

/**
 * Six places with new music in them, in the order most people want them.
 *
 * Six and not eight: two columns of three is a shape the eye can take in at
 * once, and a grid that needs a second look has failed at being a shortcut.
 */
private val LUNARA_BROWSE_ENTRIES = listOf(
    LunaraBrowseEntry(R.string.history, R.drawable.history, "history"),
    LunaraBrowseEntry(R.string.new_release_albums, R.drawable.album, "new_release"),
    LunaraBrowseEntry(R.string.charts, R.drawable.trending_up, "charts_screen"),
    LunaraBrowseEntry(R.string.mood_and_genres, R.drawable.explore_outlined, "mood_and_genres"),
    LunaraBrowseEntry(R.string.stats, R.drawable.stats, "stats"),
    LunaraBrowseEntry(R.string.lunara_local_folders, R.drawable.folder, "local_folders"),
)

@Composable
fun LunaraSearch(
    savedStateHandle: SavedStateHandle,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val isPlayerExpanded = LocalIsPlayerExpanded.current

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    val history by remember { database.searchHistory() }
        .collectAsStateWithLifecycle(initialValue = emptyList<SearchHistory>())

    // Opening Search from the tab shows what there is to browse; the keyboard
    // waits for the tab to be tapped a second time, which is what arrives here
    // as a rising count.
    val scrollToTopCount by savedStateHandle
        .getStateFlow("scrollToTopCount", 0)
        .collectAsStateWithLifecycle(initialValue = 0)
    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    var isHandlingScrollToTop by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            isHandlingScrollToTop = true

            kotlinx.coroutines.delay(100)

            if (!isPlayerExpanded) {
                focusManager.clearFocus(force = true)
                kotlinx.coroutines.delay(50)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {
                    // The field was not laid out yet. Not worth reporting: the
                    // user simply taps it, which is the same outcome.
                }
            }

            kotlinx.coroutines.delay(500)
            isHandlingScrollToTop = false
        }
    }

    // Opened from the search bar on the home page, tapping it meant wanting to
    // type, so the keyboard comes with it. The field may not be laid out while
    // the screen slides in, so asking for focus is tried a few times.
    val focusOnOpen by savedStateHandle
        .getStateFlow(SEARCH_FOCUS_ON_OPEN, false)
        .collectAsStateWithLifecycle()
    LaunchedEffect(focusOnOpen) {
        if (!focusOnOpen) return@LaunchedEffect
        // Cleared only once done: clearing it first changes the key this effect
        // runs on, which cancels the effect before it has asked for anything.
        try {
            repeat(5) {
                kotlinx.coroutines.delay(100)
                if (isPlayerExpanded) return@LaunchedEffect
                if (runCatching { focusRequester.requestFocus() }.isSuccess) {
                    keyboardController?.show()
                    return@LaunchedEffect
                }
            }
        } finally {
            savedStateHandle[SEARCH_FOCUS_ON_OPEN] = false
        }
    }

    fun handleSearch(raw: String) {
        val searchQuery = raw.trim()
        if (searchQuery.isEmpty()) return

        keyboardController?.hide()
        focusManager.clearFocus()

        if (!pauseSearchHistory) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    database.query {
                        insert(SearchHistory(query = searchQuery))
                    }
                }
            }
        }

        // A pasted link is not a search. It is a request to play what it points
        // at, and searching for the text of the link would be the wrong answer.
        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
            is YouTubeUrlParser.ParsedUrl.Video -> {
                playerConnection?.playQueue(
                    YouTubeQueue(WatchEndpoint(videoId = parsedUrl.id)),
                )
            }

            is YouTubeUrlParser.ParsedUrl.Playlist -> {
                navController.navigate("online_playlist/${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Album -> {
                navController.navigate("album/MPREb_${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                navController.navigate("artist/${parsedUrl.id}")
            }

            null -> {
                navController.navigate(SearchRoutes.resultRoute(searchQuery))
            }
        }
    }

    val searchTitle = stringResource(R.string.search)
    val searchHint = stringResource(R.string.lunara_search_hint)
    val clearLabel = stringResource(R.string.lunara_clear_recent)

    // The page is already there when you arrive; it should not be caught
    // assembling itself. A short fade up from just below its resting place is
    // enough to say the page is new without being seen to happen.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val appear by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = LunaraMotion.slow(),
        label = "lunaraSearchAppear",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LunaraAmbient(
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopCenter),
            height = 240.dp,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(windowInsets.asPaddingValues()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = appear
                        translationY = (1f - appear) * 24f
                    },
            ) {
                LunaraScreenTitle(title = searchTitle)

                Spacer(Modifier.height(LunaraSpacing.lg))

                LunaraSearchField(
                    value = query.text,
                    onValueChange = { query = TextFieldValue(it) },
                    onSearch = { handleSearch(it) },
                    hint = searchHint,
                    focusRequester = focusRequester,
                    leadingIconRes = R.drawable.search,
                    modifier = Modifier.padding(horizontal = LunaraSpacing.screenEdge),
                    trailing = {
                        if (query.text.isNotEmpty()) {
                            LunaraIconAction(
                                iconRes = R.drawable.close,
                                contentDescription = clearLabel,
                                onClick = { query = TextFieldValue("") },
                                size = 32.dp,
                                filled = false,
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(LunaraSpacing.xl))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (query.text.isBlank()) {
                    LunaraSearchLanding(
                        history = history,
                        onPickQuery = { picked -> query = TextFieldValue(picked) },
                        onClearHistory = {
                            coroutineScope.launch(Dispatchers.IO) {
                                runCatching { database.clearSearchHistory() }
                            }
                        },
                        onBrowse = { route -> navController.navigate(route) },
                    )
                } else {
                    OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = { query = it },
                        onSearch = { handleSearch(it) },
                        onDismiss = { },
                        pureBlack = false,
                    )
                }
            }
        }
    }
}

/**
 * What the page shows before you have asked for anything.
 *
 * Recent searches first, because a search you have made before is the single
 * most likely thing you are about to type again, and tapping it is one gesture
 * against typing it out. Then somewhere to go, for the times you did not come
 * here looking for something in particular.
 */
@Composable
private fun LunaraSearchLanding(
    history: List<SearchHistory>,
    onPickQuery: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBrowse: (String) -> Unit,
) {
    val recentTitle = stringResource(R.string.lunara_recent)
    val clearLabel = stringResource(R.string.lunara_clear_recent)
    val browseTitle = stringResource(R.string.lunara_browse)
    val nothingTitle = stringResource(R.string.lunara_nothing_recent)
    val nothingBody = stringResource(R.string.lunara_nothing_recent_body)

    LazyColumn(
        contentPadding = PaddingValues(bottom = LunaraSpacing.giant),
        verticalArrangement = Arrangement.spacedBy(LunaraSpacing.xxl),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (history.isEmpty()) {
            item(key = "lunara_search_nothing") {
                LunaraEmptyState(
                    iconRes = R.drawable.search_off,
                    title = nothingTitle,
                    body = nothingBody,
                )
            }
        } else {
            item(key = "lunara_search_recent_title") {
                LunaraSectionHeader(
                    title = recentTitle,
                    actionLabel = clearLabel,
                    onAction = onClearHistory,
                )
            }

            item(key = "lunara_search_recent_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = LunaraSpacing.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.sm),
                ) {
                    items(history, key = { it.id }) { entry ->
                        LunaraChip(
                            label = entry.query,
                            iconRes = R.drawable.history,
                            onClick = { onPickQuery(entry.query) },
                        )
                    }
                }
            }
        }

        item(key = "lunara_search_browse_title") {
            LunaraSectionHeader(title = browseTitle)
        }

        item(key = "lunara_search_browse_grid") {
            Column(
                verticalArrangement = Arrangement.spacedBy(LunaraSpacing.md),
                modifier = Modifier.padding(horizontal = LunaraSpacing.screenEdge),
            ) {
                LUNARA_BROWSE_ENTRIES.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(LunaraSpacing.md)) {
                        pair.forEach { entry ->
                            LunaraBrowseTile(
                                title = stringResource(entry.titleId),
                                iconRes = entry.iconRes,
                                onClick = { onBrowse(entry.route) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}



