package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.ChatWithLastMessage
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface ChatDao {

    @Query("SELECT * FROM chats WHERE character_id = :characterId ORDER BY updated_at DESC")
    fun getChatsForCharacter(characterId: Long): Flow<List<ChatEntity>>

    @Query("""
        SELECT c.*,
            (SELECT m.role FROM messages m WHERE m.chat_id = c.id AND m.is_active = 1 ORDER BY m.created_at DESC LIMIT 1) as last_message_role,
            (SELECT m.content FROM messages m WHERE m.chat_id = c.id AND m.is_active = 1 ORDER BY m.created_at DESC LIMIT 1) as last_message_content
        FROM chats c
        WHERE c.character_id = :characterId
        ORDER BY c.updated_at DESC
    """)
    fun getChatsWithLastMessage(characterId: Long): Flow<List<ChatWithLastMessage>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChatById(id: Long): ChatEntity?

    @Query("SELECT * FROM chats WHERE character_id = :characterId ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLatestChatForCharacter(characterId: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: ChatEntity): Long

    @Delete
    suspend fun delete(chat: ChatEntity)

    @Query("UPDATE chats SET updated_at = :timestamp WHERE id = :chatId")
    suspend fun updateTimestamp(chatId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET name = :name WHERE id = :chatId")
    suspend fun renameChat(chatId: Long, name: String)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteById(chatId: Long)

    @Query("UPDATE chats SET background_path = :path WHERE id = :chatId")
    suspend fun updateBackground(chatId: Long, path: String?)

    @Query("UPDATE chats SET group_chattiness = :chattiness WHERE id = :chatId")
    suspend fun updateGroupChattiness(chatId: Long, chattiness: Int)

    @Query("SELECT * FROM chats WHERE character_id = :characterId ORDER BY updated_at DESC")
    suspend fun getAllChatsForCharacter(characterId: Long): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE is_group = 1 ORDER BY updated_at DESC")
    fun getAllGroupChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updated_at DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updated_at DESC LIMIT :limit")
    suspend fun getRecentChats(limit: Int): List<ChatEntity>

    @Query("SELECT * FROM chats ORDER BY id ASC")
    suspend fun getAllChatsSync(): List<ChatEntity>

    @Query("UPDATE chats SET scheduling_strategy = :strategy WHERE id = :chatId")
    suspend fun updateSchedulingStrategy(chatId: Long, strategy: String)

    @Query("UPDATE chats SET message_interval_ms = :intervalMs WHERE id = :chatId")
    suspend fun updateMessageInterval(chatId: Long, intervalMs: Long)
}
