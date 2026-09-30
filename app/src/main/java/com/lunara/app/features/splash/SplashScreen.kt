package com.lunara.app.features.splash

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.app.R
import com.lunara.app.core.constants.AppConstants
import com.lunara.app.ui.theme.LunaraAccent
import com.lunara.app.ui.theme.LunaraBackground
import com.lunara.app.ui.theme.LunaraSurfaceElevated
import com.lunara.app.ui.theme.LunaraTextMuted
import com.lunara.app.ui.theme.LunaraTextPrimary
import kotlinx.coroutines.delay

/**
 * Branded start-up screen.
 *
 * The launcher already paints the logo through the window background (see
 * `drawable/splash_background.xml` and the API 31 splash attributes); this composable keeps the
 * same look alive while the first screens compose, so the app never shows a bare black frame.
 */
@Composable
fun LunaraSplashScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logo = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.lunara_logo)?.asImageBitmap()
    }

    // Logo / wordmark intro: everything fades and scales in so the eye follows it.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val intro by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash-intro"
    )

    // Soft accent halo breathing behind the logo; the same value drives the loading pulse.
    val halo = rememberInfiniteTransition(label = "splash-halo")
    val haloPhase by halo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash-halo-phase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LunaraBackground, LunaraSurfaceElevated)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Glow layer: a large soft accent circle sitting behind the artwork.
                Box(
                    modifier = Modifier
                        .size(198.dp)
                        .graphicsLayer {
                            val scale = 0.92f + 0.16f * haloPhase
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.16f * intro
                        }
                        .clip(CircleShape)
                        .background(LunaraAccent)
                )
                logo?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(132.dp)
                            .graphicsLayer {
                                scaleX = 0.84f + 0.16f * intro
                                scaleY = 0.84f + 0.16f * intro
                                alpha = intro
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = LunaraTextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(intro)
                    .graphicsLayer { translationY = (1f - intro) * 18f }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.app_tagline),
                color = LunaraAccent.copy(alpha = 0.85f),
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(intro)
                    .graphicsLayer { translationY = (1f - intro) * 12f }
            )
        }

        // Loading pulse pinned near the bottom - present without stealing focus from the logo.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LunaraAccent.copy(alpha = 0.22f * intro))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(3.dp)
                        .graphicsLayer { translationX = haloPhase * 56.dp.toPx() }
                        .clip(RoundedCornerShape(2.dp))
                        .background(LunaraAccent.copy(alpha = 0.9f * intro))
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.splash_loading),
                color = LunaraTextMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.alpha(0.9f * intro)
            )
        }
    }
}

/**
 * Shows [LunaraSplashScreen] on top of [content] until the app is ready to be looked at.
 *
 * [content] is composed immediately, underneath the splash, on purpose: the first screens kick off
 * their network/database work during the animation instead of after it, so the splash hides real
 * start-up work rather than delaying it.
 *
 * @param minDurationMillis how long the brand stays on screen - short enough not to feel like a
 *   wait, long enough for the animation to read as intentional.
 */
@Composable
fun SplashGate(
    minDurationMillis: Long = AppConstants.SPLASH_MIN_DURATION_MILLIS,
    content: @Composable () -> Unit
) {
    var splashVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(minDurationMillis)
        splashVisible = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = splashVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 0)),
            exit = fadeOut(animationSpec = tween(durationMillis = 420)) +
                scaleOut(targetScale = 1.05f, animationSpec = tween(durationMillis = 420))
        ) {
            LunaraSplashScreen()
        }
    }
}

