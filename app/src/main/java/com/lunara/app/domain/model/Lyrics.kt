package com.lunara.app.domain.model

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class Lyrics(
    val songId: String,
    val plainLyrics: String? = null,
    val syncedLyrics: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false,
    val source: String = "LRCLIB"
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItem(
    val song: Song,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val localFilePath: String? = null,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L
)

data class PlayableMedia(
    val song: Song,
    val mediaUri: String,
    val isLocal: Boolean,
    val mimeType: String? = null,
    val isHls: Boolean = false,
    /**
     * Other resolved URLs for the same track, best first. If the CDN rejects [mediaUri]
     * mid playback (expired signature, throttled edge, PO-token gate) the player swaps to the
     * next one instead of stopping with "source error".
     */
    val fallbackUris: List<String> = emptyList(),
    /**
     * Exact size of [mediaUri] in bytes when the resolver knows it. The player needs it to
     * close its requests into a single byte range instead of discovering the size chunk by
     * chunk, which is what keeps YouTube's CDN from throttling playback.
     */
    val contentLength: Long = 0L
)

data class PlaybackQueue(
    val items: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val currentSong: Song?
        get() = if (currentIndex in items.indices) items[currentIndex] else null
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class UserSettings(
    val highQualityAudio: Boolean = true,
    val autoPlay: Boolean = true,
    /**
     * Bring back the queue and the playhead a previous run of Lunara left behind, paused where the
     * music stopped. On by default: an app that opens to an empty player has forgotten what the
     * user was doing.
     */
    val resumePlayback: Boolean = true,
    val offlineModeOnly: Boolean = false,
    val lyricsFontSize: Float = 18f,
    val dynamicColorsEnabled: Boolean = false,
    /**
     * Appearance of the app. SYSTEM follows the device dark-theme setting, which is the default
     * because the palette drives every screen.
     */
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
