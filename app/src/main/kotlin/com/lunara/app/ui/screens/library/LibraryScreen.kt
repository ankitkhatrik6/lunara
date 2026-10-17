/**
 * Ankit Khatri KC (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.ui.screens.library

import androidx.compose.runtime.Composable
import com.lunara.app.LocalNavController

@Composable
fun LibraryScreen() {
    val navController = LocalNavController.current
    LunaraLibraryHome(navController)
}
