package com.lunara.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Player : Screen("player")
    data object Lyrics : Screen("lyrics")
    data object Queue : Screen("queue")
    data object Downloads : Screen("downloads")
    data object Settings : Screen("settings")
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
}
