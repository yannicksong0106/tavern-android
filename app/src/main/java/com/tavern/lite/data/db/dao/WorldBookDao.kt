package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookDao {

    @Query("SELECT * FROM world_books ORDER BY updated_at DESC")
    fun getAllWorldBooks(): Flow<List<WorldBookEntity>>

    @Query("SELECT * FROM world_books WHERE id = :id")
    suspend fun getWorldBookById(id: Long): WorldBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldBook(worldBook: WorldBookEntity): Long

    @Update
    suspend fun updateWorldBook(worldBook: WorldBookEntity)

    @Delete
    suspend fun deleteWorldBook(worldBook: WorldBookEntity)

    // Entries
    @Query("SELECT * FROM world_book_entries WHERE world_book_id = :worldBookId AND disabled = 0 ORDER BY order_val ASC")
    fun getActiveEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>>

    @Query("SELECT * FROM world_book_entries WHERE world_book_id = :worldBookId ORDER BY order_val ASC")
    fun getAllEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WorldBookEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: WorldBookEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: WorldBookEntryEntity)

    // 关键词匹配查询
    @Query("""
        SELECT * FROM world_book_entries
        WHERE world_book_id = :worldBookId
        AND disabled = 0
        AND (constant = 1 OR keys != '[]')
        ORDER BY order_val ASC
    """)
    suspend fun getMatchableEntries(worldBookId: Long): List<WorldBookEntryEntity>

    @Query("SELECT * FROM world_books ORDER BY id ASC")
    suspend fun getAllWorldBooksSync(): List<WorldBookEntity>

    @Query("SELECT * FROM world_book_entries ORDER BY id ASC")
    suspend fun getAllEntriesSync(): List<WorldBookEntryEntity>
}
