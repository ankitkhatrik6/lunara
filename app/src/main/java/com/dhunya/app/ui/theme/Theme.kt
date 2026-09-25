package com.dhunya.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DhunyaAccent,
    onPrimary = DhunyaBackground,
    primaryContainer = DhunyaSurfaceElevated,
    onPrimaryContainer = DhunyaAccent,
    secondary = DhunyaAccentSecondary,
    onSecondary = DhunyaBackground,
    background = DhunyaBackground,
    onBackground = DhunyaTextPrimary,
    surface = DhunyaSurface,
    onSurface = DhunyaTextPrimary,
    surfaceVariant = DhunyaSurfaceElevated,
    onSurfaceVariant = DhunyaTextSecondary,
    outline = DhunyaBorder,
    error = DhunyaError
)

@Composable
fun DhunyaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DhunyaBackground.toArgb()
                window.navigationBarColor = DhunyaBackground.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = DhunyaTypography,
        shapes = DhunyaShapes,
        content = content
    )
}
