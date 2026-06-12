package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickReplyDao {
    @Query("SELECT * FROM quick_reply_sets ORDER BY display_order ASC, id ASC")
    fun getAllSets(): Flow<List<QuickReplySetEntity>>

    @Query("SELECT * FROM quick_reply_sets ORDER BY id ASC")
    suspend fun getAllSetsSync(): List<QuickReplySetEntity>

    @Query("""
        SELECT * FROM quick_reply_sets
        WHERE enabled = 1
        AND (
            scope = 'global'
            OR (scope = 'character' AND character_id = :characterId)
            OR (scope = 'chat' AND chat_id = :chatId)
        )
        ORDER BY display_order ASC, id ASC
    """)
    fun getEnabledSetsForContext(characterId: Long?, chatId: Long?): Flow<List<QuickReplySetEntity>>

    @Query("SELECT * FROM quick_reply_sets WHERE id = :id")
    suspend fun getSetById(id: Long): QuickReplySetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: QuickReplySetEntity): Long

    @Update
    suspend fun updateSet(set: QuickReplySetEntity)

    @Delete
    suspend fun deleteSet(set: QuickReplySetEntity)

    @Query("SELECT * FROM quick_replies WHERE set_id = :setId ORDER BY display_order ASC, id ASC")
    fun getRepliesForSet(setId: Long): Flow<List<QuickReplyEntity>>

    @Query("SELECT * FROM quick_replies ORDER BY id ASC")
    suspend fun getAllRepliesSync(): List<QuickReplyEntity>

    @Query("SELECT * FROM quick_replies WHERE set_id = :setId AND enabled = 1 ORDER BY display_order ASC, id ASC")
    suspend fun getEnabledRepliesForSet(setId: Long): List<QuickReplyEntity>

    @Query("""
        SELECT qr.* FROM quick_replies qr
        INNER JOIN quick_reply_sets qrs ON qr.set_id = qrs.id
        WHERE qr.enabled = 1
        AND qrs.enabled = 1
        AND (
            qrs.scope = 'global'
            OR (qrs.scope = 'character' AND qrs.character_id = :characterId)
            OR (qrs.scope = 'chat' AND qrs.chat_id = :chatId)
        )
        ORDER BY qrs.display_order ASC, qr.display_order ASC, qr.id ASC
    """)
    fun getEnabledRepliesForContext(characterId: Long?, chatId: Long?): Flow<List<QuickReplyEntity>>

    @Query("""
        SELECT qr.* FROM quick_replies qr
        INNER JOIN quick_reply_sets qrs ON qr.set_id = qrs.id
        WHERE qr.enabled = 1
        AND qrs.enabled = 1
        AND qr.automation_id = :automationId
        AND (
            qrs.scope = 'global'
            OR (qrs.scope = 'character' AND qrs.character_id = :characterId)
            OR (qrs.scope = 'chat' AND qrs.chat_id = :chatId)
        )
        ORDER BY qrs.display_order ASC, qr.display_order ASC, qr.id ASC
    """)
    suspend fun getRepliesByAutomationId(
        automationId: String,
        characterId: Long?,
        chatId: Long?
    ): List<QuickReplyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: QuickReplyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplies(replies: List<QuickReplyEntity>)

    @Update
    suspend fun updateReply(reply: QuickReplyEntity)

    @Delete
    suspend fun deleteReply(reply: QuickReplyEntity)

    @Query("DELETE FROM quick_replies WHERE set_id = :setId")
    suspend fun deleteRepliesForSet(setId: Long)
}
