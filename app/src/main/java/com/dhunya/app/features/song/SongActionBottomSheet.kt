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
import com.dhunya.app.domain.model.Song
import com.dhunya.app.ui.components.DhunyaArtwork
import com.dhunya.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionBottomSheet(
    song: Song,
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
            SheetActionItem(
                icon = Icons.Default.PlayArrow,
                text = "Play now",
                onClick = { onPlay(); onDismiss() }
            )
            SheetActionItem(
                icon = Icons.Default.PlaylistPlay,
                text = "Play next",
                onClick = { onPlayNext(); onDismiss() }
            )
            SheetActionItem(
                icon = Icons.Default.QueueMusic,
                text = "Add to queue",
                onClick = { onAddToQueue(); onDismiss() }
            )
            SheetActionItem(
                icon = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                text = if (song.isFavorite) "Remove from favorites" else "Add to favorites",
                iconTint = if (song.isFavorite) DhunyaAccent else DhunyaTextPrimary,
                onClick = { onToggleFavorite(); onDismiss() }
            )
            SheetActionItem(
                icon = if (song.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                text = if (song.isDownloaded) "Downloaded" else "Save offline",
                iconTint = if (song.isDownloaded) DhunyaAccent else DhunyaTextPrimary,
                onClick = { onDownload(); onDismiss() }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    text: String,
    iconTint: androidx.compose.ui.graphics.Color = DhunyaTextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = DhunyaTextPrimary
        )
    }
}
