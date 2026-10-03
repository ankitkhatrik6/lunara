/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Lunara-styled home rail components for the "Yours" tab, ported from the
 * Flutter LunaraPlayer home screen (section headers, music cards, gradient
 * thumbnail cards, category grid). Colors follow the app's dynamic theme.
 */

package com.lunara.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lunara.app.ui.theme.LunaraGradientEnd
import com.lunara.app.ui.theme.LunaraThemeColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lunara.app.R

/**
 * Section header: 22sp bold title with an optional amber "See More" action.
 */
@Composable
fun LunaraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onSeeMore != null) {
            Text(
                text = stringResource(R.string.see_more),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeMore)
                    .padding(start = 12.dp),
            )
        }
    }
}

/**
 * Square (or circular) 140dp artwork card with title + subtitle, used for the
 * Recently Played / Recommended / Favorite Artists rails.
 */
@Composable
fun LunaraMusicCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCircular: Boolean = false,
    @DrawableRes fallbackIcon: Int = R.drawable.music_note,
) {
    val shape = if (isCircular) CircleShape else RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(fallbackIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isCircular) androidx.compose.ui.text.style.TextAlign.Center else null,
            modifier = if (isCircular) Modifier.fillMaxWidth() else Modifier,
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isCircular) androidx.compose.ui.text.style.TextAlign.Center else null,
                modifier = if (isCircular) Modifier.fillMaxWidth() else Modifier,
            )
        }
    }
}

/**
 * 160dp gradient thumbnail card (ported from LunaraPlayer's MoodMusicCard):
 * artwork fades top→bottom into [seedColor]; title/subtitle sit bottom-left,
 * an optional glyph bottom-right. When [thumbnailUrl] is null it renders as a
 * solid gradient tile (used for mood cards, which have no artwork).
 */
@Composable
fun LunaraGradientCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    seedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    height: Dp = 160.dp,
    @DrawableRes iconRes: Int? = null,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(seedColor)
            .clickable(onClick = onClick),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.3f to Color.Transparent,
                            0.6f to seedColor.copy(alpha = 0.75f),
                            1.0f to seedColor.copy(alpha = 0.95f),
                        ),
                    ),
            )
        } else {
            // No artwork (mood cards): a lighter tint up top fading to the full
            // seed colour at the bottom, so the card still reads top-light → colour.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(lerp(seedColor, Color.White, 0.24f), seedColor),
                        ),
                    ),
            )
        }

        // Foreground text sits on the darker seed colour; pick a readable ink.
        val onSeed = if (seedColor.luminance() > 0.55f) Color.Black else Color.White
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = onSeed,
                maxLines = 2,
                lineHeight = 19.sp,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = onSeed.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (iconRes != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            ) {
                // Soft drop shadow behind the glyph.
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.30f),
                    modifier = Modifier
                        .offset(x = 1.5.dp, y = 2.dp)
                        .size(36.dp),
                )
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = onSeed,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

/** Lunara palette cycled across playlist cards (LunaraPlayer-style). */
val LunaraPlaylistPalette = listOf(
    Color(0xFFB71C5A), // magenta
    Color(0xFF00838F), // teal
    Color(0xFF283593), // indigo
    Color(0xFF8D6E63), // brown
    Color(0xFF6A1B9A), // purple
    Color(0xFFEF6C00), // deep orange
)

/**
 * Wide playlist card ported from LunaraPlayer: a coloured tile with the artwork
 * (single or 2x2 collage) filling the right half, blended into the card colour
 * by a left→right gradient, title + subtitle on the coloured left, an optional
 * glyph top-right.
 */
@Composable
fun LunaraPlaylistCard(
    title: String,
    subtitle: String,
    thumbnails: List<String>,
    seedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.6f,
    @DrawableRes iconRes: Int? = null,
) {
    val hasArt = thumbnails.any { it.isNotEmpty() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(18.dp))
            .background(seedColor)
            .clickable(onClick = onClick),
    ) {
        if (hasArt) {
            // Artwork on the right half.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f),
            ) {
                PlaylistArtwork(thumbnails)
            }
            // Left→right gradient blends artwork into the card colour.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to seedColor,
                            0.45f to seedColor,
                            0.62f to seedColor.copy(alpha = 0.88f),
                            0.78f to seedColor.copy(alpha = 0.5f),
                            1.0f to seedColor.copy(alpha = 0.05f),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.66f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = if (hasArt) 1f else 0.92f),
                modifier = Modifier
                    .align(if (hasArt) Alignment.TopEnd else Alignment.BottomEnd)
                    .padding(14.dp)
                    .size(if (hasArt) 22.dp else 34.dp),
            )
        }
    }
}

@Composable
private fun PlaylistArtwork(thumbnails: List<String>) {
    val urls = thumbnails.filter { it.isNotEmpty() }
    when {
        urls.size >= 4 -> {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ArtCell(urls[0], Modifier.weight(1f).fillMaxHeight())
                    ArtCell(urls[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ArtCell(urls[2], Modifier.weight(1f).fillMaxHeight())
                    ArtCell(urls[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        urls.isNotEmpty() -> ArtCell(urls[0], Modifier.fillMaxSize())
        else -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
    }
}

@Composable
private fun ArtCell(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** A single browse-category entry for [LunaraCategoryGrid]. */
data class LunaraCategory(
    val label: String,
    @DrawableRes val icon: Int,
    val color: Color,
    val onClick: () -> Unit,
)

/**
 * 3-column gradient category tiles (Songs / Albums / Artists / Playlists /
 * Downloads / Favorites). Rendered as fixed rows so it can live inside a
 * LazyColumn item without a nested scroll container.
 */
@Composable
fun LunaraCategoryGrid(
    categories: List<LunaraCategory>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { category ->
                    LunaraCategoryTile(
                        category = category,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                // Pad the final short row so tiles keep their column width.
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LunaraCategoryTile(
    category: LunaraCategory,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(category.color, category.color.copy(alpha = 0.7f)),
                ),
            )
            .clickable(onClick = category.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(category.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = category.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Horizontal amber pill filter chips (Library / search-style filters). The
 * selected chip fills with the Lunara amber→orange gradient.
 */
@Composable
fun <T> LunaraFilterChips(
    chips: List<Pair<T, String>>,
    currentValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips) { (value, label) ->
            val selected = value == currentValue
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (selected) {
                            Modifier.background(
                                Brush.linearGradient(listOf(LunaraThemeColor, LunaraGradientEnd)),
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        },
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color(0xFF241500) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
