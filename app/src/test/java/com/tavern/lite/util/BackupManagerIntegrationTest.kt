package com.tavern.lite.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tavern.lite.data.db.TavernDatabase
import com.tavern.lite.data.db.entity.BgmEntity
import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.db.entity.CharacterPersonaEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatCharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import com.tavern.lite.data.db.entity.SummaryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BackupManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var sourceDb: TavernDatabase
    private lateinit var restoredDb: TavernDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sourceDb = createDatabase()
        restoredDb = createDatabase()
    }

    @After
    fun teardown() {
        sourceDb.close()
        restoredDb.close()
    }

    @Test
    fun `backup then restore preserves image message paths and BGM emotion`() = runTest {
        sourceDb.characterDao().insert(
            CharacterEntity(
                id = CHARACTER_ID,
                name = "Alice",
                presetId = CHARACTER_PRESET_ID,
                createdAt = 100,
                updatedAt = 100
            )
        )
        sourceDb.characterDao().insert(
            CharacterEntity(
                id = SECOND_CHARACTER_ID,
                name = "Bob",
                createdAt = 101,
                updatedAt = 101
            )
        )
        sourceDb.personaDao().insert(
            PersonaEntity(
                id = PERSONA_ID,
                name = "Story persona",
                biography = "Keeps continuity",
                createdAt = 105
            )
        )
        sourceDb.personaDao().linkCharacterPersona(
            CharacterPersonaEntity(
                characterId = CHARACTER_ID,
                personaId = PERSONA_ID
            )
        )
        sourceDb.chatDao().insert(
            ChatEntity(
                id = CHAT_ID,
                characterId = CHARACTER_ID,
                name = "Main chat",
                isGroup = true,
                presetId = CHAT_PRESET_ID,
                createdAt = 110,
                updatedAt = 120
            )
        )
        sourceDb.chatCharacterDao().insertAll(
            listOf(
                ChatCharacterEntity(
                    id = CHAT_CHARACTER_ID,
                    chatId = CHAT_ID,
                    characterId = CHARACTER_ID,
                    displayOrder = 0,
                    chattiness = 80,
                    createdAt = 121
                ),
                ChatCharacterEntity(
                    id = SECOND_CHAT_CHARACTER_ID,
                    chatId = CHAT_ID,
                    characterId = SECOND_CHARACTER_ID,
                    displayOrder = 1,
                    chattiness = 40,
                    createdAt = 122
                )
            )
        )
        sourceDb.branchDao().insert(
            BranchEntity(
                id = BRANCH_ID,
                chatId = CHAT_ID,
                name = "Main branch",
                isDefault = true,
                createdAt = 123
            )
        )
        sourceDb.messageDao().insert(
            MessageEntity(
                id = PARENT_MESSAGE_ID,
                chatId = CHAT_ID,
                role = "user",
                content = "parent",
                branchId = BRANCH_ID,
                createdAt = 125
            )
        )
        sourceDb.messageDao().insert(
            MessageEntity(
                id = MESSAGE_ID,
                chatId = CHAT_ID,
                role = "user",
                content = "see attached",
                branchId = BRANCH_ID,
                replyToId = PARENT_MESSAGE_ID,
                isPinned = true,
                createdAt = 130,
                imagePaths = """["/images/a.png","/images/b.png"]"""
            )
        )
        sourceDb.summaryDao().insert(
            SummaryEntity(
                id = SUMMARY_ID,
                chatId = CHAT_ID,
                content = "The conversation starts with an image.",
                messageRangeStart = PARENT_MESSAGE_ID,
                messageRangeEnd = MESSAGE_ID,
                tokenCount = 42,
                createdAt = 135
            )
        )
        sourceDb.bgmDao().insert(
            BgmEntity(
                id = BGM_ID,
                characterId = CHARACTER_ID,
                name = "Happy theme",
                audioPath = "/audio/happy.mp3",
                emotion = "happy",
                displayOrder = 2,
                createdAt = 140
            )
        )
        sourceDb.quickReplyDao().insertSet(
            QuickReplySetEntity(
                id = QUICK_REPLY_SET_ID,
                name = "Chat automations",
                scope = "chat",
                chatId = CHAT_ID,
                displayOrder = 3,
                createdAt = 150,
                updatedAt = 151
            )
        )
        sourceDb.quickReplyDao().insertReply(
            QuickReplyEntity(
                id = QUICK_REPLY_ID,
                setId = QUICK_REPLY_SET_ID,
                label = "Open",
                script = "/setvar mood calm\n/input {{mood}}",
                icon = "!",
                automationId = "chat_open",
                requiresConfirmation = true,
                allowAutoRun = true,
                canSendMessages = false,
                canTriggerGeneration = false,
                displayOrder = 4
            )
        )

        val backupFile = manager(sourceDb).backup().getOrThrow()
        val result = backupFile.inputStream().use { manager(restoredDb).restore(it).getOrThrow() }

        assertEquals(2, result.charactersRestored)
        assertEquals(1, result.chatsRestored)
        assertEquals(2, result.chatCharactersRestored)
        assertEquals(1, result.characterPersonasRestored)
        assertEquals(1, result.branchesRestored)
        assertEquals(1, result.summariesRestored)
        assertEquals(2, result.messagesRestored)
        assertEquals(1, result.bgmsRestored)
        assertEquals(1, result.quickReplySetsRestored)
        assertEquals(1, result.quickRepliesRestored)

        val restoredCharacter = restoredDb.characterDao().getCharacterById(CHARACTER_ID)
        assertEquals(CHARACTER_PRESET_ID, restoredCharacter?.presetId)

        val restoredChat = restoredDb.chatDao().getChatById(CHAT_ID)
        assertEquals(CHAT_PRESET_ID, restoredChat?.presetId)

        val restoredMessage = restoredDb.messageDao().getMessageById(MESSAGE_ID)
        assertEquals("""["/images/a.png","/images/b.png"]""", restoredMessage?.imagePaths)
        assertEquals(BRANCH_ID, restoredMessage?.branchId)
        assertEquals(PARENT_MESSAGE_ID, restoredMessage?.replyToId)
        assertEquals(true, restoredMessage?.isPinned)

        val restoredChatCharacters = restoredDb.chatCharacterDao().getCharactersForChat(CHAT_ID)
        assertEquals(listOf(CHARACTER_ID, SECOND_CHARACTER_ID), restoredChatCharacters.map { it.characterId })
        assertEquals(listOf(80, 40), restoredChatCharacters.map { it.chattiness })

        assertEquals(PERSONA_ID, restoredDb.personaDao().getLinkedPersonaId(CHARACTER_ID))
        assertEquals("Main branch", restoredDb.branchDao().getBranchById(BRANCH_ID)?.name)

        val restoredSummary = restoredDb.summaryDao().getSummaryById(SUMMARY_ID)
        assertEquals("The conversation starts with an image.", restoredSummary?.content)
        assertEquals(42, restoredSummary?.tokenCount)

        val restoredBgm = restoredDb.bgmDao().getBgmById(BGM_ID)
        assertEquals("happy", restoredBgm?.emotion)
        assertEquals("/audio/happy.mp3", restoredBgm?.audioPath)

        val restoredQuickReplySet = restoredDb.quickReplyDao().getSetById(QUICK_REPLY_SET_ID)
        assertEquals("Chat automations", restoredQuickReplySet?.name)
        assertEquals("chat", restoredQuickReplySet?.scope)
        assertEquals(CHAT_ID, restoredQuickReplySet?.chatId)

        val restoredQuickReplies = restoredDb.quickReplyDao().getEnabledRepliesForSet(QUICK_REPLY_SET_ID)
        assertEquals(1, restoredQuickReplies.size)
        assertEquals("chat_open", restoredQuickReplies.single().automationId)
        assertEquals("/setvar mood calm\n/input {{mood}}", restoredQuickReplies.single().script)
        assertEquals(true, restoredQuickReplies.single().requiresConfirmation)
        assertEquals(true, restoredQuickReplies.single().allowAutoRun)
    }

    @Test
    fun `restore accepts legacy backups without image paths or BGM emotion`() = runTest {
        val legacyBackup = """
            {
              "version": 1,
              "timestamp": 1000,
              "appVersion": "unknown",
              "characters": [
                {
                  "id": $CHARACTER_ID,
                  "name": "Legacy",
                  "createdAt": 100,
                  "updatedAt": 100
                }
              ],
              "chats": [
                {
                  "id": $CHAT_ID,
                  "characterId": $CHARACTER_ID,
                  "createdAt": 110,
                  "updatedAt": 120
                }
              ],
              "messages": [
                {
                  "id": $MESSAGE_ID,
                  "chatId": $CHAT_ID,
                  "role": "user",
                  "content": "legacy image",
                  "createdAt": 130
                }
              ],
              "bgms": [
                {
                  "id": $BGM_ID,
                  "characterId": $CHARACTER_ID,
                  "name": "Legacy theme",
                  "audioPath": "/audio/legacy.mp3",
                  "createdAt": 140
                }
              ]
            }
        """.trimIndent()

        val result = legacyBackup.byteInputStream().use { manager(restoredDb).restore(it).getOrThrow() }

        assertEquals(1, result.messagesRestored)
        assertEquals(1, result.bgmsRestored)
        assertEquals(null, restoredDb.characterDao().getCharacterById(CHARACTER_ID)?.presetId)
        assertEquals(null, restoredDb.chatDao().getChatById(CHAT_ID)?.presetId)
        assertEquals("[]", restoredDb.messageDao().getMessageById(MESSAGE_ID)?.imagePaths)
        assertEquals(null, restoredDb.messageDao().getMessageById(MESSAGE_ID)?.replyToId)
        assertEquals(false, restoredDb.messageDao().getMessageById(MESSAGE_ID)?.isPinned)
        assertEquals("", restoredDb.bgmDao().getBgmById(BGM_ID)?.emotion)
    }

    @Test
    fun `backup then restore handles large chat history within performance budget`() = runTest {
        sourceDb.characterDao().insert(
            CharacterEntity(
                id = CHARACTER_ID,
                name = "Large History",
                createdAt = 100,
                updatedAt = 100
            )
        )
        sourceDb.chatDao().insert(
            ChatEntity(
                id = CHAT_ID,
                characterId = CHARACTER_ID,
                name = "1200 message chat",
                createdAt = 110,
                updatedAt = 120
            )
        )
        sourceDb.messageDao().insertAll(
            (1..LARGE_MESSAGE_COUNT).map { index ->
                MessageEntity(
                    id = LARGE_MESSAGE_ID_BASE + index,
                    chatId = CHAT_ID,
                    role = if (index % 2 == 0) "assistant" else "user",
                    content = "Message $index with enough body to resemble a real chat turn.",
                    characterId = if (index % 2 == 0) CHARACTER_ID else null,
                    createdAt = 1_000L + index,
                    isPinned = index % 250 == 0,
                    imagePaths = if (index % 300 == 0) """["/images/large-$index.png"]""" else "[]"
                )
            }
        )

        var restoredMessages = 0
        val elapsedMs = measureTimeMillis {
            val backupFile = manager(sourceDb).backup().getOrThrow()
            assertTrue(backupFile.length() > 0)

            val result = backupFile.inputStream().use { manager(restoredDb).restore(it).getOrThrow() }
            restoredMessages = result.messagesRestored
        }

        assertTrue("Large backup/restore took ${elapsedMs}ms", elapsedMs < LARGE_BACKUP_BUDGET_MS)
        assertEquals(LARGE_MESSAGE_COUNT, restoredMessages)
        assertEquals(LARGE_MESSAGE_COUNT, restoredDb.messageDao().getMessageCount(CHAT_ID))

        val restored = restoredDb.messageDao().getAllActiveMessagesForChat(CHAT_ID)
        assertEquals("Message 1 with enough body to resemble a real chat turn.", restored.first().content)
        assertEquals(
            "Message $LARGE_MESSAGE_COUNT with enough body to resemble a real chat turn.",
            restored.last().content
        )
        assertEquals("""["/images/large-1200.png"]""", restored.last().imagePaths)
        assertEquals(4, restored.count { it.isPinned })
    }

    private fun createDatabase(): TavernDatabase =
        Room.inMemoryDatabaseBuilder(context, TavernDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun manager(db: TavernDatabase): BackupManager =
        BackupManager(
            context = context,
            db = db,
            characterDao = db.characterDao(),
            chatDao = db.chatDao(),
            chatCharacterDao = db.chatCharacterDao(),
            messageDao = db.messageDao(),
            memoryDao = db.memoryDao(),
            memoryAtomDao = db.memoryAtomDao(),
            worldBookDao = db.worldBookDao(),
            scriptDao = db.scriptDao(),
            authorNoteDao = db.authorNoteDao(),
            personaDao = db.personaDao(),
            presetDao = db.presetDao(),
            branchDao = db.branchDao(),
            summaryDao = db.summaryDao(),
            spriteDao = db.spriteDao(),
            bgmDao = db.bgmDao(),
            quickReplyDao = db.quickReplyDao()
        )

    private companion object {
        const val CHARACTER_ID = 1L
        const val SECOND_CHARACTER_ID = 3L
        const val CHARACTER_PRESET_ID = 2L
        const val PERSONA_ID = 4L
        const val CHAT_ID = 10L
        const val CHAT_PRESET_ID = 20L
        const val CHAT_CHARACTER_ID = 30L
        const val SECOND_CHAT_CHARACTER_ID = 31L
        const val BRANCH_ID = 40L
        const val PARENT_MESSAGE_ID = 99L
        const val MESSAGE_ID = 100L
        const val LARGE_MESSAGE_ID_BASE = 1_000L
        const val LARGE_MESSAGE_COUNT = 1_200
        const val LARGE_BACKUP_BUDGET_MS = 5_000L
        const val SUMMARY_ID = 150L
        const val BGM_ID = 200L
        const val QUICK_REPLY_SET_ID = 300L
        const val QUICK_REPLY_ID = 301L
    }
}
