/**
 * Ankit Khatri KC (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.ui.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lunara.app.R
import com.lunara.app.utils.ComposeToImage
import com.lunara.app.utils.ShareStoryImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The share sheet every song, album, playlist and artist opens. It draws the story picture, shows a
 * preview of it, and hands it to whichever app the user picks - Instagram Stories, WhatsApp,
 * anything that takes an image. A plain link and a copy button sit underneath for the times a
 * picture is not what is wanted.
 *
 * Shown through the app's page bottom sheet (`bottomSheetPageState.show { ... }`), so it slides up
 * over whatever screen called it and needs no navigation of its own.
 */
@Composable
fun ShareStorySheet(
    coverArtUrl: String?,
    title: String,
    artist: String,
    link: String,
    kindLabel: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(coverArtUrl, title, artist, link, kindLabel) {
        image =
            runCatching { ShareStoryImage.create(context, coverArtUrl, title, artist, link, kindLabel) }
                .onFailure { failed = true }
                .getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.share_story_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        val preview = image
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Text(
                        text = stringResource(R.string.share_story_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // One button sends the picture and the link together — Instagram Stories, WhatsApp and
        // most apps accept both, so the recipient gets the card and something that opens the app.
        Button(
            onClick = {
                scope.launch {
                    val bitmap = image
                    if (bitmap != null) {
                        shareImage(context, bitmap, link)
                    } else {
                        shareLink(context, link)
                    }
                }
            },
            enabled = image != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.share_story_image))
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { copyLink(context, link) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.copy_link))
            }
            OutlinedButton(
                onClick = { scope.launch { image?.let { shareImage(context, it, link) } ?: shareLink(context, link) } },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.share))
            }
        }
    }
}

private fun shareImage(
    context: Context,
    bitmap: Bitmap,
    link: String,
) {
    // Saving a 1080x1920 PNG is real disk work; doing it on the main thread is what made the app
    // stutter ("Lunara isn't responding") right as the share sheet opened. Hand it to IO first.
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            val uri = ComposeToImage.saveBitmapAsFile(context, bitmap, "lunara_share_${System.currentTimeMillis()}")
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    // The link rides along as text too, so apps that only take text still carry it.
                    putExtra(Intent.EXTRA_TEXT, link)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_story_image)))
            }
        }.onFailure {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.share_story_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun shareLink(
    context: Context,
    link: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}

private fun copyLink(
    context: Context,
    link: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share), link))
    Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
}

