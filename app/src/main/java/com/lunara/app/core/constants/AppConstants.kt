package com.lunara.app.core.constants

object AppConstants {
    const val DATABASE_NAME = "lunara_database"
    const val DATASTORE_NAME = "lunara_preferences"

    // Multi-provider lyrics backends (same order as Blazify):
    // 1. Paxsenix (Apple Music synced lyrics)  2. LRCLIB (community LRC)
    const val PAXSENIX_BASE_URL = "https://lyrics.paxsenix.org"
    const val LRCLIB_BASE_URL = "https://lrclib.net/api"
    
    // Notification & Media Channel
    const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "lunara_playback_channel"
    const val PLAYBACK_NOTIFICATION_ID = 1001
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "lunara_downloads_channel"
    
    // Search Debounce Duration in ms
    const val SEARCH_DEBOUNCE_MILLIS = 350L
    
    // Playback cache budget for the media cache
    const val MEDIA_CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100MB
}
