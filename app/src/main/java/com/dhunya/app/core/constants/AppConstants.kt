package com.dhunya.app.core.constants

object AppConstants {
    const val DATABASE_NAME = "dhunya_database"
    const val DATASTORE_NAME = "dhunya_preferences"
    
    // LRCLIB API Base URL for real synchronized and plain lyrics
    const val LRCLIB_BASE_URL = "https://lrclib.net/api"
    
    // Notification & Media Channel
    const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "dhunya_playback_channel"
    const val PLAYBACK_NOTIFICATION_ID = 1001
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "dhunya_downloads_channel"
    
    // Search Debounce Duration in ms
    const val SEARCH_DEBOUNCE_MILLIS = 350L
    
    // Default audio sample stream URLs (high quality royalty-free tracks for immediate playback)
    const val MEDIA_CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100MB
}
