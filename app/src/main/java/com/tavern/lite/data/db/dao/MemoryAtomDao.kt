package com.tavern.lite.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import kotlinx.coroutines.flow.Flow

data class CategoryCount(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "count") val count: Int
)

@Dao
interface MemoryAtomDao {

    @Query("SELECT * FROM memory_atoms WHERE character_id = :characterId AND superseded = 0 ORDER BY importance DESC, last_accessed DESC")
    fun getAtomsForCharacter(characterId: Long): Flow<List<MemoryAtomEntity>>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        AND category = :category
        ORDER BY importance DESC, last_accessed DESC
        LIMIT :limit
    """)
    suspend fun getAtomsByCategory(characterId: Long, category: String, limit: Int): List<MemoryAtomEntity>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        AND content LIKE '%' || :keyword || '%'
        ORDER BY importance DESC, last_accessed DESC
        LIMIT :limit
    """)
    suspend fun searchAtoms(characterId: Long, keyword: String, limit: Int = 10): List<MemoryAtomEntity>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        ORDER BY
            CASE WHEN category = 'character_consistency' THEN 0 ELSE 1 END,
            importance DESC,
            last_accessed DESC
        LIMIT :limit
    """)
    suspend fun getTopAtoms(characterId: Long, limit: Int = 10): List<MemoryAtomEntity>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        AND category = 'character_consistency'
        ORDER BY importance DESC
        LIMIT :limit
    """)
    suspend fun getCharacterConsistencyAtoms(characterId: Long, limit: Int = 5): List<MemoryAtomEntity>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        AND (category = 'event' OR importance >= 8)
        ORDER BY importance DESC
    """)
    suspend fun getHighPriorityAtoms(characterId: Long): List<MemoryAtomEntity>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        AND content LIKE '%' || :text || '%'
        LIMIT 1
    """)
    suspend fun findSimilar(characterId: Long, text: String): MemoryAtomEntity?

    @Query("SELECT * FROM memory_atoms WHERE id = :id")
    suspend fun getById(id: Long): MemoryAtomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(atom: MemoryAtomEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(atoms: List<MemoryAtomEntity>): List<Long>

    @Update
    suspend fun update(atom: MemoryAtomEntity)

    @Query("UPDATE memory_atoms SET superseded = 1 WHERE id = :id")
    suspend fun supersede(id: Long)

    @Query("UPDATE memory_atoms SET last_accessed = :now, access_count = access_count + 1 WHERE id IN (:ids)")
    suspend fun touchAtoms(ids: List<Long>, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM memory_atoms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memory_atoms WHERE character_id = :characterId")
    suspend fun deleteAllForCharacter(characterId: Long)

    @Query("DELETE FROM memory_atoms WHERE character_id = :characterId AND superseded = 1")
    suspend fun purgeSuperseded(characterId: Long)

    @Query("SELECT COUNT(*) FROM memory_atoms WHERE character_id = :characterId AND superseded = 0")
    suspend fun getAtomCount(characterId: Long): Int

    @Query("SELECT COUNT(*) FROM memory_atoms WHERE character_id = :characterId AND superseded = 0")
    fun getAtomCountFlow(characterId: Long): Flow<Int>

    @Query("SELECT * FROM memory_atoms WHERE character_id = :characterId AND superseded = 0 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentAtoms(characterId: Long, limit: Int): List<MemoryAtomEntity>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        ORDER BY (importance * 0.6 + (CAST(:now - last_accessed AS REAL) / 86400000) * -0.2 + access_count * 0.2) DESC
        LIMIT :limit
    """)
    suspend fun getRelevantAtoms(characterId: Long, limit: Int = 10, now: Long = System.currentTimeMillis()): List<MemoryAtomEntity>

    @Query("SELECT * FROM memory_atoms ORDER BY id ASC")
    suspend fun getAllMemoryAtoms(): List<MemoryAtomEntity>

    // --- New queries for visual memory library ---

    @Query("""
        SELECT category, COUNT(*) as count FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        GROUP BY category
    """)
    fun getCategoryCounts(characterId: Long): Flow<List<CategoryCount>>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0 AND category = :category
        ORDER BY importance DESC, last_accessed DESC
    """)
    fun getAtomsByCategoryFlow(characterId: Long, category: String): Flow<List<MemoryAtomEntity>>

    @Query("""
        SELECT * FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0
        AND content LIKE '%' || :query || '%'
        ORDER BY importance DESC, last_accessed DESC
    """)
    fun searchAtomsFlow(characterId: Long, query: String): Flow<List<MemoryAtomEntity>>

    @Query("""
        SELECT MAX(created_at) FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0 AND source != 'manual'
    """)
    fun getLastExtractionTime(characterId: Long): Flow<Long?>

    @Query("""
        DELETE FROM memory_atoms
        WHERE expires_at IS NOT NULL AND expires_at < :now AND superseded = 0
    """)
    suspend fun purgeExpired(now: Long = System.currentTimeMillis())

    @Query("""
        SELECT COUNT(*) FROM memory_atoms
        WHERE character_id = :characterId AND superseded = 0 AND source != 'manual'
    """)
    fun getExtractedCount(characterId: Long): Flow<Int>
}
