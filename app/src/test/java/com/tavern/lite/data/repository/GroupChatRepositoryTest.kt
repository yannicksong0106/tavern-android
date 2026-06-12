package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.ChatCharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatCharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GroupChatRepositoryTest {

    private lateinit var repository: GroupChatRepository
    private lateinit var fakeChatDao: FakeGroupChatDao
    private lateinit var fakeChatCharacterDao: FakeChatCharacterDao
    private lateinit var fakeMessageDao: FakeGroupMessageDao

    @Before
    fun setup() {
        fakeChatDao = FakeGroupChatDao()
        fakeChatCharacterDao = FakeChatCharacterDao()
        fakeMessageDao = FakeGroupMessageDao()
        repository = GroupChatRepository(fakeChatDao, fakeChatCharacterDao, fakeMessageDao)
    }

    @Test
    fun `createGroupChat creates chat with first character as primary`() = runTest {
        val chatId = repository.createGroupChat(listOf(1, 2, 3), "My Group")
        assertEquals(1L, chatId)
        assertEquals(1, fakeChatDao.inserted.size)
        val chat = fakeChatDao.inserted[0]
        assertEquals(1L, chat.characterId) // first character is primary
        assertEquals(true, chat.isGroup)
        assertEquals("My Group", chat.name)
    }

    @Test
    fun `createGroupChat inserts chat_characters for all characters`() = runTest {
        repository.createGroupChat(listOf(10, 20, 30))
        assertEquals(3, fakeChatCharacterDao.inserted.size)
        assertEquals(10L, fakeChatCharacterDao.inserted[0].characterId)
        assertEquals(20L, fakeChatCharacterDao.inserted[1].characterId)
        assertEquals(30L, fakeChatCharacterDao.inserted[2].characterId)
        // Display order should be 0, 1, 2
        assertEquals(0, fakeChatCharacterDao.inserted[0].displayOrder)
        assertEquals(1, fakeChatCharacterDao.inserted[1].displayOrder)
        assertEquals(2, fakeChatCharacterDao.inserted[2].displayOrder)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createGroupChat requires at least 2 characters`() = runTest {
        repository.createGroupChat(listOf(1))
    }

    @Test
    fun `addCharacter adds to existing group chat`() = runTest {
        fakeChatCharacterDao.existingCharacters.add(ChatCharacterEntity(chatId = 1, characterId = 1, displayOrder = 0))
        repository.addCharacter(1, 2)
        assertEquals(2, fakeChatCharacterDao.inserted.size + fakeChatCharacterDao.existingCharacters.size)
    }

    @Test
    fun `removeCharacter calls dao`() = runTest {
        repository.removeCharacter(1, 2)
        assertEquals(1L, fakeChatCharacterDao.removedChatId)
        assertEquals(2L, fakeChatCharacterDao.removedCharacterId)
    }

    @Test
    fun `isGroupChat returns true for group chat`() = runTest {
        fakeChatDao.inserted.add(ChatEntity(id = 1, characterId = 1, isGroup = true))
        assertTrue(repository.isGroupChat(1))
    }

    @Test
    fun `isGroupChat returns false for non-group chat`() = runTest {
        fakeChatDao.inserted.add(ChatEntity(id = 1, characterId = 1, isGroup = false))
        assertEquals(false, repository.isGroupChat(1))
    }

    @Test
    fun `getCharacterCount returns count`() = runTest {
        fakeChatCharacterDao.characterCount = 3
        assertEquals(3, repository.getCharacterCount(1))
    }

    @Test
    fun `updateCharacterChattiness calls dao`() = runTest {
        repository.updateCharacterChattiness(1, 2, 75)
        assertEquals(Triple(1L, 2L, 75), fakeChatCharacterDao.lastChattinessUpdate)
    }
}

private class FakeGroupChatDao : ChatDao {
    val inserted = mutableListOf<ChatEntity>()
    private var nextId = 1L

    override fun getChatsForCharacter(characterId: Long): Flow<List<ChatEntity>> = flowOf(emptyList())
    override fun getChatsWithLastMessage(characterId: Long): Flow<List<com.tavern.lite.data.db.entity.ChatWithLastMessage>> = flowOf(emptyList())
    override fun getAllGroupChats(): Flow<List<ChatEntity>> = flowOf(inserted.filter { it.isGroup })
    override fun getAllChats(): Flow<List<ChatEntity>> = flowOf(inserted)
    override suspend fun getChatById(id: Long): ChatEntity? = inserted.find { it.id == id }
    override suspend fun getLatestChatForCharacter(characterId: Long): ChatEntity? = null
    override suspend fun insert(chat: ChatEntity): Long {
        val id = nextId++
        inserted.add(chat.copy(id = id))
        return id
    }
    override suspend fun delete(chat: ChatEntity) { inserted.removeIf { it.id == chat.id } }
    override suspend fun deleteById(chatId: Long) { inserted.removeIf { it.id == chatId } }
    override suspend fun updateTimestamp(chatId: Long, timestamp: Long) {}
    override suspend fun renameChat(chatId: Long, name: String) {}
    override suspend fun updateBackground(chatId: Long, path: String?) {}
    override suspend fun updateGroupChattiness(chatId: Long, chattiness: Int) {}
    override suspend fun getAllChatsForCharacter(characterId: Long): List<ChatEntity> = emptyList()
    override suspend fun getRecentChats(limit: Int): List<ChatEntity> = emptyList()
    override suspend fun getAllChatsSync(): List<ChatEntity> = inserted.toList()
    override suspend fun updateSchedulingStrategy(chatId: Long, strategy: String) {}
    override suspend fun updateMessageInterval(chatId: Long, intervalMs: Long) {}
}

private class FakeChatCharacterDao : ChatCharacterDao {
    val inserted = mutableListOf<ChatCharacterEntity>()
    val existingCharacters = mutableListOf<ChatCharacterEntity>()
    var removedChatId: Long? = null
    var removedCharacterId: Long? = null
    var characterCount = 0
    var lastChattinessUpdate: Triple<Long, Long, Int>? = null

    override suspend fun insert(chatCharacter: ChatCharacterEntity): Long {
        inserted.add(chatCharacter)
        return (inserted.size).toLong()
    }
    override suspend fun insertAll(chatCharacters: List<ChatCharacterEntity>) { inserted.addAll(chatCharacters) }
    override suspend fun getCharactersForChat(chatId: Long): List<ChatCharacterEntity> = existingCharacters.filter { it.chatId == chatId }
    override suspend fun getAllChatCharacters(): List<ChatCharacterEntity> = existingCharacters + inserted
    override fun getCharacterEntitiesForChat(chatId: Long): Flow<List<CharacterEntity>> = flowOf(emptyList())
    override suspend fun getCharacterEntitiesForChatSync(chatId: Long): List<CharacterEntity> = emptyList()
    override suspend fun removeCharacter(chatId: Long, characterId: Long) { removedChatId = chatId; removedCharacterId = characterId }
    override suspend fun removeAllCharacters(chatId: Long) {}
    override suspend fun updateOrder(chatId: Long, characterId: Long, order: Int) {}
    override suspend fun updateChattiness(chatId: Long, characterId: Long, chattiness: Int) { lastChattinessUpdate = Triple(chatId, characterId, chattiness) }
    override suspend fun getCharacterCount(chatId: Long): Int = characterCount
}

private class FakeGroupMessageDao : MessageDao {
    override fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
    override suspend fun getRecentMessages(chatId: Long, limit: Int): List<MessageEntity> = emptyList()
    override suspend fun getLastMessageForChat(chatId: Long): MessageEntity? = null
    override suspend fun getLastUserMessage(chatId: Long): MessageEntity? = null
    override suspend fun getMessageById(id: Long): MessageEntity? = null
    override suspend fun getMessageCount(chatId: Long): Int = 0
    override suspend fun insert(message: MessageEntity): Long = 1
    override suspend fun insertAll(messages: List<MessageEntity>) {}
    override suspend fun updateContent(id: Long, content: String) {}
    override suspend fun appendContent(id: Long, chunk: String) {}
    override suspend fun softDelete(id: Long) {}
    override suspend fun delete(message: MessageEntity) {}
    override suspend fun deleteAllForChat(chatId: Long) {}
    override suspend fun getBranchIds(chatId: Long): List<Long?> = emptyList()
    override suspend fun deactivateAllMessages(chatId: Long) {}
    override suspend fun activateBranch(chatId: Long, branchId: Long) {}
    override suspend fun updateSwipe(id: Long, swipeJson: String, swipeIndex: Int, currentContent: String) {}
    override suspend fun updateSwipeIndex(id: Long, swipeIndex: Int, currentContent: String) {}
    override suspend fun getAllActiveMessagesForChat(chatId: Long): List<MessageEntity> = emptyList()
    override suspend fun getAllMessages(): List<MessageEntity> = emptyList()
    override suspend fun setPinned(messageId: Long, pinned: Boolean) {}
    override fun getPinnedMessages(chatId: Long): Flow<List<MessageEntity>> = flowOf(emptyList())
}
