package com.tavern.lite.network

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tavern.lite.data.model.ApiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val Context.apiDataStore: DataStore<Preferences> by preferencesDataStore(name = "api_config")

@Singleton
class ApiConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private val API_CONFIG_KEY = stringPreferencesKey("api_config_json")
    }

    val configFlow: Flow<ApiConfig> = context.apiDataStore.data.map { prefs ->
        val jsonStr = prefs[API_CONFIG_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<ApiConfig>(jsonStr)
            } catch (_: Exception) {
                ApiConfig()
            }
        } else {
            ApiConfig()
        }
    }

    suspend fun save(config: ApiConfig) {
        context.apiDataStore.edit { prefs ->
            prefs[API_CONFIG_KEY] = json.encodeToString(config)
        }
    }
}
