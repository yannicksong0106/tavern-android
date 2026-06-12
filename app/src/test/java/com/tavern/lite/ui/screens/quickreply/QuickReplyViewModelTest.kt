package com.tavern.lite.ui.screens.quickreply

import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.QuickReplyRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickReplyViewModelTest {
    @MockK private lateinit var repository: QuickReplyRepository
    @MockK private lateinit var characterRepository: CharacterRepository
    @MockK private lateinit var chatRepository: ChatRepository

    private val testDispatcher = StandardTestDispatcher()
    private val setsFlow = MutableStateFlow<List<QuickReplySetEntity>>(emptyList())
    private val charactersFlow = MutableStateFlow<List<CharacterEntity>>(emptyList())
    private val chatsFlow = MutableStateFlow<List<ChatEntity>>(emptyList())
    private lateinit var viewModel: QuickReplyViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllSets() } returns setsFlow
        every { repository.getRepliesForSet(any()) } returns flowOf(emptyList())
        every { characterRepository.getAllCharacters() } returns charactersFlow
        every { chatRepository.getAllChats() } returns chatsFlow
        coEvery { repository.insertSet(any()) } returns 7L
        coEvery { repository.updateSet(any()) } returns Unit
        coEvery { repository.deleteSet(any()) } returns Unit
        coEvery { repository.insertReply(any()) } returns 11L
        coEvery { repository.updateReply(any()) } returns Unit
        coEvery { repository.deleteReply(any()) } returns Unit

        viewModel = QuickReplyViewModel(repository, characterRepository, chatRepository)
    }

    @Test
    fun `exposes character and chat options for scope selectors`() = runTest {
        val characterJob = launch { viewModel.characters.collect {} }
        val chatJob = launch { viewModel.chats.collect {} }
        charactersFlow.value = listOf(CharacterEntity(id = 1, name = "Alice"))
        chatsFlow.value = listOf(ChatEntity(id = 2, characterId = 1, name = "Chapter One"))
        advanceUntilIdle()

        assertEquals("Alice", viewModel.characters.value.single().name)
        assertEquals("Chapter One", viewModel.chats.value.single().name)
        characterJob.cancel()
        chatJob.cancel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selects first set when available`() = runTest {
        setsFlow.value = listOf(
            QuickReplySetEntity(id = 3, name = "Global"),
            QuickReplySetEntity(id = 4, name = "Chat")
        )
        advanceUntilIdle()

        assertEquals(3L, viewModel.selectedSetId.value)
    }

    @Test
    fun `selectSet ignores invalid ids`() = runTest {
        setsFlow.value = listOf(QuickReplySetEntity(id = 3, name = "Global"))
        advanceUntilIdle()

        viewModel.selectSet(0L)
        viewModel.selectSet(-1L)

        assertEquals(3L, viewModel.selectedSetId.value)
    }

    @Test
    fun `createSet trims name and only keeps scoped ids`() = runTest {
        val slot = slot<QuickReplySetEntity>()

        viewModel.createSet(
            name = "  Character tools  ",
            scope = "character",
            characterId = 42L,
            chatId = 99L,
            enabled = true,
            displayOrder = 2
        )
        advanceUntilIdle()

        coVerify { repository.insertSet(capture(slot)) }
        assertEquals("Character tools", slot.captured.name)
        assertEquals("character", slot.captured.scope)
        assertEquals(42L, slot.captured.characterId)
        assertNull(slot.captured.chatId)
        assertEquals(2, slot.captured.displayOrder)
        assertEquals(7L, viewModel.selectedSetId.value)
    }

    @Test
    fun `updateSet switches scope and clears stale ids`() = runTest {
        val existing = QuickReplySetEntity(id = 5, name = "Old", scope = "character", characterId = 42L)
        val slot = slot<QuickReplySetEntity>()

        viewModel.updateSet(
            set = existing,
            name = " Chat tools ",
            scope = "chat",
            characterId = 42L,
            chatId = 100L,
            enabled = false,
            displayOrder = 3
        )
        advanceUntilIdle()

        coVerify { repository.updateSet(capture(slot)) }
        assertEquals(5L, slot.captured.id)
        assertEquals("Chat tools", slot.captured.name)
        assertEquals("chat", slot.captured.scope)
        assertNull(slot.captured.characterId)
        assertEquals(100L, slot.captured.chatId)
        assertEquals(false, slot.captured.enabled)
        assertEquals(3, slot.captured.displayOrder)
    }

    @Test
    fun `createSet ignores scoped sets without required target id`() = runTest {
        viewModel.createSet(
            name = "Character tools",
            scope = "character",
            characterId = null,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        viewModel.createSet(
            name = "Chat tools",
            scope = "chat",
            characterId = null,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.insertSet(any()) }
    }

    @Test
    fun `createSet ignores scoped sets with non positive target id`() = runTest {
        viewModel.createSet(
            name = "Character tools",
            scope = "character",
            characterId = 0L,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        viewModel.createSet(
            name = "Chat tools",
            scope = "chat",
            characterId = null,
            chatId = -1L,
            enabled = true,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.insertSet(any()) }
    }

    @Test
    fun `createSet ignores invalid scope`() = runTest {
        viewModel.createSet(
            name = "Invalid",
            scope = "invalid",
            characterId = null,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.insertSet(any()) }
    }

    @Test
    fun `updateSet ignores scoped sets without required target id`() = runTest {
        val existing = QuickReplySetEntity(id = 5, name = "Old")

        viewModel.updateSet(
            set = existing,
            name = "Character tools",
            scope = "character",
            characterId = null,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        viewModel.updateSet(
            set = existing,
            name = "Chat tools",
            scope = "chat",
            characterId = null,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateSet(any()) }
    }

    @Test
    fun `updateSet ignores scoped sets with non positive target id`() = runTest {
        val existing = QuickReplySetEntity(id = 5, name = "Old")

        viewModel.updateSet(
            set = existing,
            name = "Character tools",
            scope = "character",
            characterId = -1L,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        viewModel.updateSet(
            set = existing,
            name = "Chat tools",
            scope = "chat",
            characterId = null,
            chatId = 0L,
            enabled = true,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateSet(any()) }
    }

    @Test
    fun `updateSet ignores invalid scope`() = runTest {
        val existing = QuickReplySetEntity(id = 5, name = "Old")

        viewModel.updateSet(
            set = existing,
            name = "Invalid",
            scope = "invalid",
            characterId = null,
            chatId = null,
            enabled = true,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateSet(any()) }
    }

    @Test
    fun `createReply trims fields and stores permissions`() = runTest {
        val slot = slot<QuickReplyEntity>()

        viewModel.createReply(
            setId = 7L,
            label = " Send ",
            script = " /send hi ",
            icon = " * ",
            automationId = " ",
            enabled = true,
            requiresConfirmation = true,
            allowAutoRun = false,
            canSendMessages = true,
            canTriggerGeneration = false,
            displayOrder = 4
        )
        advanceUntilIdle()

        coVerify { repository.insertReply(capture(slot)) }
        assertEquals(7L, slot.captured.setId)
        assertEquals("Send", slot.captured.label)
        assertEquals("/send hi", slot.captured.script)
        assertEquals("*", slot.captured.icon)
        assertNull(slot.captured.automationId)
        assertTrue(slot.captured.requiresConfirmation)
        assertTrue(slot.captured.canSendMessages)
        assertEquals(4, slot.captured.displayOrder)
    }

    @Test
    fun `createReply ignores blank label or script`() = runTest {
        viewModel.createReply(
            setId = 7L,
            label = " ",
            script = "/send hi",
            icon = null,
            automationId = null,
            enabled = true,
            requiresConfirmation = false,
            allowAutoRun = false,
            canSendMessages = false,
            canTriggerGeneration = false,
            displayOrder = 0
        )
        viewModel.createReply(
            setId = 7L,
            label = "Send",
            script = " ",
            icon = null,
            automationId = null,
            enabled = true,
            requiresConfirmation = false,
            allowAutoRun = false,
            canSendMessages = false,
            canTriggerGeneration = false,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.insertReply(any()) }
    }

    @Test
    fun `createReply ignores invalid set id`() = runTest {
        viewModel.createReply(
            setId = 0L,
            label = "Send",
            script = "/send hi",
            icon = null,
            automationId = null,
            enabled = true,
            requiresConfirmation = false,
            allowAutoRun = false,
            canSendMessages = false,
            canTriggerGeneration = false,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.insertReply(any()) }
    }

    @Test
    fun `updateReply ignores blank label or script`() = runTest {
        val existing = QuickReplyEntity(setId = 7L, label = "Send", script = "/send hi")

        viewModel.updateReply(
            reply = existing,
            label = " ",
            script = "/send hi",
            icon = null,
            automationId = null,
            enabled = true,
            requiresConfirmation = false,
            allowAutoRun = false,
            canSendMessages = false,
            canTriggerGeneration = false,
            displayOrder = 0
        )
        viewModel.updateReply(
            reply = existing,
            label = "Send",
            script = " ",
            icon = null,
            automationId = null,
            enabled = true,
            requiresConfirmation = false,
            allowAutoRun = false,
            canSendMessages = false,
            canTriggerGeneration = false,
            displayOrder = 0
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateReply(any()) }
    }
}
