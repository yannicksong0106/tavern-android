package com.tavern.lite.network

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.security.CryptoHelper
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
    private val json: Json,
    private val cryptoHelper: CryptoHelper
) {
    companion object {
        private val API_CONFIG_KEY = stringPreferencesKey("api_config_json")
    }

    val configFlow: Flow<ApiConfig> = context.apiDataStore.data.map { prefs ->
        val stored = prefs[API_CONFIG_KEY]
        if (stored != null) {
            try {
                // 尝试解密（新格式：加密的 JSON）
                val jsonStr = cryptoHelper.tryDecrypt(stored) ?: stored
                json.decodeFromString<ApiConfig>(jsonStr)
            } catch (e: Exception) {
                Log.w("ApiConfigStore", "配置损坏，回退默认值", e)
                ApiConfig()
            }
        } else {
            ApiConfig()
        }
    }

    suspend fun save(config: ApiConfig) {
        val plainJson = json.encodeToString(config)
        val encrypted = cryptoHelper.encrypt(plainJson)
        context.apiDataStore.edit { prefs ->
            prefs[API_CONFIG_KEY] = encrypted
        }
    }
}
