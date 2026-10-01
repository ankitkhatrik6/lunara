package com.lunara.app.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.lunara.app.core.state.SongStates
import com.lunara.app.domain.model.DownloadStatus
import com.lunara.app.domain.model.Song
import com.lunara.app.features.playlist.PlaylistPickerSheet
import com.lunara.app.features.share.SharePosterEffect
import com.lunara.app.features.share.ShareSongViewModel
import com.lunara.app.features.downloads.DownloadsScreen
import com.lunara.app.features.home.HomeScreen
import com.lunara.app.features.library.LibraryScreen
import com.lunara.app.features.lyrics.LyricsScreen
import com.lunara.app.features.player.PlayerScreen
import com.lunara.app.features.playlist.PlaylistDetailScreen
import com.lunara.app.features.search.SearchScreen
import com.lunara.app.features.settings.SettingsScreen
import com.lunara.app.features.song.SongActionBottomSheet
import com.lunara.app.player.PlayerManager
import com.lunara.app.ui.components.MiniPlayerBar
import com.lunara.app.ui.theme.*

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun LunaraApp(
    playerManager: PlayerManager,
    songStates: SongStates,
    navController: NavHostController = rememberNavController()
) {
    val playbackState by playerManager.playbackState.collectAsState()
    val favoriteIds by songStates.favoriteIds.collectAsState()
    val downloadStates by songStates.downloadStatus.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedActionSong by remember { mutableStateOf<Song?>(null) }
    var playlistTargetSong by remember { mutableStateOf<Song?>(null) }
    var pendingSnackbar by remember { mutableStateOf<String?>(null) }
    val shareViewModel: ShareSongViewModel = hiltViewModel()

    // Renders the poster and opens the system share sheet once it is ready.
    SharePosterEffect(viewModel = shareViewModel)

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home.route, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(Screen.Search.route, "Search", Icons.Filled.Search, Icons.Outlined.Search),
        BottomNavItem(Screen.Library.route, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    )

    val showBottomBar = currentRoute in listOf(Screen.Home.route, Screen.Search.route, Screen.Library.route)
    val hasActiveSong = playbackState.currentSong != null
    val showMiniPlayer = hasActiveSong && showBottomBar

    // Playback failures (unavailable tracks, blocked streams) must never be silent.
    val snackbarHostState = remember { SnackbarHostState() }
    val playbackError = playbackState.errorMessage
    LaunchedEffect(playbackError) {
        if (!playbackError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = playbackError, withDismissAction = true)
        }
    }

    // Playlist confirmations ("Added to Road Trip") reuse the same host.
    LaunchedEffect(pendingSnackbar) {
        pendingSnackbar?.let { message ->
            snackbarHostState.showSnackbar(message = message, withDismissAction = true)
            pendingSnackbar = null
        }
    }

    Scaffold(
        containerColor = LunaraBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Column(modifier = Modifier.background(LunaraBackground)) {
                    if (showMiniPlayer) {
                        MiniPlayerBar(
                            state = playbackState,
                            onBarClick = { navController.navigate(Screen.Player.route) },
                            onPlayPauseClick = { playerManager.togglePlayPause() },
                            onNextClick = { playerManager.playNext() }
                        )
                    }

                    NavigationBar(
                        containerColor = LunaraSurface,
                        tonalElevation = 8.dp
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LunaraAccent,
                                    selectedTextColor = LunaraAccent,
                                    indicatorColor = LunaraSurfaceElevated,
                                    unselectedIconColor = LunaraTextSecondary,
                                    unselectedTextColor = LunaraTextSecondary
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onSongActionClick = { selectedActionSong = it }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onSongActionClick = { selectedActionSong = it }
                )
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                    },
                    onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) },
                    onSongActionClick = { selectedActionSong = it }
                )
            }

            composable(Screen.Player.route) {
                PlayerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLyrics = { navController.navigate(Screen.Lyrics.route) }
                )
            }

            composable(Screen.Lyrics.route) {
                LyricsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSongActionClick = { selectedActionSong = it }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) {
                PlaylistDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSongActionClick = { selectedActionSong = it }
                )
            }
        }
    }

    // Contextual Song Action Bottom Sheet
    selectedActionSong?.let { song ->
        val downloadStatus = downloadStates[song.id]
        val isDownloaded = downloadStatus == DownloadStatus.COMPLETED || song.isDownloaded
        SongActionBottomSheet(
            song = song,
            isFavorite = song.id in favoriteIds,
            isDownloaded = isDownloaded,
            isDownloading = downloadStatus == DownloadStatus.QUEUED ||
                downloadStatus == DownloadStatus.DOWNLOADING,
            onDismiss = { selectedActionSong = null },
            onPlay = {
                playerManager.playSong(song)
            },
            onPlayNext = {
                playerManager.queueManager.addToQueueNext(song)
            },
            onAddToQueue = {
                playerManager.queueManager.addToQueueEnd(song)
            },
            onStartRadio = {
                playerManager.startRadio(song)
            },
            onAddToPlaylist = {
                playlistTargetSong = song
            },
            onShare = {
                shareViewModel.share(song)
            },
            onToggleFavorite = {
                songStates.toggleFavorite(song)
            },
            onDownload = {
                if (isDownloaded) songStates.removeDownload(song.id) else songStates.download(song)
            }
        )
    }

    // "Add to playlist" picker, opened from any song's action sheet.
    playlistTargetSong?.let { song ->
        PlaylistPickerSheet(
            song = song,
            onDismiss = { playlistTargetSong = null },
            onSongAdded = { playlistName ->
                playlistTargetSong = null
                pendingSnackbar = "Added to \"$playlistName\""
            }
        )
    }
}
