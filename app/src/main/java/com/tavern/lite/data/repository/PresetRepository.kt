package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.PresetDao
import com.tavern.lite.data.db.entity.PresetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao
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
}
