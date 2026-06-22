package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.domain.usecase.ContinueGenerationUseCase
import com.tavern.lite.domain.usecase.ProactiveDialogueUseCase
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.domain.usecase.SendMessageUseCase
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.ImageGenerationService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatStreamingManagerTest {

    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var apiConfigStore: ApiConfigStore
    @MockK private lateinit var sendMessageUseCase: SendMessageUseCase
    @MockK private lateinit var continueGenerationUseCase: ContinueGenerationUseCase
    @MockK private lateinit var proactiveMessageUseCase: ProactiveMessageUseCase
    @MockK private lateinit var proactiveDialogueUseCase: ProactiveDialogueUseCase
    @MockK private lateinit var imageGenerationService: ImageGenerationService

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var manager: ChatStreamingManager
    private val chatId = 10L
    private val characterId = 1L

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)

        val configFlow = MutableStateFlow(ApiConfig())
        every { apiConfigStore.configFlow } returns configFlow

        manager = ChatStreamingManager(
            chatId = chatId,
            characterId = characterId,
            chatRepository = chatRepository,
            apiConfigStore = apiConfigStore,
            sendMessageUseCase = sendMessageUseCase,
            continueGenerationUseCase = continueGenerationUseCase,
            proactiveMessageUseCase = proactiveMessageUseCase,
            proactiveDialogueUseCase = proactiveDialogueUseCase,
            imageGenerationService = imageGenerationService,
            scope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initial state ====================

    @Test
    fun `initial isGenerating is false`() {
        assertFalse(manager.isGenerating.value)
    }

    // ==================== stopGeneration ====================

    @Test
    fun `stopGeneration sets isGenerating to false`() {
        manager.stopGeneration()
        assertFalse(manager.isGenerating.value)
    }

    // ==================== sendMessage guards ====================

    @Test
    fun `sendMessage ignores blank content with no images`() {
        manager.sendMessage("", emptyList())
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `sendMessage ignores whitespace-only content`() {
        manager.sendMessage("   ", emptyList())
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `sendMessage sets isGenerating true during generation`() = runTest {
        val character = CharacterEntity(id = characterId, name = "Test")
        manager.characterProvider = { character }
        coEvery { sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any(), any()) } returns null

        manager.sendMessage("hello")
        advanceUntilIdle()

        // After completion, isGenerating should be false again
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `sendMessage emits assistant reply committed after assistant message is saved`() = runTest {
        val character = CharacterEntity(id = characterId, name = "Test")
        var committedCount = 0
        var emotionContent: String? = null
        manager.characterProvider = { character }
        manager.onAssistantReplyCommitted = { committedCount++ }
        manager.onEmotionUpdate = { emotionContent = it }
        coEvery {
            sendMessageUseCase.sendSingleMessage(chatId, character, "hello", any(), null, emptyList())
        } returns MessageExecutionHelper.ExecutionResult(assistantMsgId = 88L, fullResponse = "reply")
        coEvery { chatRepository.getMessageById(88L) } returns MessageEntity(
            id = 88L,
            chatId = chatId,
            role = "assistant",
            content = "reply"
        )
        every { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        manager.sendMessage("hello")
        advanceUntilIdle()

        assertEquals(1, committedCount)
        assertEquals("reply", emotionContent)
        coVerify(exactly = 1) { chatRepository.getMessageById(88L) }
    }

    @Test
    fun `direct group mention emits assistant reply committed after assistant message is saved`() = runTest {
        val alice = CharacterEntity(id = 1L, name = "Alice")
        val bob = CharacterEntity(id = 2L, name = "Bob")
        var committedCount = 0
        var emotionContent: String? = null
        manager.isGroupChatProvider = { true }
        manager.groupCharactersProvider = { listOf(alice, bob) }
        manager.onAssistantReplyCommitted = { committedCount++ }
        manager.onEmotionUpdate = { emotionContent = it }
        every { proactiveDialogueUseCase.parseAtMention("@Bob hi", listOf(alice, bob)) } returns (bob to "hi")
        coEvery {
            sendMessageUseCase.sendDirectMessage(chatId, listOf(alice, bob), bob, "hi", any(), emptyList())
        } returns MessageExecutionHelper.ExecutionResult(assistantMsgId = 99L, fullResponse = "direct reply")
        coEvery { chatRepository.getMessageById(99L) } returns MessageEntity(
            id = 99L,
            chatId = chatId,
            role = "assistant",
            content = "direct reply",
            characterId = bob.id
        )

        manager.sendMessage("@Bob hi")
        advanceUntilIdle()

        assertEquals(1, committedCount)
        assertEquals("direct reply", emotionContent)
        coVerify {
            sendMessageUseCase.sendDirectMessage(chatId, listOf(alice, bob), bob, "hi", any(), emptyList())
        }
    }

    @Test
    fun `direct group mention does not emit assistant reply committed without assistant message id`() = runTest {
        val alice = CharacterEntity(id = 1L, name = "Alice")
        val bob = CharacterEntity(id = 2L, name = "Bob")
        var committedCount = 0
        manager.isGroupChatProvider = { true }
        manager.groupCharactersProvider = { listOf(alice, bob) }
        manager.onAssistantReplyCommitted = { committedCount++ }
        every { proactiveDialogueUseCase.parseAtMention("@Bob hi", listOf(alice, bob)) } returns (bob to "hi")
        coEvery {
            sendMessageUseCase.sendDirectMessage(chatId, listOf(alice, bob), bob, "hi", any(), emptyList())
        } returns MessageExecutionHelper.ExecutionResult(fullResponse = "direct reply")

        manager.sendMessage("@Bob hi")
        advanceUntilIdle()

        assertEquals(0, committedCount)
        coVerify(exactly = 0) { chatRepository.getMessageById(any()) }
    }

    // ==================== continueGeneration guards ====================

    @Test
    fun `continueGeneration does nothing when no assistant messages`() = runTest {
        manager.messagesProvider = { emptyList() }
        manager.continueGeneration()
        advanceUntilIdle()
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `continueGeneration does nothing when last message is user`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = chatId, role = "user", content = "hello")
        )
        manager.messagesProvider = { messages }
        manager.continueGeneration()
        advanceUntilIdle()
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `continueGeneration passes reasoning for target assistant message`() = runTest {
        val character = CharacterEntity(id = characterId, name = "Test")
        manager.characterProvider = { character }
        coEvery {
            sendMessageUseCase.sendSingleMessage(chatId, character, "hello", any(), null, emptyList())
        } returns MessageExecutionHelper.ExecutionResult(
            assistantMsgId = 88L,
            fullResponse = "reply",
            reasoningContent = "first reasoning"
        )
        coEvery { chatRepository.getMessageById(88L) } returns MessageEntity(
            id = 88L,
            chatId = chatId,
            role = "assistant",
            content = "reply"
        )
        every { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        manager.sendMessage("hello")
        advanceUntilIdle()

        manager.messagesProvider = {
            listOf(
                MessageEntity(id = 1L, chatId = chatId, role = "user", content = "hello"),
                MessageEntity(id = 88L, chatId = chatId, role = "assistant", content = "reply")
            )
        }
        coEvery {
            continueGenerationUseCase.continueGeneration(
                chatId = chatId,
                characterId = characterId,
                character = character,
                lastAssistantMsgId = 88L,
                lastAssistantContent = "reply",
                config = any(),
                previousReasoningContent = "first reasoning"
            )
        } returns MessageExecutionHelper.ExecutionResult(
            assistantMsgId = 88L,
            fullResponse = " continued",
            reasoningContent = "continued reasoning"
        )

        manager.continueGeneration()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            continueGenerationUseCase.continueGeneration(
                chatId = chatId,
                characterId = characterId,
                character = character,
                lastAssistantMsgId = 88L,
                lastAssistantContent = "reply",
                config = any(),
                previousReasoningContent = "first reasoning"
            )
        }
    }

    @Test
    fun `continueGeneration passes reasoning recorded from group assistant message`() = runTest {
        val alice = CharacterEntity(id = 1L, name = "Alice")
        val bob = CharacterEntity(id = 2L, name = "Bob")
        manager.isGroupChatProvider = { true }
        manager.characterProvider = { bob }
        manager.groupCharactersProvider = { listOf(alice, bob) }
        manager.schedulingStrategyProvider = { GroupSchedulingStrategy.LIST_ORDER }
        manager.messageIntervalProvider = { 1L }
        every { proactiveDialogueUseCase.parseAtMention("hello", listOf(alice, bob)) } returns null
        coEvery {
            sendMessageUseCase.sendGroupMessage(chatId, listOf(alice, bob), "hello", any(), emptyList())
        } returns listOf(
            alice.id to MessageExecutionHelper.ExecutionResult(
                assistantMsgId = 11L,
                fullResponse = "alice reply",
                reasoningContent = "alice reasoning"
            ),
            bob.id to MessageExecutionHelper.ExecutionResult(
                assistantMsgId = 22L,
                fullResponse = "bob reply",
                reasoningContent = "bob reasoning"
            )
        )
        coEvery { chatRepository.getMessageById(11L) } returns MessageEntity(
            id = 11L,
            chatId = chatId,
            role = "assistant",
            content = "alice reply",
            characterId = alice.id
        )
        coEvery { chatRepository.getMessageById(22L) } returns MessageEntity(
            id = 22L,
            chatId = chatId,
            role = "assistant",
            content = "bob reply",
            characterId = bob.id
        )
        every { proactiveDialogueUseCase.shouldScheduleGroupProactive(listOf(alice, bob)) } returns null

        manager.sendMessage("hello")
        advanceUntilIdle()

        manager.messagesProvider = {
            listOf(
                MessageEntity(id = 1L, chatId = chatId, role = "user", content = "hello"),
                MessageEntity(id = 11L, chatId = chatId, role = "assistant", content = "alice reply", characterId = alice.id),
                MessageEntity(id = 22L, chatId = chatId, role = "assistant", content = "bob reply", characterId = bob.id)
            )
        }
        coEvery {
            continueGenerationUseCase.continueGeneration(
                chatId = chatId,
                characterId = characterId,
                character = bob,
                lastAssistantMsgId = 22L,
                lastAssistantContent = "bob reply",
                config = any(),
                previousReasoningContent = "bob reasoning"
            )
        } returns MessageExecutionHelper.ExecutionResult(
            assistantMsgId = 22L,
            fullResponse = " continued",
            reasoningContent = "continued bob reasoning"
        )

        manager.continueGeneration()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            continueGenerationUseCase.continueGeneration(
                chatId = chatId,
                characterId = characterId,
                character = bob,
                lastAssistantMsgId = 22L,
                lastAssistantContent = "bob reply",
                config = any(),
                previousReasoningContent = "bob reasoning"
            )
        }
    }

    // ==================== regenerate guards ====================

    @Test
    fun `regenerate does nothing when message not found`() = runTest {
        manager.messagesProvider = { emptyList() }
        manager.regenerate(999L)
        advanceUntilIdle()
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `regenerate does nothing when message is user role`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = chatId, role = "user", content = "hello")
        )
        manager.messagesProvider = { messages }
        manager.regenerate(1L)
        advanceUntilIdle()
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `regenerate does nothing when no user message before assistant`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = chatId, role = "system", content = "system"),
            MessageEntity(id = 2, chatId = chatId, role = "assistant", content = "reply")
        )
        manager.messagesProvider = { messages }
        manager.regenerate(2L)
        advanceUntilIdle()
        assertFalse(manager.isGenerating.value)
    }

    // ==================== resendUserMessage guards ====================

    @Test
    fun `resendUserMessage does nothing when message not found`() = runTest {
        manager.messagesProvider = { emptyList() }
        manager.resendUserMessage(999L)
        advanceUntilIdle()
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `resendUserMessage does nothing when message is assistant role`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = chatId, role = "assistant", content = "reply")
        )
        manager.messagesProvider = { messages }
        manager.resendUserMessage(1L)
        advanceUntilIdle()
        assertFalse(manager.isGenerating.value)
    }

    // ==================== generateImage guards ====================

    @Test
    fun `generateImage ignores blank prompt`() {
        manager.generateImage("")
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `generateImage ignores whitespace prompt`() {
        manager.generateImage("   ")
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `generateImage sends generated image through single message flow once`() = runTest {
        val character = CharacterEntity(id = characterId, name = "Test")
        manager.characterProvider = { character }
        coEvery { imageGenerationService.generateImage(any(), any()) } returns "/tmp/image.png"
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any(), any())
        } returns MessageExecutionHelper.ExecutionResult(assistantMsgId = 99L, fullResponse = "done")
        coEvery { chatRepository.getMessageById(99L) } returns MessageEntity(
            id = 99L,
            chatId = chatId,
            role = "assistant",
            content = "done"
        )
        coEvery { chatRepository.sendMessage(any(), any(), any(), any(), any()) } returns 1L

        manager.generateImage("cat in tavern")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            sendMessageUseCase.sendSingleMessage(
                chatId,
                character,
                "/imagine cat in tavern",
                any(),
                null,
                listOf("/tmp/image.png")
            )
        }
        coVerify(exactly = 0) {
            chatRepository.sendMessage(chatId, any(), "user", any(), any())
        }
        assertFalse(manager.isGenerating.value)
    }

    // ==================== triggerProactiveIfNeeded ====================

    @Test
    fun `triggerProactiveIfNeeded does nothing when last message is assistant but no character`() {
        manager.characterProvider = { null }
        val messages = listOf(
            MessageEntity(id = 1, chatId = chatId, role = "assistant", content = "reply")
        )
        manager.messagesProvider = { messages }
        manager.triggerProactiveIfNeeded()
        assertFalse(manager.isGenerating.value)
    }

    @Test
    fun `triggerProactiveIfNeeded does nothing when no messages`() {
        manager.messagesProvider = { emptyList() }
        manager.triggerProactiveIfNeeded()
        assertFalse(manager.isGenerating.value)
    }

    // ==================== cancel ====================

    @Test
    fun `cancel cancels streaming job`() {
        manager.cancel()
        // No crash, no-op when no job
    }
}
