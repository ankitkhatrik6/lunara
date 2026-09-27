package com.dhunya.app.features.song

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dhunya.app.domain.model.DownloadStatus
import com.dhunya.app.domain.model.Song
import com.dhunya.app.ui.components.AnimatedDownloadIcon
import com.dhunya.app.ui.components.AnimatedFavoriteIcon
import com.dhunya.app.ui.components.DhunyaArtwork
import com.dhunya.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionBottomSheet(
    song: Song,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DhunyaSurfaceElevated,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header: Artwork + Title + Artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DhunyaArtwork(
                    url = song.artworkUrl,
                    contentDescription = song.title,
                    modifier = Modifier.size(54.dp),
                    cornerRadius = 10.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = DhunyaTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunyaTextSecondary,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = DhunyaBorder, modifier = Modifier.padding(bottom = 8.dp))

            // Action Items
            SheetActionRow(text = "Play now", onClick = { onPlay(); onDismiss() }) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = DhunyaTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            SheetActionRow(text = "Play next", onClick = { onPlayNext(); onDismiss() }) {
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = DhunyaTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            SheetActionRow(text = "Add to queue", onClick = { onAddToQueue(); onDismiss() }) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = DhunyaTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            // Favorite / download keep the sheet open so the animated flip is visible.
            SheetActionRow(
                text = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = onToggleFavorite
            ) {
                AnimatedFavoriteIcon(
                    isFavorite = isFavorite,
                    iconSize = 24.dp,
                    inactiveTint = DhunyaTextPrimary
                )
            }
            SheetActionRow(
                text = when {
                    isDownloaded -> "Remove download"
                    isDownloading -> "Downloading…"
                    else -> "Save offline"
                },
                onClick = onDownload
            ) {
                AnimatedDownloadIcon(
                    status = if (isDownloading) DownloadStatus.DOWNLOADING else null,
                    isDownloaded = isDownloaded,
                    iconSize = 24.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * One action row. The leading glyph is a slot so callers can pass an animated icon
 * (heart pop, download progress) instead of a static [ImageVector].
 */
@Composable
private fun SheetActionRow(
    text: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            leading()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = DhunyaTextPrimary
        )
    }
}
