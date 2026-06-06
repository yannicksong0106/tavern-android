package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tavern.lite.data.db.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {

    @Query("SELECT * FROM summaries WHERE chat_id = :chatId ORDER BY created_at DESC")
    fun getSummariesForChat(chatId: Long): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries WHERE chat_id = :chatId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestSummary(chatId: Long): SummaryEntity?

    @Query("SELECT * FROM summaries WHERE id = :id")
    suspend fun getSummaryById(id: Long): SummaryEntity?

    @Query("SELECT * FROM summaries ORDER BY chat_id ASC, created_at ASC")
    suspend fun getAllSummaries(): List<SummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: SummaryEntity): Long

    @Query("UPDATE summaries SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM summaries WHERE chat_id = :chatId")
    suspend fun deleteAllForChat(chatId: Long)

    @Query("SELECT COUNT(*) FROM summaries WHERE chat_id = :chatId")
    suspend fun getCountForChat(chatId: Long): Int
}
