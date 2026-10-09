/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist, Spotify-inspired splash screen:
 * Pitch-black background, iconic green emblem, bold crisp typography, and instant smooth transition.
 * No emojis, no sparkles, no gradients.
 */

package com.lunara.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.app.R
import com.lunara.app.ui.theme.SpotifyBlack
import com.lunara.app.ui.theme.SpotifyElevatedSurface
import com.lunara.app.ui.theme.SpotifyGreen
import kotlinx.coroutines.delay

@Composable
fun LunaraSplash(
    visible: Boolean,
    onIntroFinished: () -> Unit = {},
) {
    var mounted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mounted = true
        delay(400)
        onIntroFinished()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(animationSpec = tween(durationMillis = 250)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpotifyBlack),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Minimal icon container
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SpotifyElevatedSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.lunara_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(52.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )

                Spacer(Modifier.height(36.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = SpotifyGreen,
                    strokeWidth = 2.5.dp,
                )
            }
        }
    }
}
