package com.lunara.app.features.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lunara.app.domain.model.Song
import com.lunara.app.ui.components.LunaraArtwork
import com.lunara.app.ui.theme.LunaraAccent
import com.lunara.app.ui.theme.LunaraSurfaceElevated
import com.lunara.app.ui.theme.LunaraTextPrimary
import com.lunara.app.ui.theme.LunaraTextSecondary

/**
 * "Add to playlist" sheet: the existing playlists, a one-field "new playlist" composer, and a
 * confirmation checkmark when the song lands somewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    song: Song,
    onDismiss: () -> Unit,
    onSongAdded: (playlistName: String) -> Unit,
    viewModel: PlaylistPickerViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val justAddedTo by viewModel.justAddedTo.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var showComposer by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Side effect, not composition work: state writes happen after the frame is committed.
    LaunchedEffect(justAddedTo) {
        justAddedTo?.let { name ->
            onSongAdded(name)
            viewModel.onConfirmationShown()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LunaraSurfaceElevated,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                color = LunaraTextPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "${song.title} • ${song.artistName}",
                style = MaterialTheme.typography.bodyMedium,
                color = LunaraTextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (showComposer) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.createAndAdd(song, newPlaylistName)
                            newPlaylistName = ""
                            showComposer = false
                        },
                        enabled = newPlaylistName.isNotBlank() && !isSaving
                    ) {
                        Text("Save")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                PlaylistPickerNewRow(onClick = { showComposer = true })
            }

            PlaylistPickerListBody(
                song = song,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlaylistPickerListBody(
    song: Song,
    viewModel: PlaylistPickerViewModel
) {
    val playlists by viewModel.playlists.collectAsState()
    val justAddedTo by viewModel.justAddedTo.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isSaving) {
                        viewModel.addToPlaylist(song, playlist)
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playlist.coverArtworkUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = LunaraTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    LunaraArtwork(
                        url = playlist.coverArtworkUrl,
                        contentDescription = playlist.name,
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 10.dp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = LunaraTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${playlist.songCount} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = LunaraTextSecondary
                    )
                }
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LunaraAccent,
                        strokeWidth = 2.dp
                    )
                } else if (justAddedTo == playlist.name) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Added",
                        tint = LunaraAccent,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    IconButton(
                        onClick = { viewModel.addToPlaylist(song, playlist) },
                        enabled = !isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to ${playlist.name}",
                            tint = LunaraTextSecondary
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PlaylistPickerNewRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = LunaraAccent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "New playlist",
            style = MaterialTheme.typography.titleMedium,
            color = LunaraTextPrimary
        )
    }
}
