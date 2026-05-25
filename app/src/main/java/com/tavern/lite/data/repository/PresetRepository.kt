package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.PresetDao
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.store.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao,
    private val characterDao: CharacterDao,
    private val chatDao: ChatDao,
    private val settingsStore: SettingsStore,
) {
    fun getAllPresets(): Flow<List<PresetEntity>> = presetDao.getAllPresets()

    suspend fun getPresetById(id: Long): PresetEntity? = presetDao.getPresetById(id)

    suspend fun getDefaultPreset(): PresetEntity? = presetDao.getDefaultPreset()

    suspend fun insertPreset(preset: PresetEntity): Long = presetDao.insertPreset(preset)

    suspend fun updatePreset(preset: PresetEntity) = presetDao.updatePreset(preset)

    suspend fun deletePreset(preset: PresetEntity) = presetDao.deletePreset(preset)

    suspend fun setDefaultPreset(id: Long) {
        presetDao.clearDefaultPresets()
        presetDao.setDefaultPreset(id)
    }

    fun getPresetsByScope(scope: String): Flow<List<PresetEntity>> = presetDao.getPresetsByScope(scope)

    /**
     * 解析三级预设：Chat > Character > Global > null
     * 合并规则：非空字段覆盖，空字段 fallback 到下一级
     */
    suspend fun resolveEffectivePreset(chatId: Long, characterId: Long): PresetEntity? {
        val globalPresetId = settingsStore.globalPresetIdFlow.first()
        val character = characterDao.getCharacterById(characterId)
        val chat = chatDao.getChatById(chatId)

        val globalPreset = if (globalPresetId > 0) presetDao.getPresetById(globalPresetId) else null
        val characterPreset = character?.presetId?.let { presetDao.getPresetById(it) }
        val chatPreset = chat?.presetId?.let { presetDao.getPresetById(it) }

        return mergePresets(globalPreset, characterPreset, chatPreset)
    }

    /**
     * 合并多级预设：later 优先级更高，非空字段覆盖 earlier
     */
    private fun mergePresets(vararg presets: PresetEntity?): PresetEntity? {
        val nonNull = presets.filterNotNull()
        if (nonNull.isEmpty()) return null
        if (nonNull.size == 1) return nonNull[0]

        return nonNull.reduce { acc, preset ->
            PresetEntity(
                name = preset.name, // 取高优先级的名称
                description = preset.description.ifBlank { acc.description },
                systemPrompt = preset.systemPrompt.ifBlank { acc.systemPrompt },
                postHistoryInstructions = preset.postHistoryInstructions.ifBlank { acc.postHistoryInstructions },
                authorNote = preset.authorNote.ifBlank { acc.authorNote },
                scope = preset.scope,
                isDefault = preset.isDefault || acc.isDefault
            )
        }
    }
}
