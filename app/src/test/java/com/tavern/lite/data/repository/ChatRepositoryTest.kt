package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.BranchDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.ChatWithLastMessage
import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatRepositoryTest {

    private lateinit var repository: ChatRepository
    private lateinit var fakeChatDao: FakeChatDao
    private lateinit var fakeMessageDao: FakeMessageDao

    @Before
    fun setup() {
        fakeChatDao = FakeChatDao()
        fakeMessageDao = FakeMessageDao()
        repository = ChatRepository(fakeChatDao, fakeMessageDao, FakeBranchDao())
    }

    @Test
    fun `createChat inserts chat and returns id`() = runTest {
        val id = repository.createChat(1, "Test Chat")
        assertEquals(1L, id)
        assertEquals(1, fakeChatDao.insertedChats.size)
        assertEquals("Test Chat", fakeChatDao.insertedChats[0].name)
        assertEquals(1L, fakeChatDao.insertedChats[0].characterId)
    }

    @Test
    fun `sendMessage inserts message and updates chat timestamp`() = runTest {
        fakeChatDao.insertedChats.add(ChatEntity(id = 1, characterId = 1))
        val msgId = repository.sendMessage(1, "Hello", "user")
        assertEquals(1L, msgId)
        assertEquals(1, fakeMessageDao.insertedMessages.size)
        assertEquals("Hello", fakeMessageDao.insertedMessages[0].content)
        assertEquals("user", fakeMessageDao.insertedMessages[0].role)
        assertTrue(fakeChatDao.timestampUpdated)
    }

    @Test
    fun `sendMessage with replyToId passes through`() = runTest {
        val msgId = repository.sendMessage(1, "Reply", "user", replyToId = 42)
        assertEquals(42L, fakeMessageDao.insertedMessages[0].replyToId)
    }

    @Test
    fun `sendMessage with characterId passes through`() = runTest {
        val msgId = repository.sendMessage(1, "Character msg", "assistant", characterId = 5)
        assertEquals(5L, fakeMessageDao.insertedMessages[0].characterId)
    }

    @Test
    fun `deleteMessage calls softDelete`() = runTest {
        fakeMessageDao.messages[1] = MessageEntity(id = 1, chatId = 1, role = "user", content = "test")
        repository.deleteMessage(1)
        assertTrue(fakeMessageDao.softDeletedIds.contains(1))
    }

    @Test
    fun `togglePinMessage sets pinned state`() = runTest {
        fakeMessageDao.messages[1] = MessageEntity(id = 1, chatId = 1, role = "user", content = "test")
        repository.togglePinMessage(1, true)
        assertEquals(true, fakeMessageDao.pinnedStates[1])
    }

    @Test
    fun `getSwipeCount returns 1 for message with no swipes`() = runTest {
        fakeMessageDao.messages[1] = MessageEntity(id = 1, chatId = 1, role = "assistant", content = "original")
        val count = repository.getSwipeCount(1)
        assertEquals(1, count)
    }

    @Test
    fun `addSwipe adds alternative content`() = runTest {
        fakeMessageDao.messages[1] = MessageEntity(id = 1, chatId = 1, role = "assistant", content = "original")
        repository.addSwipe(1, "alternative")
        assertNotNull(fakeMessageDao.updatedSwipe)
        assertEquals(1, fakeMessageDao.updatedSwipeIndex)
    }

    @Test
    fun `switchSwipe updates content to target swipe`() = runTest {
        fakeMessageDao.messages[1] = MessageEntity(
            id = 1, chatId = 1, role = "assistant", content = "alt1",
            swipeContent = "[\"original\",\"alt1\"]", swipeIndex = 1
        )
        repository.switchSwipe(1, 0)
        assertEquals(0, fakeMessageDao.updatedSwipeIndex)
        assertEquals("original", fakeMessageDao.updatedSwipeContent)
    }

    @Test
    fun `switchSwipe does nothing for invalid index`() = runTest {
        fakeMessageDao.messages[1] = MessageEntity(
            id = 1, chatId = 1, role = "assistant", content = "test",
            swipeContent = "[\"test\"]", swipeIndex = 0
        )
        repository.switchSwipe(1, 5)
        // No update should happen
        assertNull(fakeMessageDao.updatedSwipeContent)
    }

}

// === Fake DAOs ===

private class FakeChatDao : ChatDao {
    val insertedChats = mutableListOf<ChatEntity>()
    var timestampUpdated = false

