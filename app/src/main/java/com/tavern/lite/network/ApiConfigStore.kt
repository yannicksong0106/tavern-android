package com.tavern.lite.network

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tavern.lite.data.db.dao.ApiConfigProfileDao
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.port.ApiConfigStorePort
import com.tavern.lite.domain.port.LegacyConfigReaderPort
import com.tavern.lite.security.CryptoHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val Context.apiDataStore: DataStore<Preferences> by preferencesDataStore(name = "api_config")

/**
 * API 配置存储
 * 支持两种模式：
 * 1. 旧模式：直接从 DataStore 读写（兼容旧版本）
 * 2. 新模式：从 Room profile 读写（A7 配置档案）
 *
 * 迁移后使用新模式，未迁移时使用旧模式
 */
@Singleton
class ApiConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val cryptoHelper: CryptoHelper,
    private val profileDao: ApiConfigProfileDao
) : LegacyConfigReaderPort, ApiConfigStorePort {
    companion object {
        private val API_CONFIG_KEY = stringPreferencesKey("api_config_json")
        private const val TAG = "ApiConfigStore"
    }

    /** 当前激活的 profile ID（null 时优先使用默认 profile，若不存在再使用旧模式） */
    private val _activeProfileId = MutableStateFlow<Long?>(null)
    override val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

    /**
     * 配置 Flow
     * 有激活 profile 时：观察 Room profile 变化（保存后自动触发）
     * 无激活 profile 时：优先观察默认 Room profile，未迁移时再从 DataStore 读取
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val configFlow: Flow<ApiConfig> = _activeProfileId.flatMapLatest { profileId ->
        if (profileId != null) {
            // 观察 Room profile 的变化，保存后自动触发
            profileDao.getProfileByIdFlow(profileId).map { profile ->
                if (profile != null) {
                    parseProfileConfig(profile.configJson)
                } else {
                    Log.w(TAG, "Profile $profileId not found, falling back to default profile")
                    loadDefaultProfileConfigOrDataStore()
                }
            }
        } else {
            profileDao.getDefaultProfileFlow().map { defaultProfile ->
                if (defaultProfile != null) {
                    parseProfileConfig(defaultProfile.configJson)
                } else {
                    loadFromDataStore()
                }
            }
        }
    }

    /**
     * 设置激活的 profile
     */
    override fun setActiveProfile(profileId: Long?) {
        Log.d(TAG, "Setting active profile: $profileId")
        _activeProfileId.value = profileId
    }

    /**
     * 解析 profile 配置
     */
    private fun parseProfileConfig(configJson: String): ApiConfig {
        return try {
            val decryptedJson = cryptoHelper.tryDecrypt(configJson) ?: configJson
            json.decodeFromString(ApiConfig.serializer(), decryptedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse profile config: ${e.message}", e)
            ApiConfig()
        }
    }

    /**
     * 从 DataStore 加载配置（旧模式）
     */
    private suspend fun loadFromDataStore(): ApiConfig {
        return try {
            val stored = context.apiDataStore.data.map { prefs ->
                prefs[API_CONFIG_KEY]
            }.firstOrNull()

            if (stored != null) {
                val jsonStr = cryptoHelper.tryDecrypt(stored) ?: stored
                json.decodeFromString<ApiConfig>(jsonStr)
            } else {
                ApiConfig()
            }
        } catch (e: Exception) {
            Log.w(TAG, "配置损坏，回退默认值", e)
            ApiConfig()
        }
    }

    private suspend fun loadDefaultProfileConfigOrDataStore(): ApiConfig {
        return try {
            val defaultProfile = profileDao.getDefaultProfile()
            if (defaultProfile != null) {
                parseProfileConfig(defaultProfile.configJson)
            } else {
                loadFromDataStore()
            }
        } catch (e: Exception) {
            Log.w(TAG, "默认配置档案读取失败，回退旧配置", e)
            loadFromDataStore()
        }
    }

    /**
     * 保存配置
     * 如果有激活的 profile，保存到 Room；否则保存到 DataStore
     */
    override suspend fun save(config: ApiConfig) {
        val profileId = _activeProfileId.value ?: profileDao.getDefaultProfile()?.id
        if (profileId != null) {
            saveToProfile(profileId, config)
        } else {
            saveToDataStore(config)
        }
    }

    /**
     * 保存配置到 Room profile
     */
    private suspend fun saveToProfile(profileId: Long, config: ApiConfig) {
        try {
            val profile = profileDao.getProfileById(profileId)
            if (profile != null) {
                val plainJson = json.encodeToString(config)
                val encrypted = cryptoHelper.encrypt(plainJson)
                profileDao.updateProfile(
                    profile.copy(
                        configJson = encrypted,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                Log.w(TAG, "Profile $profileId not found for save")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config to profile $profileId: ${e.message}", e)
        }
    }

    /**
     * 保存配置到 DataStore（旧模式）
     */
    private suspend fun saveToDataStore(config: ApiConfig) {
        val plainJson = json.encodeToString(config)
        val encrypted = cryptoHelper.encrypt(plainJson)
        context.apiDataStore.edit { prefs ->
            prefs[API_CONFIG_KEY] = encrypted
        }
    }

    /**
     * 读取当前配置（LegacyConfigReaderPort 实现）
     * 用于迁移场景：从 Flow 中获取当前配置
     */
    override suspend fun readConfig(): ApiConfig {
        return configFlow.first()
    }
}
