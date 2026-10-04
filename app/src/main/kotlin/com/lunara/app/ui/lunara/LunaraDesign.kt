/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * The Lunara design system.
 *
 * Everything here is a *decision written down once*: how far apart things sit,
 * how round a corner is, how long a change takes. Screens then read these
 * tokens instead of inventing their own numbers, which is the only way a set of
 * screens ends up looking like one product rather than several.
 *
 * The scales are deliberately short. A long scale is a scale nobody uses
 * consistently — five corner radii and eight steps of space cover every case
 * this app has, and there being only five means a new screen has no choice but
 * to agree with the old ones.
 *
 * The motion values are the part that makes it feel expensive. Nothing moves
 * linearly, nothing moves faster than a blink, and a press always answers back
 * within a frame or two — that immediate feedback is most of what "smooth"
 * means to a hand.
 */

package com.lunara.app.ui.lunara

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space between things.
 *
 * [screenEdge] is the one that matters most: every screen's content starts this
 * far from the glass, so nothing ever looks like it drifted toward an edge.
 */
object LunaraSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val huge: Dp = 32.dp
    val giant: Dp = 40.dp
    val vast: Dp = 56.dp

    /** Distance from a screen's content to the side of the phone. */
    val screenEdge: Dp = 20.dp

    /** Between two stacked shelves of artwork. */
    val shelfGap: Dp = 28.dp
}

/**
 * How round a corner is.
 *
 * Artwork uses [md] or [lg] and nothing else. Artwork with a single radius
 * across the whole app reads as deliberate; artwork with a different radius per
 * screen reads as unfinished.
 */
object LunaraRadius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp

    /** Fully round: for a pill, a chip, a round control. */
    val pill: Dp = 999.dp
}

/**
 * How far something sits off the page.
 *
 * Short on purpose. Deep shadows are the fastest way to make an interface look
 * dated; these are steps of light, not theatrical drops.
 */
object LunaraElevation {
    val none: Dp = 0.dp
    val raised: Dp = 2.dp
    val floating: Dp = 6.dp
    val overlay: Dp = 12.dp
}

/**
 * How long a change takes, and what shape its curve is.
 *
 * [standard] is suspiciously close to the curve the platform itself uses. That
 * is the point: a custom curve nobody has seen before draws attention to the
 * animation, and the animation should be the last thing anyone notices.
 */
object LunaraMotion {
    /** For colour and state changes that must not be seen to happen. */
    const val QUICK = 140

    /** The default: a screen settling, a card expanding. */
    const val BASE = 240

    /** For something crossing the screen — a sheet, a full-screen player. */
    const val SLOW = 420

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For something arriving that must not overshoot: a fade, a sheet. */
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** For something leaving: it should get out of the way. */
    val accelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun <T> quick(): AnimationSpec<T> = tween(durationMillis = QUICK, easing = standard)

    fun <T> base(): AnimationSpec<T> = tween(durationMillis = BASE, easing = standard)

    fun <T> slow(): AnimationSpec<T> = tween(durationMillis = SLOW, easing = decelerate)

    /**
     * A press answering back.
     *
     * Stiff and damped: a control that springs past its resting size and comes
     * back looks like rubber, and rubber is the opposite of expensive.
     */
    fun <T> press(): AnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 900f)
}

/** Sizes that have to agree across screens so nothing jumps when you move. */
object LunaraSizes {
    /** A shelf card: the artwork is this wide, and its title sits under it. */
    val shelfCard: Dp = 148.dp

    /** A quick tile: the small square on the home grid. */
    val quickTile: Dp = 64.dp

    /** How tall one row of the quick grid is. */
    val quickTileRow: Dp = 56.dp

    /** The smallest a tap target is allowed to be, to a thumb. */
    val touchTarget: Dp = 48.dp

    /** The floating mini-player's artwork. */
    val miniArtwork: Dp = 44.dp
}
