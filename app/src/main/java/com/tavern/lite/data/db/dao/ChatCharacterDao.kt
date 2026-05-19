package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatCharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatCharacterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chatCharacter: ChatCharacterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chatCharacters: List<ChatCharacterEntity>)

    @Query("SELECT * FROM chat_characters WHERE chat_id = :chatId ORDER BY display_order ASC")
    suspend fun getCharactersForChat(chatId: Long): List<ChatCharacterEntity>

    @Query("""
        SELECT c.* FROM characters c
        INNER JOIN chat_characters cc ON c.id = cc.character_id
        WHERE cc.chat_id = :chatId AND cc.is_active = 1
        ORDER BY cc.display_order ASC
    """)
    fun getCharacterEntitiesForChat(chatId: Long): Flow<List<CharacterEntity>>

    @Query("""
        SELECT c.* FROM characters c
        INNER JOIN chat_characters cc ON c.id = cc.character_id
        WHERE cc.chat_id = :chatId AND cc.is_active = 1
        ORDER BY cc.display_order ASC
    """)
    suspend fun getCharacterEntitiesForChatSync(chatId: Long): List<CharacterEntity>

    @Query("DELETE FROM chat_characters WHERE chat_id = :chatId AND character_id = :characterId")
    suspend fun removeCharacter(chatId: Long, characterId: Long)

    @Query("DELETE FROM chat_characters WHERE chat_id = :chatId")
    suspend fun removeAllCharacters(chatId: Long)

    @Query("UPDATE chat_characters SET display_order = :order WHERE chat_id = :chatId AND character_id = :characterId")
    suspend fun updateOrder(chatId: Long, characterId: Long, order: Int)

    @Query("SELECT COUNT(*) FROM chat_characters WHERE chat_id = :chatId AND is_active = 1")
    suspend fun getCharacterCount(chatId: Long): Int
}
