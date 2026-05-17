package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Query("SELECT * FROM characters ORDER BY updated_at DESC")
    fun getAllCharacters(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getCharacterById(id: Long): CharacterEntity?

    @Query("SELECT * FROM characters WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR personality LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY updated_at DESC")
    fun searchCharacters(query: String): Flow<List<CharacterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(character: CharacterEntity): Long

    @Update
    suspend fun update(character: CharacterEntity)

    @Delete
    suspend fun delete(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: Long)
}
