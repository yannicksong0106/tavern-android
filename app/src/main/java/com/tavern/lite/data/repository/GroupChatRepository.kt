package com.tavern.lite.data.repository

import com.tavern.lite.data.db.TransactionRunner
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
    private val tx: TransactionRunner,
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

        // 建 chat + 写成员必须原子：分两次独立写时，insertAll 失败或进程被杀会留下
        // isGroup=true 但零成员的孤儿群聊，UI 可见却永久不可用（X 审计）。
        return tx.run {
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

            chatId
        }
    }

    /**
     * Add a character to an existing group chat.
     */
    suspend fun addCharacter(chatId: Long, characterId: Long) {
        // 读 size 再插入是读-改-写：两次并发添加会读到同一 size，产生重复 displayOrder（X 审计）。
        // 包进事务让读与写在同一隔离边界内。
        tx.run {
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

    /**
     * Update the scheduling strategy for a group chat.
     */
    suspend fun updateSchedulingStrategy(chatId: Long, strategy: String) =
        chatDao.updateSchedulingStrategy(chatId, strategy)

    /**
     * Update the message interval for a group chat.
     */
    suspend fun updateMessageInterval(chatId: Long, intervalMs: Long) =
        chatDao.updateMessageInterval(chatId, intervalMs)
}
