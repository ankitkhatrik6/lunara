/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * The pieces every Lunara screen is built from.
 *
 * These are deliberately few and deliberately dumb: a card does not know what
 * it is showing, a tile does not know what it starts. A screen decides that.
 * The alternative - a card per kind of content - is how a set of screens ends
 * up with four subtly different corner radii and three different gaps, which
 * reads as sloppy however good each screen is on its own.
 */

package com.lunara.app.ui.lunara

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A control that answers a finger immediately.
 *
 * The scale is small - two or three percent - because a card that visibly
 * shrinks looks like a button, and this is a piece of album art. The point is
 * that the surface reacts at all before the destination has loaded.
 */
@Composable
fun Modifier.lunaraPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = LunaraMotion.press(),
        label = "lunaraPressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The glow behind a screen's top, taken from whatever is playing.
 *
 * This is the one place colour is allowed to be loud. It fades to nothing well
 * before the content starts, so it colours the page without ever competing with
 * what is written on it.
 */
@Composable
fun LunaraAmbient(
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 320.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.38f),
                        accent.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/** Darkens the foot of artwork so a title can sit on it and stay readable. */
@Composable
fun LunaraScrim(
    modifier: Modifier = Modifier,
    strength: Float = 1f,
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.30f * strength),
                    1.00f to Color.Black.copy(alpha = 0.72f * strength),
                ),
            ),
        ),
    )
}

/**
 * A translucent surface that reads as glass rather than as a grey box.
 *
 * Translucency plus a hairline edge is what makes a surface sit above what it
 * covers. Opaque cards stacked on an opaque page look like a spreadsheet.
 */
@Composable
fun LunaraGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LunaraRadius.lg),
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceContainer.copy(alpha = 0.74f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.32f), shape),
        content = content,
    )
}


/**
 * A shelf's heading, with an optional way to see more.
 *
 * The action is a word rather than an arrow or a "see all", because a word is
 * unambiguous and costs two characters of the heading line instead of a whole
 * row of the page.
 */
@Composable
fun LunaraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LunaraSpacing.screenEdge),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(LunaraRadius.pill))
                    .clickable(onClick = onAction)
                    .padding(horizontal = LunaraSpacing.md, vertical = LunaraSpacing.sm),
            )
        }
    }
}

/**
 * One card in a shelf: square artwork, a title, and a line under it.
 *
 * The artwork is the control and the words are a caption - not the other way
 * round. That is why the whole column is pressable rather than just the words.
 */
@Composable
fun LunaraShelfCard(
    title: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = LunaraSizes.shelfCard,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(width)
            .lunaraPressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(LunaraRadius.lg))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LunaraAmbient(
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(LunaraSpacing.sm))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The small, wide tile: artwork on the left, a name on the right.
 *
 * Two of these fit side by side, which is what makes the top of a home page
 * scannable - six things you were just listening to, read in one glance,
 * instead of one enormous hero and a scroll.
 */
@Composable
fun LunaraQuickTile(
    title: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(LunaraSizes.quickTileRow)
            .clip(RoundedCornerShape(LunaraRadius.sm))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .lunaraPressScale(interaction, pressedScale = 0.98f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(LunaraSizes.quickTileRow)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = LunaraSpacing.md)
                .weight(1f),
        )
    }
}
