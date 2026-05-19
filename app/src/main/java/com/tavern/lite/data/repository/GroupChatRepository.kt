package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.ChatCharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatCharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val chatCharacterDao: ChatCharacterDao,
    private val messageDao: MessageDao
) {
    /**
     * Create a group chat with the given characters.
     * The first character is the "primary" (stored in chats.character_id for backward compat).
     */
    suspend fun createGroupChat(
        characterIds: List<Long>,
        name: String? = null
    ): Long {
        require(characterIds.size >= 2) { "Group chat needs at least 2 characters" }

        val chatId = chatDao.insert(
            ChatEntity(
                characterId = characterIds.first(),
                name = name,
                isGroup = true
            )
        )

        val chatCharacters = characterIds.mapIndexed { index, characterId ->
            ChatCharacterEntity(
                chatId = chatId,
                characterId = characterId,
                displayOrder = index
            )
        }
        chatCharacterDao.insertAll(chatCharacters)

        return chatId
    }

    /**
     * Add a character to an existing group chat.
     */
    suspend fun addCharacter(chatId: Long, characterId: Long) {
        val existing = chatCharacterDao.getCharactersForChat(chatId)
        val nextOrder = existing.size
        chatCharacterDao.insert(
            ChatCharacterEntity(
                chatId = chatId,
                characterId = characterId,
                displayOrder = nextOrder
            )
        )
    }

    /**
     * Remove a character from a group chat.
     */
    suspend fun removeCharacter(chatId: Long, characterId: Long) {
        chatCharacterDao.removeCharacter(chatId, characterId)
    }

    /**
     * Get all characters in a group chat.
     */
    fun getCharactersForChat(chatId: Long): Flow<List<CharacterEntity>> =
        chatCharacterDao.getCharacterEntitiesForChat(chatId)

    /**
     * Get characters synchronously (for prompt building).
     */
    suspend fun getCharactersForChatSync(chatId: Long): List<CharacterEntity> =
        chatCharacterDao.getCharacterEntitiesForChatSync(chatId)

    /**
     * Check if a chat is a group chat.
     */
    suspend fun isGroupChat(chatId: Long): Boolean {
        val chat = chatDao.getChatById(chatId)
        return chat?.isGroup == true
    }

    /**
     * Get the character count for a chat.
     */
    suspend fun getCharacterCount(chatId: Long): Int =
        chatCharacterDao.getCharacterCount(chatId)

    /**
     * Update a character's chattiness in a group chat.
     */
    suspend fun updateCharacterChattiness(chatId: Long, characterId: Long, chattiness: Int) =
        chatCharacterDao.updateChattiness(chatId, characterId, chattiness)

    /**
     * Get ChatCharacterEntity list (with chattiness info) for a group chat.
     */
    suspend fun getChatCharacters(chatId: Long): List<ChatCharacterEntity> =
        chatCharacterDao.getCharactersForChat(chatId)
}
