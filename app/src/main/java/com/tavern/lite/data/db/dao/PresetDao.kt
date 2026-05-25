package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY is_default DESC, name ASC")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPresetById(id: Long): PresetEntity?

    @Query("SELECT * FROM presets WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultPreset(): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity): Long

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Query("UPDATE presets SET is_default = 0")
    suspend fun clearDefaultPresets()

    @Query("UPDATE presets SET is_default = 1 WHERE id = :id")
    suspend fun setDefaultPreset(id: Long)

    @Query("SELECT * FROM presets ORDER BY id ASC")
    suspend fun getAllPresetsSync(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE scope = :scope ORDER BY is_default DESC, name ASC")
    fun getPresetsByScope(scope: String): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE scope = :scope ORDER BY is_default DESC, name ASC")
    suspend fun getPresetsByScopeSync(scope: String): List<PresetEntity>
}
