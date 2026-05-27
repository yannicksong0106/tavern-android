package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND is_active = 1 ORDER BY created_at ASC")
    fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND is_active = 1 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentMessages(chatId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND is_active = 1 ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastMessageForChat(chatId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND is_active = 1 AND role = 'user' ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastUserMessage(chatId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chat_id = :chatId AND is_active = 1")
    suspend fun getMessageCount(chatId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("UPDATE messages SET content = content || :chunk WHERE id = :id")
    suspend fun appendContent(id: Long, chunk: String)

    @Query("UPDATE messages SET is_active = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun deleteAllForChat(chatId: Long)

    // 分支操作
    @Query("SELECT DISTINCT branch_id FROM messages WHERE chat_id = :chatId AND branch_id IS NOT NULL ORDER BY created_at ASC")
    suspend fun getBranchIds(chatId: Long): List<Long?>

    @Query("UPDATE messages SET is_active = 0 WHERE chat_id = :chatId AND is_active = 1")
    suspend fun deactivateAllMessages(chatId: Long)

    @Query("UPDATE messages SET is_active = 1 WHERE chat_id = :chatId AND branch_id = :branchId")
    suspend fun activateBranch(chatId: Long, branchId: Long)

    // Swipe alternatives
    @Query("UPDATE messages SET swipe_content = :swipeJson, swipe_index = :swipeIndex, content = :currentContent WHERE id = :id")
    suspend fun updateSwipe(id: Long, swipeJson: String, swipeIndex: Int, currentContent: String)

    @Query("UPDATE messages SET swipe_index = :swipeIndex, content = :currentContent WHERE id = :id")
    suspend fun updateSwipeIndex(id: Long, swipeIndex: Int, currentContent: String)

    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND is_active = 1 ORDER BY created_at ASC")
    suspend fun getAllActiveMessagesForChat(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY id ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("UPDATE messages SET is_pinned = :pinned WHERE id = :messageId")
    suspend fun setPinned(messageId: Long, pinned: Boolean)

    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND is_active = 1 AND is_pinned = 1 ORDER BY created_at DESC")
    fun getPinnedMessages(chatId: Long): Flow<List<MessageEntity>>
}
