package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {
    fun getChatsForCharacter(characterId: Long): Flow<List<ChatEntity>> =
        chatDao.getChatsForCharacter(characterId)

    suspend fun getChatById(id: Long): ChatEntity? = chatDao.getChatById(id)

    suspend fun getLatestChatForCharacter(characterId: Long): ChatEntity? =
        chatDao.getLatestChatForCharacter(characterId)

    suspend fun createChat(characterId: Long, name: String? = null): Long {
        return chatDao.insert(ChatEntity(characterId = characterId, name = name))
    }

    suspend fun deleteChat(chat: ChatEntity) = chatDao.delete(chat)

    suspend fun deleteChatById(chatId: Long) = chatDao.deleteById(chatId)

    suspend fun renameChat(chatId: Long, name: String) = chatDao.renameChat(chatId, name)

    suspend fun updateChatBackground(chatId: Long, path: String?) = chatDao.updateBackground(chatId, path)

    // Messages
    fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesForChat(chatId)

    suspend fun getRecentMessages(chatId: Long, limit: Int): List<MessageEntity> =
        messageDao.getRecentMessages(chatId, limit)

    suspend fun sendMessage(chatId: Long, content: String, role: String): Long {
        val id = messageDao.insert(
            MessageEntity(chatId = chatId, role = role, content = content)
        )
        chatDao.updateTimestamp(chatId)
        return id
    }

    suspend fun appendToMessage(messageId: Long, content: String) {
        val existing = messageDao.getMessageById(messageId)
        if (existing != null) {
            messageDao.updateContent(messageId, existing.content + content)
        }
    }

    suspend fun updateMessageContent(messageId: Long, content: String) {
        messageDao.updateContent(messageId, content)
    }

    suspend fun deleteMessage(messageId: Long) = messageDao.softDelete(messageId)

    suspend fun getMessageById(messageId: Long): MessageEntity? = messageDao.getMessageById(messageId)

    // 分支操作
    suspend fun getBranchIds(chatId: Long): List<Long?> = messageDao.getBranchIds(chatId)

    suspend fun switchBranch(chatId: Long, branchId: Long) {
        messageDao.deactivateAllMessages(chatId)
        messageDao.activateBranch(chatId, branchId)
    }

    suspend fun createBranch(chatId: Long, fromMessageId: Long, newBranchId: Long) {
        // 从 fromMessageId 之后的消息都设为非激活
        val allMessages = messageDao.getRecentMessages(chatId, 1000).reversed()
        val fromIndex = allMessages.indexOfFirst { it.id == fromMessageId }
        if (fromIndex < 0) return

        // 将 fromMessage 之后的消息设为非激活
        for (i in (fromIndex + 1) until allMessages.size) {
            messageDao.deactivateMessage(chatId, allMessages[i].id)
        }
    }

    suspend fun sendMessageInBranch(chatId: Long, content: String, role: String, branchId: Long, parentId: Long?): Long {
        val id = messageDao.insert(
            MessageEntity(
                chatId = chatId,
                role = role,
                content = content,
                branchId = branchId,
                parentId = parentId
            )
        )
        chatDao.updateTimestamp(chatId)
        return id
    }

    suspend fun getAllChatsForCharacter(characterId: Long): List<ChatEntity> =
        chatDao.getAllChatsForCharacter(characterId)

    suspend fun getAllMessagesForChat(chatId: Long): List<MessageEntity> =
        messageDao.getRecentMessages(chatId, 10000)

    // Swipe alternatives
    suspend fun addSwipe(messageId: Long, newContent: String) {
        val msg = messageDao.getMessageById(messageId) ?: return
        val swipes = parseSwipeContent(msg.swipeContent).toMutableList()
        // If current content is not in swipes, add it first
        if (swipes.isEmpty() || swipes.last() != msg.content) {
            swipes.add(msg.content)
        }
        swipes.add(newContent)
        val newIndex = swipes.size - 1
        messageDao.updateSwipe(messageId, toJsonArray(swipes), newIndex, newContent)
    }

    suspend fun switchSwipe(messageId: Long, newIndex: Int) {
        val msg = messageDao.getMessageById(messageId) ?: return
        val swipes = parseSwipeContent(msg.swipeContent)
        if (newIndex < 0 || newIndex >= swipes.size) return
        messageDao.updateSwipeIndex(messageId, newIndex, swipes[newIndex])
    }

    suspend fun getSwipeCount(messageId: Long): Int {
        val msg = messageDao.getMessageById(messageId) ?: return 0
        val swipes = parseSwipeContent(msg.swipeContent)
        return if (swipes.isEmpty()) 1 else swipes.size
    }

    private fun parseSwipeContent(json: String): List<String> {
        if (json == "[]" || json.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJsonArray(items: List<String>): String {
        val arr = org.json.JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }
}
