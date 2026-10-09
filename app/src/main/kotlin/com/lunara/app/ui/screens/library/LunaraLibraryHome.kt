/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Cute, dreamy "Your Library" landing: pastel emblem header, bubbly filter chips,
 * kawaii gradient pinned playlists (Liked Songs in sakura rose, Top 50 in peach coral,
 * Offline in mint-teal, Cached in lavender, Local Music in sky blue), followed by
 * user playlists and favorite artists.
 */

package com.lunara.app.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lunara.app.ui.theme.SpotifyElevatedSurface
import com.lunara.app.ui.theme.SpotifyTextPrimary
import com.lunara.app.LocalDatabase
import com.lunara.app.LocalPlayerAwareWindowInsets
import com.lunara.app.R
import com.lunara.app.constants.LocalMusicFoldersKey
import com.lunara.app.constants.ShowCachedPlaylistKey
import com.lunara.app.constants.ShowDownloadedPlaylistKey
import com.lunara.app.constants.ShowLikedPlaylistKey
import com.lunara.app.constants.ShowTopPlaylistKey
import com.lunara.app.constants.ShowUploadedPlaylistKey
import com.lunara.app.ui.component.LunaraFilterChips
import com.lunara.app.ui.component.LunaraMusicCard
import com.lunara.app.ui.component.LunaraPlaylistCard
import com.lunara.app.ui.component.LunaraPlaylistPalette
import com.lunara.app.ui.component.LunaraSectionHeader
import com.lunara.app.ui.screens.Screens
import com.lunara.app.utils.LocalMusic
import com.lunara.app.utils.rememberPreference
import com.lunara.app.viewmodels.YoursViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val LONG_RATIO = 3.0f
private const val BOX_RATIO = 1.62f
private const val USER_RATIO = 1.62f

private enum class LibraryTab { ALL, PLAYLISTS, ARTISTS, DOWNLOADED }

