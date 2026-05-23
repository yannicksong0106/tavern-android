package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.tavern.lite.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories WHERE character_id = :characterId ORDER BY importance DESC, last_accessed DESC")
    fun getMemoriesForCharacter(characterId: Long): Flow<List<MemoryEntity>>

    @Query("""
        SELECT * FROM memories
        WHERE character_id = :characterId
        AND content LIKE '%' || :keyword || '%'
        ORDER BY importance DESC, last_accessed DESC
        LIMIT :limit
    """)
    suspend fun searchMemories(characterId: Long, keyword: String, limit: Int = 5): List<MemoryEntity>

    @Query("""
        SELECT * FROM memories
        WHERE character_id = :characterId
        ORDER BY (importance * 0.7 + (CAST(:now - last_accessed AS REAL) / 86400000) * -0.3) DESC
        LIMIT :limit
    """)
    suspend fun getTopMemories(characterId: Long, limit: Int = 5, now: Long = System.currentTimeMillis()): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories WHERE character_id = :characterId")
    suspend fun deleteAllForCharacter(characterId: Long)

    @Query("UPDATE memories SET last_accessed = :now, access_count = access_count + 1 WHERE id = :id")
    suspend fun touchMemory(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE memories SET last_accessed = :now, access_count = access_count + 1 WHERE id IN (:ids)")
    suspend fun touchMemories(ids: List<Long>, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM memories ORDER BY id ASC")
    suspend fun getAllMemories(): List<MemoryEntity>

    /**
     * Search memories matching ANY of the keywords (single query with OR conditions).
     * Avoids N+1 queries when searching with multiple keywords.
     */
    @RawQuery
    suspend fun searchMemoriesRaw(query: SupportSQLiteQuery): List<MemoryEntity>

    suspend fun searchMemoriesMultiKeyword(
        characterId: Long,
        keywords: List<String>,
        limit: Int
    ): List<MemoryEntity> {
        if (keywords.isEmpty()) return emptyList()
        val orClauses = keywords.joinToString(" OR ") { "content LIKE '%' || ? || '%'" }
        val args = keywords.toTypedArray()
        val sql = "SELECT * FROM memories WHERE character_id = ? AND ($orClauses) ORDER BY importance DESC, last_accessed DESC LIMIT ?"
        val query = SimpleSQLiteQuery(sql, arrayOf(characterId, *args, limit))
        return searchMemoriesRaw(query)
    }
}
