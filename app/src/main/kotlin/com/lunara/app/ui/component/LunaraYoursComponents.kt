/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist Spotify-Inspired Components:
 * Flat surfaces, crisp typography, clean Spotify green accents.
 * No emojis, no sparkles, no gradients. Fast and smooth 60/120fps.
 */

package com.lunara.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lunara.app.R
import com.lunara.app.ui.theme.SpotifyCardSurface
import com.lunara.app.ui.theme.SpotifyElevatedSurface
import com.lunara.app.ui.theme.SpotifyGreen
import com.lunara.app.ui.theme.SpotifyTextMuted
import com.lunara.app.ui.theme.SpotifyTextPrimary
import com.lunara.app.ui.theme.SpotifyTextSecondary

/**
 * Minimalist Spotify section header: bold title with clean text action.
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
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (onSeeMore != null) {
            Text(
                text = stringResource(R.string.see_more),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyTextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onSeeMore)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Minimalist Spotify-style 144dp artwork card.
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
    val shape = if (isCircular) CircleShape else RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .width(144.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(shape)
                .background(SpotifyCardSurface),
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
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = SpotifyTextPrimary,
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
                color = SpotifyTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isCircular) androidx.compose.ui.text.style.TextAlign.Center else null,
                modifier = if (isCircular) Modifier.fillMaxWidth() else Modifier,
            )
        }
    }
}

/**
 * Minimalist flat artwork card (replaces old gradient card).
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
    val cardShape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(cardShape)
            .background(SpotifyCardSurface)
            .clickable(onClick = onClick),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Solid dark overlay at bottom for readable text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.72f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = SpotifyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (iconRes != null && thumbnailUrl == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/** Minimalist Spotify solid colors for library cards */
val LunaraPlaylistPalette = listOf(
    Color(0xFF5138AC), // Spotify Liked Purple
    Color(0xFF1E3264), // Deep Blue
    Color(0xFF282828), // Dark Neutral
    Color(0xFF148A08), // Forest Green
    Color(0xFF8D67AB), // Muted Purple
    Color(0xFFBA68C8), // Violet
)

/**
 * Minimalist Spotify-style wide playlist tile.
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
    val cardShape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(cardShape)
            .background(SpotifyCardSurface)
            .clickable(onClick = onClick),
    ) {
        if (hasArt) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.48f),
            ) {
                PlaylistArtwork(thumbnails)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(if (hasArt) 0.54f else 1f),
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = SpotifyTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (iconRes != null && !hasArt) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SpotifyElevatedSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
        else -> Box(Modifier.fillMaxSize().background(SpotifyElevatedSurface))
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

@Composable
fun LunaraCategoryGrid(
    categories: List<LunaraCategory>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    LunaraCategoryTile(
                        category = category,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
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
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(category.color)
            .clickable(onClick = category.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                painter = painterResource(category.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = category.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Minimalist Spotify pill filter chips: solid dark pills, SpotifyGreen when selected.
 * No emojis or gradients.
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
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (selected) SpotifyGreen else SpotifyCardSurface)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.Black else SpotifyTextPrimary,
                )
            }
        }
    }
}