@Composable
fun LunaraLibraryHome(
    navController: NavController,
    viewModel: YoursViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val artists by viewModel.favoriteArtists.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val likedThumbs by viewModel.likedThumbnails.collectAsStateWithLifecycle()
    val downloadedThumbs by viewModel.downloadedThumbnails.collectAsStateWithLifecycle()
    val uploadedThumbs by viewModel.uploadedThumbnails.collectAsStateWithLifecycle()
    val localThumbs by viewModel.localThumbnails.collectAsStateWithLifecycle()
    val localCount by viewModel.localSongCount.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val (localMusicFolders) = rememberPreference(LocalMusicFoldersKey, emptySet())
    var localGranted by remember { mutableStateOf(LocalMusic.hasPermission(context)) }
    val audioPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            localGranted = granted
            if (granted) {
                scope.launch(Dispatchers.IO) {
                    runCatching { LocalMusic(context, database).scan(localMusicFolders) }
                }
            }
        }
    val topThumbs by viewModel.topThumbnails.collectAsStateWithLifecycle()
    val cachedThumbs by viewModel.cachedThumbnails.collectAsStateWithLifecycle()
    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, defaultValue = true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, defaultValue = true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, defaultValue = true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, defaultValue = true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, defaultValue = true)
    val songsWord = stringResource(R.string.songs).lowercase()

    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.ALL) }

    val weeklyMost = playlists.firstOrNull { it.title.contains("weekly most", ignoreCase = true) }
    val monthlyMost = playlists.firstOrNull { it.title.contains("monthly most", ignoreCase = true) }
    val userPlaylists = playlists.filter { it !== weeklyMost && it !== monthlyMost }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        // Minimalist Spotify "Your Library" header
        item("spotify_library_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SpotifyElevatedSurface)
                            .clickable { navController.navigate("settings") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = stringResource(R.string.settings),
                            tint = SpotifyTextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.filter_library),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { navController.navigate(Screens.Search.route) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = stringResource(R.string.search),
                            tint = SpotifyTextPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                LunaraFilterChips(
                    chips = listOf(
                        LibraryTab.ALL to "All",
                        LibraryTab.PLAYLISTS to stringResource(R.string.playlists),
                        LibraryTab.ARTISTS to stringResource(R.string.artists),
                        LibraryTab.DOWNLOADED to stringResource(R.string.offline),
                    ),
                    currentValue = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
        }

        val showSystemPlaylists = selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.PLAYLISTS
        val showDownloadedOnly = selectedTab == LibraryTab.DOWNLOADED

        // ---- System playlists ----
        if ((showSystemPlaylists || showDownloadedOnly) && showLiked && !showDownloadedOnly) {
            item("liked") {
                LongPad {
                    LunaraPlaylistCard(
                        title = stringResource(R.string.liked),
                        subtitle = "${likedSongs.size} $songsWord",
                        thumbnails = likedThumbs,
                        seedColor = Color(0xFF5138AC),
                        aspectRatio = LONG_RATIO,
                        iconRes = R.drawable.favorite,
                        onClick = { navController.navigate("auto_playlist/liked") },
                    )
                }
            }
        }

        if (showSystemPlaylists && (weeklyMost != null || monthlyMost != null)) {
            item("weekly_monthly") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (weeklyMost != null) {
                        LunaraPlaylistCard(
                            title = weeklyMost.title,
                            subtitle = "${weeklyMost.songCount} $songsWord",
                            thumbnails = weeklyMost.thumbnails.take(4),
                            seedColor = Color(0xFFA88BEB),
                            aspectRatio = BOX_RATIO,
                            onClick = { navController.navigate("local_playlist/${weeklyMost.id}") },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (monthlyMost != null) {
                        LunaraPlaylistCard(
                            title = monthlyMost.title,
                            subtitle = "${monthlyMost.songCount} $songsWord",
                            thumbnails = monthlyMost.thumbnails.take(4),
                            seedColor = Color(0xFF7AD7F0),
                            aspectRatio = BOX_RATIO,
                            onClick = { navController.navigate("local_playlist/${monthlyMost.id}") },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (showSystemPlaylists && showTop) {
            item("top") {
                LongPad {
                    LunaraPlaylistCard(
                        title = stringResource(R.string.your_top_50),
                        subtitle = "Most played tracks",
                        thumbnails = topThumbs,
                        seedColor = Color(0xFF1E3264),
                        aspectRatio = LONG_RATIO,
                        iconRes = R.drawable.trending_up,
                        onClick = { navController.navigate("top_playlist/50") },
                    )
                }
            }
        }

        if (showSystemPlaylists || showDownloadedOnly) {
            if (showCached && showDownloaded && !showDownloadedOnly) {
                item("cached_downloaded") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LunaraPlaylistCard(
                            title = stringResource(R.string.cached_playlist),
                            subtitle = "Ready offline",
                            thumbnails = cachedThumbs,
                            seedColor = Color(0xFF282828),
                            aspectRatio = BOX_RATIO,
                            iconRes = R.drawable.cached,
                            onClick = { navController.navigate("cache_playlist/cached") },
                            modifier = Modifier.weight(1f),
                        )
                        LunaraPlaylistCard(
                            title = stringResource(R.string.offline),
                            subtitle = "Downloaded",
                            thumbnails = downloadedThumbs,
                            seedColor = Color(0xFF148A08),
                            aspectRatio = BOX_RATIO,
                            iconRes = R.drawable.download,
                            onClick = { navController.navigate("auto_playlist/downloaded") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (showCached && !showDownloadedOnly) {
                item("cached") {
                    LongPad {
                        LunaraPlaylistCard(
                            title = stringResource(R.string.cached_playlist),
                            subtitle = "Ready offline",
                            thumbnails = cachedThumbs,
                            seedColor = Color(0xFF282828),
                            aspectRatio = LONG_RATIO,
                            iconRes = R.drawable.cached,
                            onClick = { navController.navigate("cache_playlist/cached") },
                        )
                    }
                }
            } else if (showDownloaded || showDownloadedOnly) {
                item("downloaded") {
                    LongPad {
                        LunaraPlaylistCard(
                            title = stringResource(R.string.offline),
                            subtitle = "Downloaded songs",
                            thumbnails = downloadedThumbs,
                            seedColor = Color(0xFF148A08),
                            aspectRatio = LONG_RATIO,
                            iconRes = R.drawable.download,
                            onClick = { navController.navigate("auto_playlist/downloaded") },
                        )
                    }
                }
            }
        }

        if ((showSystemPlaylists || showDownloadedOnly) && localCount == 0) {
            item("local-invite") {
                LongPad {
                    LunaraPlaylistCard(
                        title = stringResource(R.string.local_music),
                        subtitle = stringResource(R.string.local_music_scan),
                        thumbnails = emptyList(),
                        seedColor = Color(0xFF282828),
                        aspectRatio = LONG_RATIO,
                        iconRes = R.drawable.library_music,
                        onClick = {
                            if (localGranted) {
                                scope.launch(Dispatchers.IO) {
                                    runCatching { LocalMusic(context, database).scan(localMusicFolders) }
                                }
                            } else {
                                audioPermission.launch(LocalMusic.permission)
                            }
                        },
                    )
                }
            }
        }

        if ((showSystemPlaylists || showDownloadedOnly) && localCount > 0) {
            item("local") {
                LongPad {
                    LunaraPlaylistCard(
                        title = stringResource(R.string.local_music),
                        subtitle = "$localCount $songsWord",
                        thumbnails = localThumbs,
                        seedColor = Color(0xFF282828),
                        aspectRatio = LONG_RATIO,
                        iconRes = R.drawable.library_music,
                        onClick = { navController.navigate("auto_playlist/local") },
                    )
                }
            }
        }

        if (showSystemPlaylists && showUploaded) {
            item("uploaded") {
                LongPad {
                    LunaraPlaylistCard(
                        title = stringResource(R.string.uploaded_playlist),
                        subtitle = "Cloud uploads",
                        thumbnails = uploadedThumbs,
                        seedColor = Color(0xFF8D67AB),
                        aspectRatio = LONG_RATIO,
                        iconRes = R.drawable.upload,
                        onClick = { navController.navigate("auto_playlist/uploaded") },
                    )
                }
            }
        }

        // ---- Created by you ----
        if ((selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.PLAYLISTS) && userPlaylists.isNotEmpty()) {
            item("cby_head") {
                LunaraSectionHeader(stringResource(R.string.created_by_you))
            }
            itemsIndexed(userPlaylists.chunked(2), key = { _, row -> "cby_${row.first().id}" }) { rowIndex, rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEachIndexed { j, pl ->
                        LunaraPlaylistCard(
                            title = pl.title,
                            subtitle = "${pl.songCount} $songsWord",
                            thumbnails = pl.thumbnails.take(4),
                            seedColor = LunaraPlaylistPalette[(rowIndex * 2 + j) % LunaraPlaylistPalette.size],
                            aspectRatio = USER_RATIO,
                            onClick = { navController.navigate("local_playlist/${pl.id}") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ---- Artists you liked ----
        if ((selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.ARTISTS) && artists.isNotEmpty()) {
            item("art_head") {
                LunaraSectionHeader(stringResource(R.string.artists_you_liked))
            }
            item("art_rail") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(artists, key = { it.id }) { artist ->
                        LunaraMusicCard(
                            title = artist.title,
                            subtitle = if (artist.songCount > 0) "${artist.songCount} $songsWord" else "Artist",
                            thumbnailUrl = artist.thumbnailUrl,
                            isCircular = true,
                            fallbackIcon = R.drawable.artist,
                            onClick = { navController.navigate("artist/${artist.id}") },
                        )
                    }
                }
            }
        }

        item("bottom") { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun LongPad(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        content()
    }
}
