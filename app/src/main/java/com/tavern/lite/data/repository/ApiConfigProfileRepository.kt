package com.tavern.lite.data.repository

import android.util.Log
import com.tavern.lite.data.db.dao.ApiConfigProfileDao
import com.tavern.lite.data.db.entity.ApiConfigProfileEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.security.CryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配置档案仓库
 * 负责 API 配置档案的 CRUD 操作和配置解析
 */
@Singleton
class ApiConfigProfileRepository @Inject constructor(
    private val profileDao: ApiConfigProfileDao,
    private val json: Json,
    private val cryptoHelper: CryptoHelper
) {
    companion object {
        private const val TAG = "ApiConfigProfileRepo"
    }

    /**
     * 获取所有档案
     */
    fun getAllProfiles(): Flow<List<ApiConfigProfileEntity>> = profileDao.getAllProfiles()

    /**
     * 根据 ID 获取档案
     */
    suspend fun getProfileById(id: Long): ApiConfigProfileEntity? = profileDao.getProfileById(id)

    /**
     * 获取默认档案
     */
    suspend fun getDefaultProfile(): ApiConfigProfileEntity? = profileDao.getDefaultProfile()

    /**
     * 获取默认档案 Flow
     */
    fun getDefaultProfileFlow(): Flow<ApiConfigProfileEntity?> = profileDao.getDefaultProfileFlow()

    /**
     * 根据角色 ID 获取档案
     */
    suspend fun getProfileForCharacter(characterId: Long): ApiConfigProfileEntity? =
        profileDao.getProfileForCharacter(characterId)

    /**
     * 根据聊天 ID 获取档案
     */
    suspend fun getProfileForChat(chatId: Long): ApiConfigProfileEntity? =
        profileDao.getProfileForChat(chatId)

    /**
     * 创建新档案
     */
    suspend fun createProfile(
        name: String,
        config: ApiConfig,
        description: String = "",
        isDefault: Boolean = false
    ): Long {
        val configJson = json.encodeToString(ApiConfig.serializer(), config)
        val encryptedJson = cryptoHelper.encrypt(configJson)

        val profile = ApiConfigProfileEntity(
            name = name,
            description = description,
            configJson = encryptedJson,
            isDefault = isDefault
        )

        return profileDao.insertProfile(profile)
    }

    /**
     * 更新档案
     */
    suspend fun updateProfile(profile: ApiConfigProfileEntity) {
        profileDao.updateProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * 更新档案配置
     */
    suspend fun updateProfileConfig(profileId: Long, config: ApiConfig) {
        val profile = profileDao.getProfileById(profileId) ?: return
        val configJson = json.encodeToString(ApiConfig.serializer(), config)
        val encryptedJson = cryptoHelper.encrypt(configJson)

        profileDao.updateProfile(
            profile.copy(
                configJson = encryptedJson,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 删除档案
     */
    suspend fun deleteProfile(profile: ApiConfigProfileEntity) {
        profileDao.deleteProfile(profile)
    }

    /**
     * 根据 ID 删除档案
     */
    suspend fun deleteProfileById(id: Long) {
        profileDao.deleteProfileById(id)
    }

    /**
     * 设置默认档案
     */
    suspend fun setDefaultProfile(id: Long) {
        profileDao.switchDefaultProfile(id)
    }

    /**
     * 解析档案配置
     */
    fun parseConfig(profile: ApiConfigProfileEntity): ApiConfig {
        return try {
            val decryptedJson = cryptoHelper.tryDecrypt(profile.configJson) ?: profile.configJson
            json.decodeFromString(ApiConfig.serializer(), decryptedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config for profile ${profile.id}: ${e.message}", e)
            ApiConfig()
        }
    }

    /**
     * 根据优先级获取档案（角色 > 聊天 > 默认）
     */
    suspend fun getEffectiveProfile(
        characterId: Long? = null,
        chatId: Long? = null
    ): ApiConfigProfileEntity? {
        // 1. 优先查找聊天绑定的档案
        if (chatId != null) {
            val chatProfile = profileDao.getProfileForChat(chatId)
            if (chatProfile != null) return chatProfile
        }

        // 2. 其次查找角色绑定的档案
        if (characterId != null) {
            val characterProfile = profileDao.getProfileForCharacter(characterId)
            if (characterProfile != null) return characterProfile
        }

        // 3. 最后使用默认档案
        return profileDao.getDefaultProfile()
    }

    /**
     * 获取档案总数
     */
    suspend fun getProfileCount(): Int = profileDao.getProfileCount()
}
