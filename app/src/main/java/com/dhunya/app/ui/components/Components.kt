package com.dhunya.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dhunya.app.core.extensions.formatDurationMs
import com.dhunya.app.domain.model.DownloadStatus
import com.dhunya.app.domain.model.Song
import com.dhunya.app.player.PlaybackState
import com.dhunya.app.ui.theme.*

@Composable
fun DhunyaArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(DhunyaSurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = contentDescription,
                tint = DhunyaTextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Heart that animates as it is toggled: the icon crossfades (outline <-> filled), the tint
 * eases into [activeTint] and the glyph does a short bouncy pop — the same feedback Spotify
 * and Blazify give when you love a track.
 */
@Composable
fun AnimatedFavoriteIcon(
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    inactiveTint: Color = DhunyaTextSecondary,
    activeTint: Color = DhunyaAccent
) {
    var settled by remember { mutableStateOf(false) }
    val pop = remember { Animatable(1f) }

    // Skip the very first pass: opening a screen should not make every heart jump.
    LaunchedEffect(isFavorite) {
        if (settled) {
            pop.snapTo(0.7f)
            pop.animateTo(1.25f, animationSpec = tween(140, easing = LinearOutSlowInEasing))
            pop.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        settled = true
    }

    val tint by animateColorAsState(
        targetValue = if (isFavorite) activeTint else inactiveTint,
        animationSpec = tween(220),
        label = "favoriteTint"
    )

    AnimatedContent(
        targetState = isFavorite,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f, animationSpec = tween(160)) + fadeIn(tween(160))) togetherWith
                (scaleOut(targetScale = 0.6f, animationSpec = tween(120)) + fadeOut(tween(120)))
        },
        label = "favoriteIcon",
        modifier = modifier
    ) { active ->
        Icon(
            imageVector = if (active) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (active) "Remove from favorites" else "Add to favorites",
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .scale(pop.value)
        )
    }
}

/** [AnimatedFavoriteIcon] with its own tap target, for rows and toolbars. */
@Composable
fun AnimatedFavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    inactiveTint: Color = DhunyaTextSecondary,
    activeTint: Color = DhunyaAccent
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        AnimatedFavoriteIcon(
            isFavorite = isFavorite,
            iconSize = iconSize,
            inactiveTint = inactiveTint,
            activeTint = activeTint
        )
    }
}

/**
 * Download glyph that morphs between "save offline", a spinner while the file is being
 * fetched and a filled "downloaded" tick once it is on the device.
 */
@Composable
fun AnimatedDownloadIcon(
    status: DownloadStatus?,
    isDownloaded: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp
) {
    val done = isDownloaded || status == DownloadStatus.COMPLETED
    val running = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING

    AnimatedContent(
        targetState = done to running,
        transitionSpec = {
            (scaleIn(initialScale = 0.7f, animationSpec = tween(160)) + fadeIn(tween(160))) togetherWith
                (scaleOut(targetScale = 0.7f, animationSpec = tween(120)) + fadeOut(tween(120)))
        },
        label = "downloadIcon",
        modifier = modifier
    ) { (isDone, isRunning) ->
        when {
            isDone -> Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = "Downloaded",
                tint = DhunyaAccent,
                modifier = Modifier.size(iconSize)
            )
            isRunning -> CircularProgressIndicator(
                modifier = Modifier.size(iconSize - 4.dp),
                color = DhunyaAccent,
                strokeWidth = 2.dp
            )
            else -> Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Save offline",
                tint = DhunyaTextSecondary,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean,
    onSongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DhunyaArtwork(
            url = song.artworkUrl,
            contentDescription = song.title,
            modifier = Modifier.size(48.dp),
            cornerRadius = 10.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) DhunyaAccent else DhunyaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isDownloaded) {
                    AnimatedDownloadIcon(
                        status = null,
                        isDownloaded = true,
                        iconSize = 16.dp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (song.isFavorite) {
                    AnimatedFavoriteIcon(
                        isFavorite = true,
                        iconSize = 14.dp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(
                    text = "${song.artistName} • ${song.durationMs.formatDurationMs()}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = DhunyaTextSecondary
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    state: PlaybackState,
    onBarClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.currentSong ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onBarClick),
        color = DhunyaSurfaceElevated,
        tonalElevation = 6.dp
    ) {
        Column {
            // Subtle progress indicator along top of mini player
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = DhunyaAccent,
                trackColor = Color.Transparent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DhunyaArtwork(
                    url = song.artworkUrl,
                    contentDescription = song.title,
                    modifier = Modifier.size(42.dp),
                    cornerRadius = 8.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = DhunyaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunyaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(2.dp),
                        color = DhunyaAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = DhunyaTextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(onClick = onNextClick) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Track",
                        tint = DhunyaTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DhunyaTextMuted,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DhunyaTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunyaTextSecondary
        )
    }
}
