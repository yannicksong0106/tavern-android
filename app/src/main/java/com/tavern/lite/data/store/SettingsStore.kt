package com.tavern.lite.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tavern.lite.data.model.BubbleStyleConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "visual_settings")

@Serializable
data class TtsSettings(
    val enabled: Boolean = true,
    val engine: String = "system", // "system" or "openai"
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voice: String = "", // system TTS voice name
    val openAiEndpoint: String = "",
    val openAiApiKey: String = "", // encrypted
    val openAiModel: String = "tts-1",
    val openAiVoice: String = "alloy"
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private val BUBBLE_STYLE_KEY = stringPreferencesKey("bubble_style_json")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val BACKGROUND_PROACTIVE_KEY = booleanPreferencesKey("background_proactive_enabled")
        private val TTS_SETTINGS_KEY = stringPreferencesKey("tts_settings_json")
    }

    val languageFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "system"
    }

    suspend fun saveLanguage(language: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = language
        }
    }

    val backgroundProactiveFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[BACKGROUND_PROACTIVE_KEY] ?: false
    }

    suspend fun saveBackgroundProactive(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[BACKGROUND_PROACTIVE_KEY] = enabled
        }
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

    val ttsSettingsFlow: Flow<TtsSettings> = context.settingsDataStore.data.map { prefs ->
        val jsonStr = prefs[TTS_SETTINGS_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<TtsSettings>(jsonStr)
            } catch (_: Exception) {
                TtsSettings()
            }
        } else {
            TtsSettings()
        }
    }

    suspend fun saveTtsSettings(settings: TtsSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[TTS_SETTINGS_KEY] = json.encodeToString(settings)
        }
    }
}
