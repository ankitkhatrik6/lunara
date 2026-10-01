package com.lunara.app.core.utils

import android.net.Uri
import androidx.media3.common.MimeTypes
import java.io.File
import java.io.RandomAccessFile

/** Bytes a real audio track always exceeds; a playlist or stub never reaches this. */
private const val MIN_PLAYABLE_BYTES = 32L * 1024L

/** How much of a file is read to recognise its container. */
private const val HEADER_BYTES = 64

/** Text a playlist or manifest starts with; none of it can occur in audio bytes. */
private val PLAYLIST_MARKERS = listOf("#EXTM3U", "#EXTINF", "EXT-X-", "<?xml", "<MPD")

/**
 * `true` when [path] points at a media file ExoPlayer can actually open.
 *
 * A saved download used to be whatever URL the resolver handed over, and the resolver prefers HLS
 * playlists. A playlist written to `lunara_<id>.m4a` is not audio at all: ExoPlayer parses it with
 * the MP4 extractor and reports "unsupported audio format" (3003), which is exactly what happened
 * to downloaded tracks - offline and online alike. Playlists and stubs are rejected here so
 * playback falls back to streaming (or says it is offline) instead of failing on dead bytes.
 *
 * Non-file URIs are accepted: `content://` cannot be inspected cheaply, and the device's own media
 * store does not hand out playlists.
 */
fun isPlayableAudioFile(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    if (!path.startsWith("file:")) return true
    val filePath = runCatching { Uri.parse(path).path }.getOrNull() ?: return false
    return isPlayableAudioFile(File(filePath))
}

/** `true` when [file] is real media: big enough, and not a playlist. */
fun isPlayableAudioFile(file: File): Boolean {
    if (!file.exists() || file.length() < MIN_PLAYABLE_BYTES) return false
    val head = file.header(HEADER_BYTES).decodeToString()
    return PLAYLIST_MARKERS.none { marker -> head.contains(marker) }
}

/**
 * The `file://` URL a saved track is stored under.
 *
 * Byte for byte what `Uri.fromFile(file).toString()` returns for a path that needs no escaping,
 * which every stored track is: the name is a YouTube id under the app's private directory. It is
 * written without `android.net.Uri` on purpose - a stubbed framework method hands a JVM unit test
 * `null`, so a save could never get past building this URL, let alone be asserted.
 */
fun fileUrlOf(file: File): String = "file://" + file.absolutePath

/**
 * The container [path] really holds, read from its own magic bytes.
 *
 * The stored extension is only a guess made from the download URL, and it is wrong often enough -
 * an Opus/WebM stream saved as `.m4a` gets rejected by the MP4 extractor - that the bytes have to
 * decide. `null` means "not recognised", which lets ExoPlayer sniff the file itself.
 */
fun audioMimeTypeOf(path: String): String? {
    if (path.startsWith("http")) return null
    val filePath = runCatching { Uri.parse(path).path }.getOrNull() ?: return null
    return audioMimeTypeOf(File(filePath))
}

fun audioMimeTypeOf(file: File): String? {
    val head = file.header(16)
    return when {
        head.matches(0, 0x1A, 0x45, 0xDF, 0xA3) -> MimeTypes.AUDIO_WEBM
        head.matches(0, 'f'.code, 't'.code, 'y'.code, 'p'.code) -> MimeTypes.AUDIO_MP4
        head.matches(0, 'O'.code, 'g'.code, 'g'.code, 'S'.code) -> MimeTypes.AUDIO_OGG
        head.matches(0, 'f'.code, 'L'.code, 'a'.code, 'C'.code) -> MimeTypes.AUDIO_FLAC
        head.matches(0, 'R'.code, 'I'.code, 'F'.code, 'F'.code) -> MimeTypes.AUDIO_WAV
        head.matches(0, 'I'.code, 'D'.code, '3'.code) -> MimeTypes.AUDIO_MPEG
        head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0 ->
            MimeTypes.AUDIO_MPEG
        else -> null
    }
}

private fun ByteArray.matches(offset: Int, vararg expected: Int): Boolean {
    if (size < offset + expected.size) return false
    return expected.withIndex().all { (index, value) -> this[offset + index] == value.toByte() }
}

private fun File.header(length: Int): ByteArray = runCatching {
    RandomAccessFile(this, "r").use { file ->
        val bytes = ByteArray(length)
        val read = file.read(bytes)
        if (read <= 0) ByteArray(0) else bytes.copyOf(read)
    }
}.getOrDefault(ByteArray(0))
