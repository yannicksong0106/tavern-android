package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.BgmEntity
import kotlinx.coroutines.flow.Flow

/**
 * 角色背景音乐 DAO
 */
@Dao
interface BgmDao {
    @Query("SELECT * FROM bgms WHERE character_id = :characterId ORDER BY display_order ASC, created_at ASC")
    fun getBgmsForCharacter(characterId: Long): Flow<List<BgmEntity>>

    @Query("SELECT * FROM bgms WHERE character_id = :characterId ORDER BY display_order ASC, created_at ASC")
    suspend fun getBgmsForCharacterSync(characterId: Long): List<BgmEntity>

    @Query("SELECT * FROM bgms WHERE id = :id")
    suspend fun getBgmById(id: Long): BgmEntity?

    @Query("SELECT * FROM bgms WHERE character_id = :characterId ORDER BY display_order ASC LIMIT 1")
    suspend fun getDefaultBgm(characterId: Long): BgmEntity?

    @Query("SELECT * FROM bgms WHERE character_id = :characterId AND emotion = :emotion ORDER BY display_order ASC LIMIT 1")
    suspend fun getBgmByEmotion(characterId: Long, emotion: String): BgmEntity?

    @Query("SELECT * FROM bgms")
    suspend fun getAllBgms(): List<BgmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bgm: BgmEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bgms: List<BgmEntity>)

    @Update
    suspend fun update(bgm: BgmEntity)

    @Delete
    suspend fun delete(bgm: BgmEntity)

    @Query("DELETE FROM bgms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bgms WHERE character_id = :characterId")
    suspend fun deleteAllForCharacter(characterId: Long)

    @Query("SELECT COUNT(*) FROM bgms WHERE character_id = :characterId")
    suspend fun getBgmCount(characterId: Long): Int
}
