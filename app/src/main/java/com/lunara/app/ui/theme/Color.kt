package com.lunara.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Raw brand shades.
 *
 * These stay plain values on purpose: the palettes below (and any drawing that happens outside of
 * composition) can always read them. Screens should use the theme-aware tokens further down.
 */
private val Ink = Color(0xFF0B0D0F)
private val InkSurface = Color(0xFF121519)
private val InkSurfaceElevated = Color(0xFF191D22)
private val InkSurfaceHigh = Color(0xFF22272E)

private val Snow = Color(0xFFF5F7F8)
private val Ash = Color(0xFFA8AFB7)
private val Smoke = Color(0xFF6E7681)

private val Teal = Color(0xFF35D6C2)          // Electric Teal / Mint-cyan
private val TealDeep = Color(0xFF2AB3A2)
private val Lavender = Color(0xFF8B7CFF)      // Supporting Lavender / Purple

private val Rose = Color(0xFFFF5252)
private val Leaf = Color(0xFF4CAF50)
private val InkBorder = Color(0xFF262C34)

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
    background = Ink,
    surface = InkSurface,
    surfaceElevated = InkSurfaceElevated,
    surfaceHigh = InkSurfaceHigh,
    textPrimary = Snow,
    textSecondary = Ash,
    textMuted = Smoke,
    accent = Teal,
    accentVariant = TealDeep,
    accentSecondary = Lavender,
    error = Rose,
    success = Leaf,
    border = InkBorder,
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

/*
 * Theme-aware brand tokens.
 *
 * Every screen keeps writing `color = LunaraTextPrimary` exactly as before; the difference is that
 * these now resolve against the palette [LunaraTheme] provides, so one switch flips the whole app
 * between light and dark without a conditional anywhere in the UI.
 */

val LunaraBackground: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.background

val LunaraSurface: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.surface

val LunaraSurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.surfaceElevated

val LunaraSurfaceHigh: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.surfaceHigh

val LunaraTextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.textPrimary

val LunaraTextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.textSecondary

val LunaraTextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.textMuted

val LunaraAccent: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.accent

val LunaraAccentVariant: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.accentVariant

val LunaraAccentSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.accentSecondary

val LunaraError: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.error

val LunaraSuccess: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.success

val LunaraBorder: Color
    @Composable @ReadOnlyComposable get() = LocalLunaraPalette.current.border
