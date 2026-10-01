package com.lunara.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lunara.app.domain.model.ThemeMode

private fun darkColorSchemeFor(palette: LunaraPalette) = darkColorScheme(
    primary = palette.accent,
    onPrimary = palette.background,
    primaryContainer = palette.surfaceElevated,
    onPrimaryContainer = palette.accent,
    secondary = palette.accentSecondary,
    onSecondary = palette.background,
    background = palette.background,
    onBackground = palette.textPrimary,
    surface = palette.surface,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surfaceElevated,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.border,
    error = palette.error
)

private fun lightColorSchemeFor(palette: LunaraPalette) = lightColorScheme(
    primary = palette.accent,
    onPrimary = palette.surface,
    primaryContainer = palette.surfaceHigh,
    onPrimaryContainer = palette.accentVariant,
    secondary = palette.accentSecondary,
    onSecondary = palette.surface,
    background = palette.background,
    onBackground = palette.textPrimary,
    surface = palette.surface,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surfaceElevated,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.border,
    error = palette.error
)

@Composable
fun LunaraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Material You. When on, and only on Android 12+, the palette comes from the wallpaper instead
     * of the Lunara brand shades; every other combination falls back to the brand palette.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // The SDK check is also what satisfies lint for the API-31-only scheme builders below.
    val dynamicScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val palette = dynamicScheme?.let { lunaraPaletteFrom(it, darkTheme) }
        ?: if (darkTheme) LunaraDarkPalette else LunaraLightPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = palette.background.toArgb()
                window.navigationBarColor = palette.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    // Light bars on a light canvas, and vice versa, keeps the clock readable.
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalLunaraPalette provides palette) {
        MaterialTheme(
            // A dynamic scheme is handed over untouched so Material components use the wallpaper's
            // own roles; the brand schemes are built only when there is no dynamic scheme.
            colorScheme = dynamicScheme
                ?: if (darkTheme) darkColorSchemeFor(palette) else lightColorSchemeFor(palette),
            typography = lunaraTypography(
                textPrimary = palette.textPrimary,
                textSecondary = palette.textSecondary,
                textMuted = palette.textMuted
            ),
            shapes = LunaraShapes,
            content = content
        )
    }
}
