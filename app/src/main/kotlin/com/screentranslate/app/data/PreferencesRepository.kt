package com.screentranslate.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screen_translate_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val AUTO_CAPTURE_INTERVAL = longPreferencesKey("auto_capture_interval_ms")
        val FAB_X = intPreferencesKey("fab_position_x")
        val FAB_Y = intPreferencesKey("fab_position_y")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
    }

    val targetLanguage: Flow<String> = context.dataStore.data.map { it[Keys.TARGET_LANGUAGE] ?: "en" }
    val overlayOpacity: Flow<Float> = context.dataStore.data.map { it[Keys.OVERLAY_OPACITY] ?: 0.85f }
    val autoCapture: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_CAPTURE] ?: false }
    val autoCaptureInterval: Flow<Long> = context.dataStore.data.map { it[Keys.AUTO_CAPTURE_INTERVAL] ?: 3000L }
    val fabX: Flow<Int> = context.dataStore.data.map { it[Keys.FAB_X] ?: -1 }
    val fabY: Flow<Int> = context.dataStore.data.map { it[Keys.FAB_Y] ?: -1 }
    val displayMode: Flow<String> = context.dataStore.data.map { it[Keys.DISPLAY_MODE] ?: "bubbles" }

    suspend fun setTargetLanguage(code: String) = context.dataStore.edit { it[Keys.TARGET_LANGUAGE] = code }
    suspend fun setOverlayOpacity(opacity: Float) = context.dataStore.edit { it[Keys.OVERLAY_OPACITY] = opacity }
    suspend fun setAutoCapture(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_CAPTURE] = enabled }
    suspend fun setAutoCaptureInterval(ms: Long) = context.dataStore.edit { it[Keys.AUTO_CAPTURE_INTERVAL] = ms }
    suspend fun setFabPosition(x: Int, y: Int) = context.dataStore.edit { prefs ->
        prefs[Keys.FAB_X] = x
        prefs[Keys.FAB_Y] = y
    }
    suspend fun setDisplayMode(mode: String) = context.dataStore.edit { it[Keys.DISPLAY_MODE] = mode }
}
