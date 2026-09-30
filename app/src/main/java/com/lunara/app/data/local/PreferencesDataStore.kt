package com.lunara.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.lunara.app.core.constants.AppConstants
import com.lunara.app.domain.model.ThemeMode
import com.lunara.app.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = AppConstants.DATASTORE_NAME)

class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_HIGH_QUALITY = booleanPreferencesKey("pref_high_quality")
        val KEY_AUTO_PLAY = booleanPreferencesKey("pref_auto_play")
        val KEY_OFFLINE_MODE = booleanPreferencesKey("pref_offline_mode")
        val KEY_LYRICS_FONT_SIZE = floatPreferencesKey("pref_lyrics_font_size")
        val KEY_DYNAMIC_COLORS = booleanPreferencesKey("pref_dynamic_colors")
        val KEY_THEME_MODE = stringPreferencesKey("pref_theme_mode")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            highQualityAudio = prefs[KEY_HIGH_QUALITY] ?: true,
            autoPlay = prefs[KEY_AUTO_PLAY] ?: true,
            offlineModeOnly = prefs[KEY_OFFLINE_MODE] ?: false,
            lyricsFontSize = prefs[KEY_LYRICS_FONT_SIZE] ?: 18f,
            dynamicColorsEnabled = prefs[KEY_DYNAMIC_COLORS] ?: false,
            themeMode = ThemeMode.fromStorage(prefs[KEY_THEME_MODE])
        )
    }

    suspend fun setHighQuality(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HIGH_QUALITY] = enabled }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_PLAY] = enabled }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OFFLINE_MODE] = enabled }
    }

    suspend fun setLyricsFontSize(size: Float) {
        context.dataStore.edit { it[KEY_LYRICS_FONT_SIZE] = size }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }
}
