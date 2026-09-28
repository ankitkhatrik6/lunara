package com.lunara.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Wraps any [DataSource.Factory] so that requests against YouTube's CDN carry a **closed**
 * byte range such as `Range: bytes=0-3433754`.
 *
 * YouTube's `*.googlevideo.com` servers answer `403 Forbidden` to a plain `GET` and to
 * open-ended ranges like `bytes=1234-`, so ExoPlayer's default behaviour (no `Range` on the
 * first request) can never open a YouTube Music track — playback just sits on
 * "buffering" forever with no sound. Every InnerTube stream URL carries a `clen`
 * parameter with the exact file size; this wrapper turns each such request into a
 * closed range running to the end of the file.
 *
 * Non-YouTube hosts (no `clen` on the URL) keep their original spec untouched, so local
 * files, artwork and HLS playlists flow through unchanged.
 */
class RangedHttpDataSourceFactory(
    private val upstream: DataSource.Factory
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        RangedHttpDataSource(upstream.createDataSource())
}

private class RangedHttpDataSource(
    private val upstream: DataSource
) : DataSource by upstream {

    /**
     * Opens [dataSpec] as a *closed* byte range running to the end of the file.
     *
     * Non-YouTube hosts (no `clen` on the URL) keep their original spec untouched.
     */
    override fun open(dataSpec: DataSpec): Long {
        val wireSpec = asClosedRange(dataSpec) ?: return upstream.open(dataSpec)
        return upstream.open(wireSpec)
    }

    private fun asClosedRange(dataSpec: DataSpec): DataSpec? {
        val total = dataSpec.uri.streamLength()
        if (total <= 0L) return null
        val position = dataSpec.position
        if (position < 0L || position >= total) return null
        val remaining = total - position
        val wanted = if (dataSpec.length == C.LENGTH_UNSET.toLong()) remaining
        else minOf(dataSpec.length, remaining)
        if (wanted <= 0L) return null
        // Closed range: DefaultHttpDataSource translates position+length into
        // `Range: bytes=<pos>-<pos+len-1>`, which is exactly what the CDN demands.
        return dataSpec.buildUpon()
            .setPosition(position)
            .setLength(wanted)
            .build()
    }
}

/** Reads the `clen` parameter YouTube appends to every stream URL. */
private fun Uri.streamLength(): Long = getQueryParameter("clen")?.toLongOrNull() ?: 0L

/** HLS playlists must be handed to ExoPlayer's HLS source, everything else is progressive. */
fun streamMimeTypeOrNull(uri: String): String? = when {
    uri.contains(".m3u8") ||
        uri.contains("/hls_playlist/") ||
        uri.contains("manifest/hls") ||
        uri.contains("manifest.googlevideo.com") -> MimeTypes.APPLICATION_M3U8
    else -> null
}