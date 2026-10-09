/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist Spotify-Inspired Design System:
 * Flat surfaces, high-contrast typography, and Spotify signature green accents.
 * Fully theme-aware: dark and light schemes with proper contrast in both.
 * No gradients, no emojis, no sparkle effects. Ultra fast and smooth.
 */

package com.lunara.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ============================================================================
// Raw palette (theme-independent). Backs the color-scheme builders, the
// pure-black override and the gradient fallback, all of which run OUTSIDE
// @Composable scope. UI code must use the theme-aware Spotify* accessors below
// rather than these raw constants so dark and light themes both render right.
// ============================================================================
private val PaletteGreen = Color(0xFF1ED760)
private val PaletteBlack = Color(0xFF121212)
private val PaletteDeepBlack = Color(0xFF000000)
private val PaletteElevatedSurface = Color(0xFF181818)
private val PaletteCardSurface = Color(0xFF242424)
private val PalettePillSurface = Color(0xFF2A2A2A)
private val PaletteTextSecondary = Color(0xFFB3B3B3)
private val PaletteDivider = Color(0xFF282828)

// ---------------------------------------------------------------------------
// Brand accents: constant across both themes (Spotify green is green in light
// and dark alike). Safe to reference from any scope, composable or not.
// ---------------------------------------------------------------------------
val SpotifyGreen = PaletteGreen
val SpotifyGreenHover = Color(0xFF1FDF64)
val SpotifyDeepBlack = PaletteDeepBlack
val SpotifyLikedSongsPurple = Color(0xFF5138AC)

// ---------------------------------------------------------------------------
// Theme-aware surface / text tokens. These resolve against the active
// ColorScheme, which is what makes the shared UI render correctly in light
// mode instead of painting dark cards with low-contrast grey text. Every call
// site is inside @Composable scope, which these getters require (the same
// constraint the pre-rewrite code had when it read MaterialTheme.colorScheme).
// ---------------------------------------------------------------------------
val SpotifyBlack: Color
    @Composable get() = MaterialTheme.colorScheme.background

val SpotifyElevatedSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

val SpotifyCardSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

val SpotifyCardHover: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

val SpotifyPillSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

val SpotifyTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

val SpotifyTextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val SpotifyTextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val SpotifyDivider: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

// Theme tokens referenced across the app
val DefaultThemeColor = SpotifyGreen
val LunaraThemeColor = SpotifyGreen
val LunaraGradientEnd = SpotifyGreen
val LunaraMintAccent = SpotifyGreen
val LunaraPeachAccent = SpotifyGreen

val LunaraBlackSurface = PaletteElevatedSurface
val LunaraBlackSurfaceHigh = PaletteCardSurface

val SpotifyDarkColorScheme = darkColorScheme(
    primary = PaletteGreen,
    onPrimary = Color(0xFF000000),
    primaryContainer = PaletteGreen.copy(alpha = 0.2f),
    onPrimaryContainer = PaletteGreen,
    inversePrimary = PaletteGreen,
    secondary = PaletteGreen,
    onSecondary = Color(0xFF000000),
    secondaryContainer = PaletteCardSurface,
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = PaletteGreen,
    onTertiary = Color(0xFF000000),
    background = PaletteBlack,
    onBackground = Color(0xFFFFFFFF),
    surface = PaletteBlack,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = PaletteCardSurface,
    onSurfaceVariant = PaletteTextSecondary,
    surfaceTint = PaletteGreen,
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = PaletteBlack,
    error = Color(0xFFE91429),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF383838),
    outlineVariant = PaletteDivider,
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = PaletteBlack,
    surfaceContainer = PaletteElevatedSurface,
    surfaceContainerHigh = PaletteCardSurface,
    surfaceContainerHighest = PalettePillSurface,
    surfaceBright = Color(0xFF2E2E2E),
    surfaceDim = PaletteBlack,
)

val SpotifyLightColorScheme = lightColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color(0xFF000000),
    primaryContainer = SpotifyGreen.copy(alpha = 0.15f),
    onPrimaryContainer = Color(0xFF0A5826),
    secondary = SpotifyGreen,
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFEAEAEA),
    onSecondaryContainer = Color(0xFF121212),
    tertiary = SpotifyGreen,
    onTertiary = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF535353),
    surfaceTint = SpotifyGreen,
    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFE91429),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD4D4D4),
    outlineVariant = Color(0xFFE8E8E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFDFDFDF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE8E8E8),
)

@Composable
fun LunaraTheme(
    darkTheme: Boolean = true,
    pureBlack: Boolean = true,
    themeColor: Color = SpotifyGreen,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(darkTheme, pureBlack, themeColor) {
        if (darkTheme) {
            if (pureBlack) {
                SpotifyDarkColorScheme.copy(
                    background = Color(0xFF000000),
                    surface = Color(0xFF000000),
                    surfaceContainerLow = Color(0xFF050505),
                    surfaceContainer = Color(0xFF121212),
                    surfaceContainerHigh = PaletteCardSurface,
                )
            } else {
                SpotifyDarkColorScheme
            }
        } else {
            SpotifyLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

/** Instant non-blocking color extractor - lightweight fallback for performance */
fun Bitmap.extractThemeColor(): Color = SpotifyGreen

fun Bitmap.extractGradientColors(): List<Color> = listOf(PaletteCardSurface, PaletteBlack)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceDim = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = PaletteElevatedSurface,
        surfaceContainerHigh = PaletteCardSurface,
        surfaceContainerHighest = PalettePillSurface,
        surfaceBright = Color(0xFF2E2E2E),
        surfaceVariant = PaletteCardSurface,
        primary = PaletteGreen,
        onPrimary = Color(0xFF000000),
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
