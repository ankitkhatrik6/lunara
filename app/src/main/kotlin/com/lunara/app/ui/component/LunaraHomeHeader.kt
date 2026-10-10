/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist Spotify-Inspired Home Header:
 * Left: App logo + Lunara wordmark.
 * Right: Settings icon button.
 * Row 2: Clean filter pills ("All", "Music" - no podcasts).
 * Row 3: 2-column quick access grid with adaptive light & dark mode surfaces.
 */

package com.lunara.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lunara.app.LocalNavController
import com.lunara.app.R
import com.lunara.app.ui.theme.SpotifyGreen
import com.lunara.app.ui.theme.SpotifyLikedSongsPurple

@Composable
fun LunaraHomeHeader(
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onForYouClick: (() -> Unit)? = null,
    forYouArt: String? = null,
    onSpeedDialClick: (() -> Unit)? = null,
    speedDialArt: String? = null,
) {
    val navController = LocalNavController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        // Top bar: Left has App Logo + Title; Right has Settings icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            // Left: App Logo + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.lunara_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.3).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }

            // Right: Settings icon button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Row 2: Quick Access 2-Column Grid (Spotify Home top cards).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Card 1: Liked Songs
                QuickAccessCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.liked_songs),
                    iconRes = R.drawable.favorite,
                    iconTint = Color.White,
                    badgeColor = SpotifyLikedSongsPurple,
                    onClick = { navController.navigate("auto_playlist/liked") },
                )

                // Card 2: For You / Mixes
                QuickAccessCard(
                    modifier = Modifier.weight(1f),
                    title = "For You",
                    imageUrl = forYouArt,
                    iconRes = R.drawable.music_note,
                    iconTint = Color.Black,
                    badgeColor = SpotifyGreen,
                    onClick = onForYouClick ?: {},
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Card 3: Speed Dial
                QuickAccessCard(
                    modifier = Modifier.weight(1f),
                    title = "Speed Dial",
                    imageUrl = speedDialArt,
                    iconRes = R.drawable.radio,
                    iconTint = Color.White,
                    badgeColor = Color(0xFF282828),
                    onClick = onSpeedDialClick ?: {},
                )

                // Card 4: History
                QuickAccessCard(
                    modifier = Modifier.weight(1f),
                    title = "History",
                    iconRes = R.drawable.history,
                    iconTint = Color.White,
                    badgeColor = Color(0xFF1E3264),
                    onClick = { navController.navigate("history") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    @DrawableRes iconRes: Int? = null,
    iconTint: Color = Color.White,
    badgeColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp),
            )
        } else if (iconRes != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
    }
}
