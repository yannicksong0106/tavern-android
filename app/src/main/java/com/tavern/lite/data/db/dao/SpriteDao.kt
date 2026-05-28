package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tavern.lite.data.db.entity.SpriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpriteDao {
    @Query("SELECT * FROM sprites WHERE character_id = :characterId ORDER BY display_order ASC, created_at ASC")
    fun getSpritesForCharacter(characterId: Long): Flow<List<SpriteEntity>>

    @Query("SELECT * FROM sprites WHERE character_id = :characterId ORDER BY display_order ASC, created_at ASC")
    suspend fun getSpritesForCharacterSync(characterId: Long): List<SpriteEntity>

    @Query("SELECT * FROM sprites WHERE character_id = :characterId AND emotion = :emotion LIMIT 1")
    suspend fun getSpriteByEmotion(characterId: Long, emotion: String): SpriteEntity?

    @Query("SELECT * FROM sprites WHERE id = :id")
    suspend fun getSpriteById(id: Long): SpriteEntity?

    @Query("SELECT DISTINCT emotion FROM sprites WHERE character_id = :characterId")
    suspend fun getAvailableEmotions(characterId: Long): List<String>

    @Query("SELECT * FROM sprites")
    suspend fun getAllSprites(): List<SpriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sprite: SpriteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sprites: List<SpriteEntity>)

    @Delete
    suspend fun delete(sprite: SpriteEntity)

    @Query("DELETE FROM sprites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sprites WHERE character_id = :characterId")
    suspend fun deleteAllForCharacter(characterId: Long)

    @Query("SELECT COUNT(*) FROM sprites WHERE character_id = :characterId")
    suspend fun getSpriteCount(characterId: Long): Int
}
