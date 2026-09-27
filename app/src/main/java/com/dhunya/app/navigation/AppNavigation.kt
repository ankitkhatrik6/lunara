package com.dhunya.app.navigation

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
import com.dhunya.app.core.state.SongStates
import com.dhunya.app.domain.model.DownloadStatus
import com.dhunya.app.domain.model.Song
import com.dhunya.app.features.downloads.DownloadsScreen
import com.dhunya.app.features.home.HomeScreen
import com.dhunya.app.features.library.LibraryScreen
import com.dhunya.app.features.lyrics.LyricsScreen
import com.dhunya.app.features.player.PlayerScreen
import com.dhunya.app.features.playlist.PlaylistDetailScreen
import com.dhunya.app.features.search.SearchScreen
import com.dhunya.app.features.settings.SettingsScreen
import com.dhunya.app.features.song.SongActionBottomSheet
import com.dhunya.app.player.PlayerManager
import com.dhunya.app.ui.components.MiniPlayerBar
import com.dhunya.app.ui.theme.*

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun DhunyaApp(
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

    Scaffold(
        containerColor = DhunyaBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Column(modifier = Modifier.background(DhunyaBackground)) {
                    if (showMiniPlayer) {
                        MiniPlayerBar(
                            state = playbackState,
                            onBarClick = { navController.navigate(Screen.Player.route) },
                            onPlayPauseClick = { playerManager.togglePlayPause() },
                            onNextClick = { playerManager.playNext() }
                        )
                    }

                    NavigationBar(
                        containerColor = DhunyaSurface,
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
                                    selectedIconColor = DhunyaAccent,
                                    selectedTextColor = DhunyaAccent,
                                    indicatorColor = DhunyaSurfaceElevated,
                                    unselectedIconColor = DhunyaTextSecondary,
                                    unselectedTextColor = DhunyaTextSecondary
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
                    onNavigateToFavorites = { navController.navigate(Screen.Library.route) },
                    onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) },
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
            onToggleFavorite = {
                songStates.toggleFavorite(song)
            },
            onDownload = {
                if (isDownloaded) songStates.removeDownload(song.id) else songStates.download(song)
            }
        )
    }
}
