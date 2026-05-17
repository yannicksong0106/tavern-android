package com.tavern.lite.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tavern.lite.data.model.BubbleStyleConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "visual_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private val BUBBLE_STYLE_KEY = stringPreferencesKey("bubble_style_json")
    }

    val bubbleStyleFlow: Flow<BubbleStyleConfig> = context.settingsDataStore.data.map { prefs ->
        val jsonStr = prefs[BUBBLE_STYLE_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<BubbleStyleConfig>(jsonStr)
            } catch (_: Exception) {
                BubbleStyleConfig()
            }
        } else {
            BubbleStyleConfig()
        }
    }

    suspend fun saveBubbleStyle(config: BubbleStyleConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[BUBBLE_STYLE_KEY] = json.encodeToString(config)
        }
    }
}
