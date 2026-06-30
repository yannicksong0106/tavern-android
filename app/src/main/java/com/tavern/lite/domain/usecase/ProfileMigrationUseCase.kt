package com.tavern.lite.domain.usecase

import android.util.Log
import com.tavern.lite.data.db.entity.ApiConfigProfileEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ApiConfigProfileRepository
import com.tavern.lite.domain.port.LegacyConfigReaderPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配置档案迁移用例
 * 负责将 DataStore 中的旧配置迁移到 Room profile 系统
 */
@Singleton
class ProfileMigrationUseCase @Inject constructor(
    private val legacyConfigReader: LegacyConfigReaderPort,
    private val profileRepository: ApiConfigProfileRepository
) {
    companion object {
        private const val TAG = "ProfileMigration"
        private const val DEFAULT_PROFILE_NAME = "默认配置"
    }

    /**
     * 执行迁移：如果 Room 中没有 profile，则将 DataStore 配置迁移为默认 profile
     * @return true 如果执行了迁移，false 如果已经迁移过
     */
    suspend fun migrateIfNeeded(): Boolean {
        val existingCount = profileRepository.getProfileCount()
        if (existingCount > 0) {
            Log.d(TAG, "Profiles already exist ($existingCount), skipping migration")
            return false
        }

        Log.d(TAG, "No profiles found, migrating DataStore config to default profile")
        val dataStoreConfig = legacyConfigReader.readConfig()

        profileRepository.createProfile(
            name = DEFAULT_PROFILE_NAME,
            config = dataStoreConfig,
            description = "从旧版本自动迁移的配置",
            isDefault = true
        )

        Log.d(TAG, "Migration complete: created default profile '${DEFAULT_PROFILE_NAME}'")
        return true
    }
}
