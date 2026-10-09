/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist, Spotify-inspired Onboarding screen:
 * Built from scratch. Pure dark surface, crisp typography, clean feature highlights,
 * and bold Spotify green action pill. No emojis, sparkles, or gradients.
 */

package com.lunara.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.app.R
import com.lunara.app.ui.theme.SpotifyBlack
import com.lunara.app.ui.theme.SpotifyCardSurface
import com.lunara.app.ui.theme.SpotifyElevatedSurface
import com.lunara.app.ui.theme.SpotifyGreen
import com.lunara.app.ui.theme.SpotifyTextMuted
import com.lunara.app.ui.theme.SpotifyTextPrimary
import com.lunara.app.ui.theme.SpotifyTextSecondary

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
            .padding(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding(),
                start = 24.dp,
                end = 24.dp,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Spacer(Modifier.height(16.dp))

                // Brand header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SpotifyElevatedSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.lunara_logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        color = SpotifyTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(36.dp))

                // Hero title
                Text(
                    text = "Millions of songs.\nFree on Lunara.",
                    color = SpotifyTextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "High-fidelity music streaming, synchronized lyrics, and offline playback built for you.",
                    color = SpotifyTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )

                Spacer(Modifier.height(36.dp))

                // Minimal feature cards (solid dark surfaces, no gradients)
                FeatureRow(
                    icon = R.drawable.music_note,
                    title = "Unlimited Music & Podcasts",
                    description = "Stream millions of songs and explore curated mixes without interruption.",
                )

                Spacer(Modifier.height(18.dp))

                FeatureRow(
                    icon = R.drawable.lyrics,
                    title = "Live Synced Lyrics",
                    description = "Follow line-by-line synchronized lyrics for songs in multiple languages.",
                )

                Spacer(Modifier.height(18.dp))

                FeatureRow(
                    icon = R.drawable.download,
                    title = "Listen Everywhere Offline",
                    description = "Download and cache songs to listen seamlessly on the go.",
                )
            }

            // Bottom action pill
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "Get Started",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "No subscription required. Start listening immediately.",
                    color = SpotifyTextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: Int,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyElevatedSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SpotifyCardSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SpotifyTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
