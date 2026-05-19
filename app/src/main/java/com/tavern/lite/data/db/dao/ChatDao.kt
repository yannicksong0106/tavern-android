package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tavern.lite.data.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats WHERE character_id = :characterId ORDER BY updated_at DESC")
    fun getChatsForCharacter(characterId: Long): Flow<List<ChatEntity>>

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

    @Query("SELECT * FROM chats WHERE character_id = :characterId ORDER BY updated_at DESC")
    suspend fun getAllChatsForCharacter(characterId: Long): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE is_group = 1 ORDER BY updated_at DESC")
    fun getAllGroupChats(): Flow<List<ChatEntity>>
}
