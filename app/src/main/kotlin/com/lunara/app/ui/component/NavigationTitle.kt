/**
 * Lunara Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist Spotify-Inspired NavigationTitle.
 * No emojis, no sparkles, no gradients.
 */

package com.lunara.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.app.R
import com.lunara.app.ui.theme.SpotifyGreen
import com.lunara.app.ui.theme.SpotifyTextPrimary
import com.lunara.app.ui.theme.SpotifyTextSecondary

@Composable
fun NavigationTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onPlayAllClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (thumbnail != null) {
            thumbnail.invoke()
        } else {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen),
            )
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            if (label != null) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                    color = SpotifyGreen,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
                color = SpotifyTextPrimary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }

        if (onPlayAllClick != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
                    .clickable(onClick = onPlayAllClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = SpotifyTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
