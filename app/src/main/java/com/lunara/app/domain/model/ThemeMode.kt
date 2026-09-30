package com.lunara.app.domain.model

/**
 * User-chosen appearance of the app.
 *
 * Persisted as the enum name so new values can be added without breaking stored preferences.
 */
enum class ThemeMode {
    /** Follow the device (Android 10+ dark theme setting). */
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            values().firstOrNull { it.name == value } ?: SYSTEM
    }
}