    override fun getChatsForCharacter(characterId: Long): Flow<List<ChatEntity>> = flowOf(emptyList())
    override fun getChatsWithLastMessage(characterId: Long): Flow<List<ChatWithLastMessage>> = flowOf(emptyList())
    override fun getAllGroupChats(): Flow<List<ChatEntity>> = flowOf(emptyList())
    override suspend fun getChatById(id: Long): ChatEntity? = insertedChats.find { it.id == id }
    override suspend fun getLatestChatForCharacter(characterId: Long): ChatEntity? = insertedChats.lastOrNull { it.characterId == characterId }
    override suspend fun insert(chat: ChatEntity): Long {
        val id = (insertedChats.maxOfOrNull { it.id } ?: 0) + 1
        insertedChats.add(chat.copy(id = id))
        return id
    }
    override suspend fun delete(chat: ChatEntity) { insertedChats.removeIf { it.id == chat.id } }
    override suspend fun deleteById(chatId: Long) { insertedChats.removeIf { it.id == chatId } }
    override suspend fun updateTimestamp(chatId: Long, timestamp: Long) { timestampUpdated = true }
    override suspend fun renameChat(chatId: Long, name: String) {}
    override suspend fun updateBackground(chatId: Long, path: String?) {}
    override suspend fun updateGroupChattiness(chatId: Long, chattiness: Int) {}
    override suspend fun getAllChatsForCharacter(characterId: Long): List<ChatEntity> = insertedChats.filter { it.characterId == characterId }
    override suspend fun getRecentChats(limit: Int): List<ChatEntity> = insertedChats.take(limit)
    override suspend fun getAllChatsSync(): List<ChatEntity> = insertedChats.toList()
}

private class FakeMessageDao : MessageDao {
    val messages = mutableMapOf<Long, MessageEntity>()
    val insertedMessages = mutableListOf<MessageEntity>()
    val softDeletedIds = mutableListOf<Long>()
    val pinnedStates = mutableMapOf<Long, Boolean>()
    var updatedSwipe: String? = null
    var updatedSwipeIndex: Int? = null
    var updatedSwipeContent: String? = null
    private var nextId = 1L

    override fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
    override suspend fun getRecentMessages(chatId: Long, limit: Int): List<MessageEntity> =
        messages.values.filter { it.chatId == chatId && it.isActive }.sortedByDescending { it.createdAt }.take(limit)
    override suspend fun getLastMessageForChat(chatId: Long): MessageEntity? =
        messages.values.filter { it.chatId == chatId && it.isActive }.maxByOrNull { it.createdAt }
    override suspend fun getMessageById(id: Long): MessageEntity? = messages[id]
    override suspend fun getMessageCount(chatId: Long): Int =
        messages.values.count { it.chatId == chatId && it.isActive }
    override suspend fun insert(message: MessageEntity): Long {
        val id = nextId++
        val msg = message.copy(id = id)
        messages[id] = msg
        insertedMessages.add(msg)
        return id
    }
    override suspend fun updateContent(id: Long, content: String) {
        messages[id]?.let { messages[id] = it.copy(content = content) }
    }
    override suspend fun appendContent(id: Long, chunk: String) {
        messages[id]?.let { messages[id] = it.copy(content = it.content + chunk) }
    }
    override suspend fun softDelete(id: Long) {
        softDeletedIds.add(id)
        messages[id]?.let { messages[id] = it.copy(isActive = false) }
    }
    override suspend fun delete(message: MessageEntity) { messages.remove(message.id) }
    override suspend fun deleteAllForChat(chatId: Long) { messages.values.removeIf { it.chatId == chatId } }
    override suspend fun getBranchIds(chatId: Long): List<Long?> = emptyList()
    override suspend fun deactivateAllMessages(chatId: Long) {}
    override suspend fun activateBranch(chatId: Long, branchId: Long) {}
    override suspend fun updateSwipe(id: Long, swipeJson: String, swipeIndex: Int, currentContent: String) {
        updatedSwipe = swipeJson
        updatedSwipeIndex = swipeIndex
        updatedSwipeContent = currentContent
    }
    override suspend fun updateSwipeIndex(id: Long, swipeIndex: Int, currentContent: String) {
        updatedSwipeIndex = swipeIndex
        updatedSwipeContent = currentContent
    }
    override suspend fun getAllMessages(): List<MessageEntity> = messages.values.toList()
    override suspend fun setPinned(messageId: Long, pinned: Boolean) { pinnedStates[messageId] = pinned }
    override fun getPinnedMessages(chatId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
}

private class FakeBranchDao : BranchDao {
    override fun getBranchesForChat(chatId: Long): Flow<List<BranchEntity>> = flowOf(emptyList())
    override suspend fun getBranchesForChatSync(chatId: Long): List<BranchEntity> = emptyList()
    override suspend fun getDefaultBranch(chatId: Long): BranchEntity? = null
    override suspend fun getBranchById(id: Long): BranchEntity? = null
    override suspend fun insert(branch: BranchEntity): Long = 1L
    override suspend fun update(branch: BranchEntity) {}
    override suspend fun delete(branch: BranchEntity) {}
    override suspend fun deleteAllForChat(chatId: Long) {}
}
