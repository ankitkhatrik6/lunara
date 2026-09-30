package com.lunara.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Primary Dark Theme Colors
val LunaraBackground = Color(0xFF0B0D0F)
val LunaraSurface = Color(0xFF121519)
val LunaraSurfaceElevated = Color(0xFF191D22)
val LunaraSurfaceHigh = Color(0xFF22272E)

// Text Colors
val LunaraTextPrimary = Color(0xFFF5F7F8)
val LunaraTextSecondary = Color(0xFFA8AFB7)
val LunaraTextMuted = Color(0xFF6E7681)

// Accent Colors
val LunaraAccent = Color(0xFF35D6C2)         // Electric Teal / Mint-cyan
val LunaraAccentVariant = Color(0xFF2AB3A2)
val LunaraAccentSecondary = Color(0xFF8B7CFF) // Supporting Lavender / Purple

// Status & Indicators
val LunaraError = Color(0xFFFF5252)
val LunaraSuccess = Color(0xFF4CAF50)
val LunaraBorder = Color(0xFF262C34)

/**
 * The complete set of brand colours the UI draws with.
 *
 * Screens never hard-code a shade: they read the palette that [LunaraTheme] puts into
 * [LocalLunaraPalette], which is what makes light/dark switching instant and free of conditionals
 * at the call sites.
 */
data class LunaraPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentVariant: Color,
    val accentSecondary: Color,
    val error: Color,
    val success: Color,
    val border: Color,
    val isDark: Boolean
)

/** Default look: near-black canvas with the electric-teal accent. */
val LunaraDarkPalette = LunaraPalette(
    background = LunaraBackground,
    surface = LunaraSurface,
    surfaceElevated = LunaraSurfaceElevated,
    surfaceHigh = LunaraSurfaceHigh,
    textPrimary = LunaraTextPrimary,
    textSecondary = LunaraTextSecondary,
    textMuted = LunaraTextMuted,
    accent = LunaraAccent,
    accentVariant = LunaraAccentVariant,
    accentSecondary = LunaraAccentSecondary,
    error = LunaraError,
    success = LunaraSuccess,
    border = LunaraBorder,
    isDark = true
)

/**
 * Daylight look: paper-white canvas, deepened accent shades so contrast stays legible
 * (the dark-theme teal is too pale to sit on white).
 */
val LunaraLightPalette = LunaraPalette(
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF0F2F5),
    surfaceHigh = Color(0xFFE5E9ED),
    textPrimary = Color(0xFF101418),
    textSecondary = Color(0xFF5A636E),
    textMuted = Color(0xFF8B939E),
    accent = Color(0xFF0FA795),
    accentVariant = Color(0xFF0B8B7C),
    accentSecondary = Color(0xFF6A5AE0),
    error = Color(0xFFD93025),
    success = Color(0xFF2E7D32),
    border = Color(0xFFDDE2E7),
    isDark = false
)

/**
 * Palette for the current theme. [LunaraTheme] provides it; screens read the brand tokens
 * (see `LocalLunaraPalette.current`), so no screen needs to know which mode is active.
 */
val LocalLunaraPalette = staticCompositionLocalOf { LunaraDarkPalette }
