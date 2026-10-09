/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist Spotify-Inspired Loader:
 * Lightweight, hardware-accelerated CircularProgressIndicator.
 * Pure Spotify green accent, no canvas math or sparkling stars. Ultra smooth 60/120fps.
 */

package com.lunara.app.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunara.app.ui.theme.SpotifyCardSurface
import com.lunara.app.ui.theme.SpotifyGreen

@Composable
fun LunaraLoader(
    modifier: Modifier = Modifier.size(36.dp),
    strokeWidth: Dp = 3.dp,
    color: Color = SpotifyGreen,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = SpotifyCardSurface,
        strokeWidth = strokeWidth,
    )
}
