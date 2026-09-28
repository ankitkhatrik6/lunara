package com.lunara.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LunaraAccent,
    onPrimary = LunaraBackground,
    primaryContainer = LunaraSurfaceElevated,
    onPrimaryContainer = LunaraAccent,
    secondary = LunaraAccentSecondary,
    onSecondary = LunaraBackground,
    background = LunaraBackground,
    onBackground = LunaraTextPrimary,
    surface = LunaraSurface,
    onSurface = LunaraTextPrimary,
    surfaceVariant = LunaraSurfaceElevated,
    onSurfaceVariant = LunaraTextSecondary,
    outline = LunaraBorder,
    error = LunaraError
)

@Composable
fun LunaraTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = LunaraBackground.toArgb()
                window.navigationBarColor = LunaraBackground.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = LunaraTypography,
        shapes = LunaraShapes,
        content = content
    )
}
