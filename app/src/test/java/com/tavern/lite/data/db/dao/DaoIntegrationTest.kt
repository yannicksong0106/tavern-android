package com.tavern.lite.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tavern.lite.data.db.TavernDatabase
import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.SummaryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DaoIntegrationTest {

    private lateinit var db: TavernDatabase
    private lateinit var characterDao: CharacterDao
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var branchDao: BranchDao
    private lateinit var summaryDao: SummaryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TavernDatabase::class.java
        ).allowMainThreadQueries().build()
        characterDao = db.characterDao()
        chatDao = db.chatDao()
        messageDao = db.messageDao()
        branchDao = db.branchDao()
        summaryDao = db.summaryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ==================== CharacterDao ====================

    @Test
    fun `CharacterDao insert and getById`() = runTest {
        val id = characterDao.insert(CharacterEntity(name = "Alice"))
        val loaded = characterDao.getCharacterById(id)!!
        assertEquals("Alice", loaded.name)
    }

    @Test
    fun `CharacterDao getAllCharactersSync returns all`() = runTest {
        characterDao.insert(CharacterEntity(name = "Alice"))
        characterDao.insert(CharacterEntity(name = "Bob"))
        val all = characterDao.getAllCharactersSync()
        assertEquals(2, all.size)
    }

    @Test
    fun `CharacterDao update modifies fields`() = runTest {
        val id = characterDao.insert(CharacterEntity(name = "Alice"))
        val entity = characterDao.getCharacterById(id)!!
        characterDao.update(entity.copy(description = "Updated"))
        val loaded = characterDao.getCharacterById(id)!!
        assertEquals("Updated", loaded.description)
    }

    @Test
    fun `CharacterDao delete removes entity`() = runTest {
        val id = characterDao.insert(CharacterEntity(name = "Alice"))
        val entity = characterDao.getCharacterById(id)!!
        characterDao.delete(entity)
        assertNull(characterDao.getCharacterById(id))
    }

    @Test
    fun `CharacterDao deleteById removes entity`() = runTest {
        val id = characterDao.insert(CharacterEntity(name = "Alice"))
        characterDao.deleteById(id)
        assertNull(characterDao.getCharacterById(id))
    }

    // ==================== ChatDao ====================

    @Test
    fun `ChatDao insert and getById`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId, name = "Test Chat"))
        val loaded = chatDao.getChatById(chatId)!!
        assertEquals("Test Chat", loaded.name)
        assertEquals(charId, loaded.characterId)
    }

    @Test
    fun `ChatDao getChatsForCharacter returns correct chats`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        chatDao.insert(ChatEntity(characterId = charId, name = "Chat 1"))
        chatDao.insert(ChatEntity(characterId = charId, name = "Chat 2"))
        val chats = chatDao.getChatsForCharacter(charId).first()
        assertEquals(2, chats.size)
    }

    @Test
    fun `ChatDao deleteById cascades to messages`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Hello"))
        assertEquals(1, messageDao.getMessageCount(chatId))

        chatDao.deleteById(chatId)
        assertNull(chatDao.getChatById(chatId))
        assertEquals(0, messageDao.getMessageCount(chatId))
    }

    @Test
    fun `ChatDao renameChat updates name`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId, name = "Old"))
        chatDao.renameChat(chatId, "New")
        assertEquals("New", chatDao.getChatById(chatId)!!.name)
    }

    @Test
    fun `ChatDao updateBackground sets path`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        chatDao.updateBackground(chatId, "/path/to/bg.png")
        assertEquals("/path/to/bg.png", chatDao.getChatById(chatId)!!.backgroundPath)
    }

    @Test
    fun `ChatDao getLatestChatForCharacter returns most recent`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        chatDao.insert(ChatEntity(characterId = charId, name = "Old"))
        chatDao.insert(ChatEntity(characterId = charId, name = "New"))
        val latest = chatDao.getLatestChatForCharacter(charId)!!
        assertEquals("New", latest.name)
    }

    // ==================== MessageDao ====================

    @Test
    fun `MessageDao insert and getMessageById`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val msgId = messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Hello"))
        val loaded = messageDao.getMessageById(msgId)!!
        assertEquals("Hello", loaded.content)
        assertEquals("user", loaded.role)
    }

    @Test
    fun `MessageDao getMessageCount counts active only`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "A"))
        messageDao.insert(MessageEntity(chatId = chatId, role = "assistant", content = "B"))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "C", isActive = false))
        assertEquals(2, messageDao.getMessageCount(chatId))
    }

    @Test
    fun `MessageDao softDelete sets isActive false`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val msgId = messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Hello"))
        messageDao.softDelete(msgId)
        assertEquals(0, messageDao.getMessageCount(chatId))
        // getMessageById still returns it (no isActive filter)
        assertNotNull(messageDao.getMessageById(msgId))
    }

    @Test
    fun `MessageDao updateContent changes content`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val msgId = messageDao.insert(MessageEntity(chatId = chatId, role = "assistant", content = "Hi"))
        messageDao.updateContent(msgId, "Hello there")
        assertEquals("Hello there", messageDao.getMessageById(msgId)!!.content)
    }

    @Test
    fun `MessageDao appendContent concatenates`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val msgId = messageDao.insert(MessageEntity(chatId = chatId, role = "assistant", content = "Hello"))
        messageDao.appendContent(msgId, " world")
        assertEquals("Hello world", messageDao.getMessageById(msgId)!!.content)
    }

    @Test
    fun `MessageDao getRecentMessages returns DESC order with limit`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        for (i in 1..5) {
            messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Msg $i"))
        }
        val recent = messageDao.getRecentMessages(chatId, 3)
        assertEquals(3, recent.size)
        assertEquals("Msg 5", recent[0].content)
        assertEquals("Msg 4", recent[1].content)
        assertEquals("Msg 3", recent[2].content)
    }

    @Test
    fun `MessageDao getLastMessageForChat returns latest`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "First"))
        messageDao.insert(MessageEntity(chatId = chatId, role = "assistant", content = "Second"))
        val last = messageDao.getLastMessageForChat(chatId)!!
        assertEquals("Second", last.content)
    }

    @Test
    fun `MessageDao getLastUserMessage returns latest user msg`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Q1"))
        messageDao.insert(MessageEntity(chatId = chatId, role = "assistant", content = "A1"))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Q2"))
        val lastUser = messageDao.getLastUserMessage(chatId)!!
        assertEquals("Q2", lastUser.content)
    }

    @Test
    fun `MessageDao setPinned toggles pin state`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val msgId = messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Pin me"))
        messageDao.setPinned(msgId, true)
        val pinned = messageDao.getPinnedMessages(chatId).first()
        assertEquals(1, pinned.size)
        assertEquals("Pin me", pinned[0].content)
    }

    @Test
    fun `MessageDao updateSwipe stores alternatives`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val msgId = messageDao.insert(MessageEntity(chatId = chatId, role = "assistant", content = "V1"))
        messageDao.updateSwipe(msgId, """["V1","V2","V3"]""", 2, "V3")
        val loaded = messageDao.getMessageById(msgId)!!
        assertEquals("V3", loaded.content)
        assertEquals(2, loaded.swipeIndex)
        assertEquals("""["V1","V2","V3"]""", loaded.swipeContent)
    }

    @Test
    fun `MessageDao branch operations activate and deactivate`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "A", branchId = 1))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "B", branchId = 2))
        assertEquals(2, messageDao.getMessageCount(chatId))

        messageDao.deactivateAllMessages(chatId)
        assertEquals(0, messageDao.getMessageCount(chatId))

        messageDao.activateBranch(chatId, 1)
        assertEquals(1, messageDao.getMessageCount(chatId))
        val active = messageDao.getAllActiveMessagesForChat(chatId)
        assertEquals("A", active[0].content)
    }

    @Test
    fun `MessageDao getMessagesForChat Flow emits on change`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val messages = messageDao.getMessagesForChat(chatId).first()
        assertTrue(messages.isEmpty())

        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Hello"))
        val after = messageDao.getMessagesForChat(chatId).first()
        assertEquals(1, after.size)
    }

    // ==================== BranchDao ====================

    @Test
    fun `BranchDao insert and getBranchesForChatSync`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        branchDao.insert(BranchEntity(chatId = chatId, name = "main", isDefault = true))
        branchDao.insert(BranchEntity(chatId = chatId, name = "alt"))
        val branches = branchDao.getBranchesForChatSync(chatId)
        assertEquals(2, branches.size)
    }

    @Test
    fun `BranchDao getDefaultBranch returns default`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        branchDao.insert(BranchEntity(chatId = chatId, name = "main", isDefault = true))
        branchDao.insert(BranchEntity(chatId = chatId, name = "alt", isDefault = false))
        val defaultBranch = branchDao.getDefaultBranch(chatId)!!
        assertEquals("main", defaultBranch.name)
    }

    @Test
    fun `BranchDao delete removes branch`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val id = branchDao.insert(BranchEntity(chatId = chatId, name = "temp"))
        val branch = branchDao.getBranchById(id)!!
        branchDao.delete(branch)
        assertNull(branchDao.getBranchById(id))
    }

    // ==================== SummaryDao ====================

    @Test
    fun `SummaryDao insert and getSummariesForChat`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        summaryDao.insert(SummaryEntity(chatId = chatId, content = "Summary 1", messageRangeStart = 1, messageRangeEnd = 10))
        summaryDao.insert(SummaryEntity(chatId = chatId, content = "Summary 2", messageRangeStart = 11, messageRangeEnd = 20))
        val summaries = summaryDao.getSummariesForChat(chatId).first()
        assertEquals(2, summaries.size)
    }

    @Test
    fun `SummaryDao getLatestSummary returns most recent`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        summaryDao.insert(SummaryEntity(chatId = chatId, content = "Old", messageRangeStart = 1, messageRangeEnd = 10))
        summaryDao.insert(SummaryEntity(chatId = chatId, content = "New", messageRangeStart = 11, messageRangeEnd = 20))
        val latest = summaryDao.getLatestSummary(chatId)!!
        assertEquals("New", latest.content)
    }

    @Test
    fun `SummaryDao updateContent changes content`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val id = summaryDao.insert(SummaryEntity(chatId = chatId, content = "Old", messageRangeStart = 1, messageRangeEnd = 10))
        summaryDao.updateContent(id, "Updated")
        assertEquals("Updated", summaryDao.getSummaryById(id)!!.content)
    }

    @Test
    fun `SummaryDao deleteById removes summary`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        val id = summaryDao.insert(SummaryEntity(chatId = chatId, content = "Temp", messageRangeStart = 1, messageRangeEnd = 10))
        summaryDao.deleteById(id)
        assertNull(summaryDao.getSummaryById(id))
    }

    @Test
    fun `SummaryDao getCountForChat returns correct count`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        assertEquals(0, summaryDao.getCountForChat(chatId))
        summaryDao.insert(SummaryEntity(chatId = chatId, content = "A", messageRangeStart = 1, messageRangeEnd = 5))
        summaryDao.insert(SummaryEntity(chatId = chatId, content = "B", messageRangeStart = 6, messageRangeEnd = 10))
        assertEquals(2, summaryDao.getCountForChat(chatId))
    }

    // ==================== Cascade delete ====================

    @Test
    fun `Cascade delete removes messages and summaries when chat deleted`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        val chatId = chatDao.insert(ChatEntity(characterId = charId))
        messageDao.insert(MessageEntity(chatId = chatId, role = "user", content = "Hi"))
        summaryDao.insert(SummaryEntity(chatId = chatId, content = "Sum", messageRangeStart = 1, messageRangeEnd = 1))
        branchDao.insert(BranchEntity(chatId = chatId, name = "main"))

        chatDao.deleteById(chatId)
        assertEquals(0, messageDao.getMessageCount(chatId))
        assertEquals(0, summaryDao.getCountForChat(chatId))
        assertTrue(branchDao.getBranchesForChatSync(chatId).isEmpty())
    }

    @Test
    fun `Cascade delete removes chats when character deleted`() = runTest {
        val charId = characterDao.insert(CharacterEntity(name = "Alice"))
        chatDao.insert(ChatEntity(characterId = charId, name = "Chat 1"))
        chatDao.insert(ChatEntity(characterId = charId, name = "Chat 2"))
        assertEquals(2, chatDao.getAllChatsForCharacter(charId).size)

        characterDao.deleteById(charId)
        assertEquals(0, chatDao.getAllChatsForCharacter(charId).size)
    }
}
