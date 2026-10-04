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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.lunara.app.ui.theme.LunaraGradientEnd
import com.lunara.app.ui.theme.LunaraThemeColor

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

/** The two colours the product is, swept across the wordmark and nothing else. */
@Composable
fun lunaraBrandBrush(): Brush =
    Brush.linearGradient(listOf(LunaraThemeColor, LunaraGradientEnd))

/**
 * A page's title, and the line under it if there is one.
 *
 * Large and tight - the tracking comes in as the size goes up, which is what
 * makes a word look set rather than merely enlarged.
 */
@Composable
fun LunaraScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = LunaraSpacing.screenEdge,
                end = LunaraSpacing.screenEdge,
                top = LunaraSpacing.md,
            ),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6f).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(LunaraSpacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * A round control.
 *
 * The only button shape in the product. A round control beside a round field
 * beside a pill chip is three round things; three different shapes would be
 * three different products.
 */
@Composable
fun LunaraIconAction(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = LunaraSizes.touchTarget,
    filled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = LunaraAlpha.glass),
                    )
                } else {
                    Modifier
                },
            )
            .lunaraPressScale(interaction, pressedScale = 0.90f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/**
 * The search field.
 *
 * Glass, not a box: it sits on the ambient wash and lets the colour through,
 * so the top of a page reads as one surface with a hole in it rather than a
 * grey rectangle lying on top of one.
 */
@Composable
fun LunaraSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    leadingIconRes: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LunaraRadius.pill)
    val placed = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = placed
            .fillMaxWidth()
            .height(LunaraSizes.field)
            .clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = LunaraAlpha.glassStrong))
            .border(1.dp, scheme.outlineVariant.copy(alpha = LunaraAlpha.hairline), shape)
            .padding(horizontal = LunaraSpacing.lg),
    ) {
        if (leadingIconRes != null) {
            Icon(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(LunaraSpacing.md))
        }

        Box(modifier = Modifier.weight(1f)) {
            // The hint is drawn by hand rather than given to a placeholder slot,
            // so it can carry the same muted alpha every other secondary line
            // uses instead of a colour of its own.
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant.copy(alpha = LunaraAlpha.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = scheme.onSurface,
                    fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (trailing != null) trailing()
    }
}

/**
 * Two or three options as one control.
 *
 * The selection is a fill that fades in rather than a shape that slides, so
 * tapping the far option does not drag a highlight across the one in between -
 * there is nothing to drag, and the eye goes straight to the new answer.
 */
@Composable
fun LunaraSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LunaraRadius.pill)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(LunaraSizes.segmented)
            .clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = LunaraAlpha.glass))
            .padding(LunaraSpacing.xs),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val fill by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = LunaraMotion.base(),
                label = "lunaraSegmentedFill",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(scheme.primary.copy(alpha = 0.16f * fill))
                    .clickable { onSelect(index) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A word you can tap: a recent search, a filter. */
@Composable
fun LunaraChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LunaraRadius.pill)
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(LunaraSizes.chip)
            .clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = LunaraAlpha.glass))
            .border(1.dp, scheme.outlineVariant.copy(alpha = LunaraAlpha.hairline), shape)
            .lunaraPressScale(interaction, pressedScale = 0.95f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = LunaraSpacing.lg),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(LunaraSpacing.sm))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A line of content: artwork, a name, and a line under it.
 *
 * The whole row is the target, not just the words - a thumb aiming at a 52
 * point square should not have to find the eleven points of text inside it.
 */
@Composable
fun LunaraListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    artworkUrl: String? = null,
    roundArtwork: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LunaraRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = LunaraSpacing.sm, vertical = LunaraSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(LunaraSizes.rowArtwork)
                .clip(if (roundArtwork) CircleShape else RoundedCornerShape(LunaraRadius.xs))
                .background(scheme.surfaceContainerHighest),
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

        Spacer(Modifier.width(LunaraSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(LunaraSpacing.sm))
            trailing()
        }
    }
}

/**
 * A tile in the browse grid: a glyph on a wash of colour, and a word under it.
 *
 * The colour is the tile's own rather than the page's, which is what stops a
 * grid of six of them reading as a settings menu.
 */
@Composable
fun LunaraBrowseTile(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(LunaraRadius.md))
            .lunaraPressScale(interaction, pressedScale = 0.96f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(LunaraSizes.browseTile)
                .clip(RoundedCornerShape(LunaraRadius.md))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.30f),
                            accent.copy(alpha = LunaraAlpha.haloFade),
                        ),
                    ),
                ),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(LunaraSpacing.sm))

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Something that is not there yet, said properly instead of left blank. */
@Composable
fun LunaraEmptyState(
    iconRes: Int,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LunaraSpacing.huge, vertical = LunaraSpacing.giant),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.12f)),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(LunaraSpacing.lg))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(LunaraSpacing.sm))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The only divider the app has: a hairline, never a drawn line. */
@Composable
fun LunaraHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = LunaraAlpha.hairline),
            ),
    )
}

/**
 * A placeholder where something is still loading.
 *
 * It breathes rather than shimmers. A highlight travelling across a page draws
 * the eye to the one thing on it that has nothing in it; a slow fade reads as
 * the page being about to be there.
 */
@Composable
fun LunaraSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LunaraRadius.sm),
) {
    val transition = rememberInfiniteTransition(label = "lunaraSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LunaraMotion.standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lunaraSkeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
    )
}
