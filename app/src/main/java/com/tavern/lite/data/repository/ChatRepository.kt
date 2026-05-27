package com.tavern.lite.data.repository

import com.tavern.lite.data.db.TransactionRunner
import com.tavern.lite.data.db.dao.BranchDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.ChatWithLastMessage
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.util.SwipeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val tx: TransactionRunner,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val branchDao: BranchDao
) {
    fun getChatsForCharacter(characterId: Long): Flow<List<ChatEntity>> =
        chatDao.getChatsForCharacter(characterId)

    fun getChatsWithLastMessage(characterId: Long): Flow<List<ChatWithLastMessage>> =
        chatDao.getChatsWithLastMessage(characterId)

    fun getAllGroupChats(): Flow<List<ChatEntity>> =
        chatDao.getAllGroupChats()

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

    suspend fun updateGroupChattiness(chatId: Long, chattiness: Int) = chatDao.updateGroupChattiness(chatId, chattiness)

    // Messages
    fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesForChat(chatId)

    suspend fun getRecentMessages(chatId: Long, limit: Int): List<MessageEntity> =
        messageDao.getRecentMessages(chatId, limit)

    suspend fun getLastMessageForChat(chatId: Long): MessageEntity? =
        messageDao.getLastMessageForChat(chatId)

    suspend fun getLastUserMessage(chatId: Long): MessageEntity? =
        messageDao.getLastUserMessage(chatId)

    suspend fun sendMessage(chatId: Long, content: String, role: String, characterId: Long? = null, replyToId: Long? = null): Long {
        val id = messageDao.insert(
            MessageEntity(chatId = chatId, role = role, content = content, characterId = characterId, replyToId = replyToId)
        )
        chatDao.updateTimestamp(chatId)
        return id
    }

    suspend fun appendToMessage(messageId: Long, content: String) {
        messageDao.appendContent(messageId, content)
    }

    suspend fun updateMessageContent(messageId: Long, content: String) {
        messageDao.updateContent(messageId, content)
    }

    suspend fun deleteMessage(messageId: Long) = messageDao.softDelete(messageId)

    suspend fun togglePinMessage(messageId: Long, pinned: Boolean) = messageDao.setPinned(messageId, pinned)

    fun getPinnedMessages(chatId: Long) = messageDao.getPinnedMessages(chatId)

    suspend fun getMessageById(messageId: Long): MessageEntity? = messageDao.getMessageById(messageId)

    suspend fun getMessageCount(chatId: Long): Int = messageDao.getMessageCount(chatId)

    // 分支操作
    suspend fun getBranchIds(chatId: Long): List<Long?> = messageDao.getBranchIds(chatId)

    suspend fun switchBranch(chatId: Long, branchId: Long) = tx.run {
        messageDao.deactivateAllMessages(chatId)
        messageDao.activateBranch(chatId, branchId)
    }

    suspend fun getAllChatsForCharacter(characterId: Long): List<ChatEntity> =
        chatDao.getAllChatsForCharacter(characterId)

    suspend fun getAllMessagesForChat(chatId: Long): List<MessageEntity> =
        messageDao.getAllActiveMessagesForChat(chatId)

    // Swipe alternatives
    suspend fun addSwipe(messageId: Long, newContent: String) = tx.run {
        val msg = messageDao.getMessageById(messageId) ?: return@run
        val swipes = SwipeUtils.parseSwipeContent(msg.swipeContent).toMutableList()
        if (swipes.isEmpty() || swipes.last() != msg.content) {
            swipes.add(msg.content)
        }
        swipes.add(newContent)
        val newIndex = swipes.size - 1
        messageDao.updateSwipe(messageId, SwipeUtils.toJsonArray(swipes), newIndex, newContent)
    }

    suspend fun switchSwipe(messageId: Long, newIndex: Int) = tx.run {
        val msg = messageDao.getMessageById(messageId) ?: return@run
        val swipes = SwipeUtils.parseSwipeContent(msg.swipeContent)
        if (newIndex < 0 || newIndex >= swipes.size) return@run
        messageDao.updateSwipeIndex(messageId, newIndex, swipes[newIndex])
    }

    suspend fun getSwipeCount(messageId: Long): Int {
        val msg = messageDao.getMessageById(messageId) ?: return 0
        val swipes = SwipeUtils.parseSwipeContent(msg.swipeContent)
        return if (swipes.isEmpty()) 1 else swipes.size
    }

    // 分支元数据操作
    fun getBranchesForChat(chatId: Long): Flow<List<BranchEntity>> = branchDao.getBranchesForChat(chatId)

    suspend fun getBranchesForChatSync(chatId: Long): List<BranchEntity> = branchDao.getBranchesForChatSync(chatId)

    suspend fun getDefaultBranch(chatId: Long): BranchEntity? = branchDao.getDefaultBranch(chatId)

    suspend fun getBranchById(id: Long): BranchEntity? = branchDao.getBranchById(id)

    suspend fun createBranch(chatId: Long, name: String, isDefault: Boolean = false): Long {
        return branchDao.insert(BranchEntity(chatId = chatId, name = name, isDefault = isDefault))
    }

    suspend fun updateBranch(branch: BranchEntity) = branchDao.update(branch)

    suspend fun deleteBranch(branch: BranchEntity) = branchDao.delete(branch)

    /**
     * 从指定消息创建分支：复制该消息及其之前的所有活跃消息到新分支
     */
    suspend fun createBranchFromMessage(chatId: Long, messageId: Long, branchName: String): Long {
        val branchId = createBranch(chatId, branchName)
        val messages = messageDao.getAllActiveMessagesForChat(chatId)
        val targetIndex = messages.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return branchId

        val messagesToCopy = messages.take(targetIndex + 1).filter { it.isActive }
        for (msg in messagesToCopy) {
            messageDao.insert(msg.copy(
                id = 0, // auto-generate new ID
                branchId = branchId,
                isActive = true
            ))
        }
        return branchId
    }
}
