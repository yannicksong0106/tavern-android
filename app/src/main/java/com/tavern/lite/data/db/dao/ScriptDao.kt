package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.ScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts WHERE character_id = :characterId ORDER BY sort_order ASC, id ASC")
    fun getScriptsForCharacter(characterId: Long): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE character_id = :characterId AND enabled = 1 ORDER BY sort_order ASC, id ASC")
    suspend fun getEnabledScripts(characterId: Long): List<ScriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity): Long

    @Update
    suspend fun updateScript(script: ScriptEntity)

    @Delete
    suspend fun deleteScript(script: ScriptEntity)

    @Query("DELETE FROM scripts WHERE character_id = :characterId")
    suspend fun deleteAllForCharacter(characterId: Long)
}
