package com.dhunya.app.features.share

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Watches [ShareSongViewModel.posterToShare] and fires Android's share sheet for every new
 * poster file. Drop this once in a screen that hosts a share entry point (player, lists).
 */
@Composable
fun SharePosterEffect(viewModel: ShareSongViewModel) {
    val context = LocalContext.current
    val poster by viewModel.posterToShare.collectAsState()

    LaunchedEffect(poster) {
        val file: File = poster ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + PROVIDER_SUFFIX,
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share this song"))
        }
        viewModel.onPosterConsumed()
    }
}

private const val PROVIDER_SUFFIX = ".fileprovider"
