package com.tavern.lite.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.model.StScriptCommand
import com.tavern.lite.data.model.StScriptCommandType
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.GroupChatRepository
import com.tavern.lite.data.repository.QuickReplyRepository
import com.tavern.lite.data.repository.SummaryRepository
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.domain.port.LegacyConfigReaderPort
import com.tavern.lite.domain.usecase.ContinueGenerationUseCase
import com.tavern.lite.domain.usecase.MemoryExtractionUseCase
import com.tavern.lite.domain.usecase.ProactiveDialogueUseCase
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerResult
import com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCase
import com.tavern.lite.domain.usecase.SendMessageUseCase
import com.tavern.lite.domain.usecase.QuickReplyAutomationExecution
import com.tavern.lite.domain.usecase.StScriptAction
import com.tavern.lite.domain.usecase.StScriptBlockedCommand
import com.tavern.lite.domain.usecase.StScriptExecutionResult
import com.tavern.lite.domain.usecase.StScriptLiteExecutor
import com.tavern.lite.domain.usecase.SummaryUseCase
import com.tavern.lite.domain.port.EmotionDetectionPort
import com.tavern.lite.domain.port.ImageGenerationPort
import com.tavern.lite.data.repository.BgmRepository
import com.tavern.lite.data.repository.SpriteRepository
import com.tavern.lite.ui.screens.vn.BgmPlayer
import com.tavern.lite.util.ChatActiveTracker
import com.tavern.lite.util.TtsHelper
import com.tavern.lite.util.SttHelper
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.noties.markwon.Markwon
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @MockK private lateinit var characterRepository: CharacterRepository
    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var groupChatRepository: GroupChatRepository
    @MockK private lateinit var quickReplyRepository: QuickReplyRepository
    @MockK private lateinit var configReader: LegacyConfigReaderPort
    @MockK private lateinit var settingsStore: SettingsStore
    @MockK private lateinit var promptInspectorBuilder: PromptInspectorBuilder
    @MockK private lateinit var sendMessageUseCase: SendMessageUseCase
    @MockK private lateinit var continueGenerationUseCase: ContinueGenerationUseCase
    @MockK private lateinit var proactiveMessageUseCase: ProactiveMessageUseCase
    @MockK private lateinit var proactiveDialogueUseCase: ProactiveDialogueUseCase
    @MockK private lateinit var quickReplyAutomationTriggerUseCase: QuickReplyAutomationTriggerUseCase
    @MockK private lateinit var stScriptLiteExecutor: StScriptLiteExecutor
    @MockK private lateinit var memoryExtractionUseCase: MemoryExtractionUseCase
    @MockK private lateinit var summaryUseCase: SummaryUseCase
    @MockK private lateinit var summaryRepository: SummaryRepository
    @MockK private lateinit var spriteRepository: SpriteRepository
    @MockK private lateinit var bgmRepository: BgmRepository
    @MockK private lateinit var emotionDetector: EmotionDetectionPort
    @MockK private lateinit var bgmPlayer: BgmPlayer
    @MockK private lateinit var imageGenerationService: ImageGenerationPort
    @MockK private lateinit var ttsHelper: TtsHelper
    @MockK private lateinit var sttHelper: SttHelper
    @MockK private lateinit var markwon: Markwon

    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testCharacter = CharacterEntity(
        id = 42,
        name = "Alice",
        description = "A test character",
        chattiness = 50
    )

    private val testConfig = ApiConfig()

    companion object {
        private const val CHARACTER_ID = 42L
        private const val CHAT_ID = 100L
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        // Default mocks for ViewModel init
        every { settingsStore.bubbleStyleFlow } returns flowOf(BubbleStyleConfig())
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(emptyList())
        every { quickReplyRepository.getEnabledRepliesForContext(CHARACTER_ID, CHAT_ID) } returns flowOf(emptyList())
        coEvery { chatRepository.getMessagesPage(any(), any()) } returns emptyList()
        every { chatRepository.getPinnedMessages(CHAT_ID) } returns flowOf(emptyList())
        coEvery { characterRepository.getCharacterById(CHARACTER_ID) } returns testCharacter
        coEvery { chatRepository.getChatById(CHAT_ID) } returns mockk(relaxed = true) {
            every { backgroundPath } returns null
            every { isGroup } returns false
        }
        coEvery { chatRepository.getMessageCount(CHAT_ID) } returns 0
        every { memoryExtractionUseCase.setMessageCount(any()) } returns Unit
        every { summaryRepository.getSummariesForChat(CHAT_ID) } returns flowOf(emptyList())
        every { spriteRepository.getSpritesForCharacter(CHARACTER_ID) } returns flowOf(emptyList())
        coEvery { spriteRepository.getAvailableEmotions(CHARACTER_ID) } returns emptyList()
        every { bgmPlayer.isPlaying() } returns false
        every { bgmPlayer.currentPath() } returns null
        coEvery { bgmRepository.getDefaultBgm(any()) } returns null
        every { emotionDetector.detectEmotion(any()) } returns "neutral"
        every { emotionDetector.getSupportedEmotions() } returns listOf("happy", "sad", "angry", "surprised", "scared", "disgusted", "confused", "embarrassed", "love", "neutral")
        coEvery { configReader.readConfig() } returns testConfig
        every { ttsHelper.isSpeaking } returns MutableStateFlow(false)
        every { ttsHelper.speakingMessageId } returns MutableStateFlow(null)
        every { ttsHelper.stop() } returns Unit
        every { ttsHelper.speak(any(), any()) } returns Unit
        every { sttHelper.isListening } returns MutableStateFlow(false)
        every { sttHelper.partialText } returns MutableStateFlow("")
        every { sttHelper.stopListening() } returns Unit
        every { sttHelper.shutdown() } returns Unit

        val savedStateHandle = SavedStateHandle(
            mapOf("characterId" to CHARACTER_ID, "chatId" to CHAT_ID)
        )
        viewModel = ChatViewModel(
            savedStateHandle,
            characterRepository,
            chatRepository,
            groupChatRepository,
            quickReplyRepository,
            configReader,
            settingsStore,
            promptInspectorBuilder,
            sendMessageUseCase,
            continueGenerationUseCase,
            proactiveMessageUseCase,
            proactiveDialogueUseCase,
            quickReplyAutomationTriggerUseCase,
            stScriptLiteExecutor,
            memoryExtractionUseCase,
            summaryUseCase,
            summaryRepository,
            spriteRepository,
            bgmRepository,
            emotionDetector,
            bgmPlayer,
            imageGenerationService,
            ttsHelper,
            sttHelper,
            markwon
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ==================== sendMessage guards ====================

    @Test
    fun `sendMessage ignores blank content`() = runTest {
        viewModel.streamingManager.sendMessage("")
        advanceUntilIdle()

        coVerify(exactly = 0) { sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage ignores whitespace-only content`() = runTest {
        viewModel.streamingManager.sendMessage("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage sets isGenerating true during generation`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } coAnswers {
            // Verify isGenerating is true while inside the use case
            assertTrue(viewModel.streamingManager.isGenerating.value)
            null
        }
        coEvery { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        viewModel.streamingManager.sendMessage("Hello")
        advanceUntilIdle()

        assertFalse("isGenerating should be false after completion", viewModel.streamingManager.isGenerating.value)
    }

    @Test
    fun `sendMessage ignores when already generating`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } coAnswers {
            // Simulate slow response
            kotlinx.coroutines.delay(5000)
            null
        }
        coEvery { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        viewModel.streamingManager.sendMessage("First")
        // Second call should be ignored because isGenerating is true
        viewModel.streamingManager.sendMessage("Second")
        advanceUntilIdle()

        // sendSingleMessage should only be called once
        coVerify(exactly = 1) { sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any()) }
    }

    // ==================== stopGeneration ====================

    @Test
    fun `stopGeneration cancels streaming and resets state`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } coAnswers {
            kotlinx.coroutines.delay(10000)
            null
        }
        coEvery { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        viewModel.streamingManager.sendMessage("Hello")
        advanceUntilIdle()

        viewModel.streamingManager.stopGeneration()
        advanceUntilIdle()

        assertFalse(viewModel.streamingManager.isGenerating.value)
    }

    // ==================== editMessage / deleteMessage / togglePinMessage ====================

    @Test
    fun `editMessage delegates to chatRepository`() = runTest {
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        viewModel.editMessage(1L, "New content")
        advanceUntilIdle()

        coVerify { chatRepository.updateMessageContent(1L, "New content") }
    }

    @Test
    fun `deleteMessage delegates to chatRepository`() = runTest {
        coEvery { chatRepository.deleteMessage(any()) } returns Unit

        viewModel.deleteMessage(1L)
        advanceUntilIdle()

        coVerify { chatRepository.deleteMessage(1L) }
    }

    @Test
    fun `togglePinMessage toggles pin state`() = runTest {
        val msg = MessageEntity(id = 5, chatId = CHAT_ID, role = "assistant", content = "Hi", isPinned = false)
        // Inject message into the messages flow
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(listOf(msg))
        // Re-create to pick up the new flow
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { chatRepository.togglePinMessage(any(), any()) } returns Unit

        viewModel.togglePinMessage(5L)
        advanceUntilIdle()

        coVerify { chatRepository.togglePinMessage(5L, true) }
    }

    @Test
    fun `togglePinMessage unpins pinned message`() = runTest {
        val msg = MessageEntity(id = 5, chatId = CHAT_ID, role = "assistant", content = "Hi", isPinned = true)
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(listOf(msg))
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { chatRepository.togglePinMessage(any(), any()) } returns Unit

        viewModel.togglePinMessage(5L)
        advanceUntilIdle()

        coVerify { chatRepository.togglePinMessage(5L, false) }
    }

    // ==================== searchMessages ====================

    @Test
    fun `searchMessages with blank query clears results`() {
        viewModel.searchManager.searchMessages("test")
        viewModel.searchManager.searchMessages("")

        assertEquals("", viewModel.searchManager.searchQuery.value)
        assertEquals(emptyList<Int>(), viewModel.searchManager.searchResults.value)
        assertEquals(-1, viewModel.searchManager.currentSearchIndex.value)
    }

    @Test
    fun `searchMessages finds matching messages`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = CHAT_ID, role = "user", content = "Hello world"),
            MessageEntity(id = 2, chatId = CHAT_ID, role = "assistant", content = "Hi there"),
            MessageEntity(id = 3, chatId = CHAT_ID, role = "user", content = "Hello again"),
            MessageEntity(id = 4, chatId = CHAT_ID, role = "assistant", content = "Goodbye")
        )
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(messages)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchManager.searchMessages("hello")

        assertEquals(listOf(0, 2), viewModel.searchManager.searchResults.value)
        assertEquals(0, viewModel.searchManager.currentSearchIndex.value)
    }

    @Test
    fun `searchMessages is case insensitive`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = CHAT_ID, role = "user", content = "Hello World"),
            MessageEntity(id = 2, chatId = CHAT_ID, role = "assistant", content = "HELLO there")
        )
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(messages)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchManager.searchMessages("hello")

        assertEquals(listOf(0, 1), viewModel.searchManager.searchResults.value)
    }

    @Test
    fun `searchMessages returns empty for no match`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = CHAT_ID, role = "user", content = "Hello")
        )
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(messages)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchManager.searchMessages("xyz")

        assertEquals(emptyList<Int>(), viewModel.searchManager.searchResults.value)
        assertEquals(-1, viewModel.searchManager.currentSearchIndex.value)
    }

    @Test
    fun `nextSearchResult cycles through results`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = CHAT_ID, role = "user", content = "test a"),
            MessageEntity(id = 2, chatId = CHAT_ID, role = "assistant", content = "test b"),
            MessageEntity(id = 3, chatId = CHAT_ID, role = "user", content = "test c")
        )
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(messages)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchManager.searchMessages("test")
        assertEquals(0, viewModel.searchManager.currentSearchIndex.value)

        viewModel.searchManager.nextSearchResult()
        assertEquals(1, viewModel.searchManager.currentSearchIndex.value)

        viewModel.searchManager.nextSearchResult()
        assertEquals(2, viewModel.searchManager.currentSearchIndex.value)

        // Wraps around
        viewModel.searchManager.nextSearchResult()
        assertEquals(0, viewModel.searchManager.currentSearchIndex.value)
    }

    @Test
    fun `previousSearchResult cycles backward`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = CHAT_ID, role = "user", content = "test a"),
            MessageEntity(id = 2, chatId = CHAT_ID, role = "assistant", content = "test b"),
            MessageEntity(id = 3, chatId = CHAT_ID, role = "user", content = "test c")
        )
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(messages)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchManager.searchMessages("test")
        assertEquals(0, viewModel.searchManager.currentSearchIndex.value)

        // Wraps to end
        viewModel.searchManager.previousSearchResult()
        assertEquals(2, viewModel.searchManager.currentSearchIndex.value)
    }

    @Test
    fun `clearSearch resets all search state`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = CHAT_ID, role = "user", content = "test")
        )
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(messages)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchManager.searchMessages("test")
        viewModel.searchManager.clearSearch()

        assertEquals("", viewModel.searchManager.searchQuery.value)
        assertEquals(emptyList<Int>(), viewModel.searchManager.searchResults.value)
        assertEquals(-1, viewModel.searchManager.currentSearchIndex.value)
    }

    // ==================== swipe operations ====================

    @Test
    fun `getSwipeInfo returns correct info for message with no swipes`() = runTest {
        val msg = MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Hi", swipeContent = "[]", swipeIndex = 0)
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(listOf(msg))
        viewModel = createViewModel()
        advanceUntilIdle()

        val (current, total) = viewModel.getSwipeInfo(1L)
        assertEquals(1, current)
        assertEquals(1, total)
    }

    @Test
    fun `getSwipeInfo returns zero for unknown message`() = runTest {
        val (current, total) = viewModel.getSwipeInfo(999L)
        assertEquals(0, current)
        assertEquals(0, total)
    }

    @Test
    fun `swipeLeft does nothing at index zero`() = runTest {
        val msg = MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Hi", swipeContent = "[\"Alt\"]", swipeIndex = 0)
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(listOf(msg))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.swipeLeft(1L)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatRepository.switchSwipe(any(), any()) }
    }

    @Test
    fun `swipeRight switches to next swipe`() = runTest {
        val msg = MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Hi", swipeContent = "[\"Alt 1\",\"Alt 2\"]", swipeIndex = 0)
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(listOf(msg))
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { chatRepository.switchSwipe(any(), any()) } returns Unit

        viewModel.swipeRight(1L)
        advanceUntilIdle()

        coVerify { chatRepository.switchSwipe(1L, 1) }
    }

    @Test
    fun `swipeRight does nothing at last swipe`() = runTest {
        val msg = MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Alt 2", swipeContent = "[\"Alt 1\",\"Alt 2\"]", swipeIndex = 1)
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(listOf(msg))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.swipeRight(1L)
        advanceUntilIdle()

        coVerify(exactly = 0) { chatRepository.switchSwipe(any(), any()) }
    }

    // ==================== branch operations ====================

    @Test
    fun `loadBranches populates branchEntities`() = runTest {
        val branches = listOf(
            BranchEntity(id = 1, chatId = CHAT_ID, name = "Main", isDefault = true),
            BranchEntity(id = 2, chatId = CHAT_ID, name = "Alt")
        )
        coEvery { chatRepository.getBranchesForChatSync(CHAT_ID) } returns branches

        viewModel.branchManager.loadBranches()
        advanceUntilIdle()

        assertEquals(branches, viewModel.branchManager.branchEntities.value)
        assertEquals(1L, viewModel.branchManager.currentBranchId.value)
    }

    @Test
    fun `switchBranch delegates to chatRepository`() = runTest {
        coEvery { chatRepository.switchBranch(any(), any()) } returns Unit

        viewModel.branchManager.switchBranch(2L)
        advanceUntilIdle()

        coVerify { chatRepository.switchBranch(CHAT_ID, 2L) }
        assertEquals(2L, viewModel.branchManager.currentBranchId.value)
    }

    @Test
    fun `createBranch delegates and reloads`() = runTest {
        coEvery { chatRepository.createBranch(any(), any()) } returns 3L
        coEvery { chatRepository.getBranchesForChatSync(CHAT_ID) } returns listOf(
            BranchEntity(id = 3, chatId = CHAT_ID, name = "New Branch")
        )

        viewModel.branchManager.createBranch("New Branch")
        advanceUntilIdle()

        coVerify { chatRepository.createBranch(CHAT_ID, "New Branch") }
    }

    @Test
    fun `deleteBranch delegates and reloads`() = runTest {
        val branch = BranchEntity(id = 2, chatId = CHAT_ID, name = "ToDelete")
        coEvery { chatRepository.deleteBranch(any()) } returns Unit
        coEvery { chatRepository.getBranchesForChatSync(CHAT_ID) } returns emptyList()

        viewModel.branchManager.deleteBranch(branch)
        advanceUntilIdle()

        coVerify { chatRepository.deleteBranch(branch) }
    }

    @Test
    fun `createBranchFromMessage delegates correctly`() = runTest {
        coEvery { chatRepository.createBranchFromMessage(any(), any(), any()) } returns 4L
        coEvery { chatRepository.getBranchesForChatSync(CHAT_ID) } returns emptyList()

        viewModel.branchManager.createBranchFromMessage(10L, "Branch at msg 10")
        advanceUntilIdle()

        coVerify { chatRepository.createBranchFromMessage(CHAT_ID, 10L, "Branch at msg 10") }
    }

    // ==================== bookmark filter ====================

    @Test
    fun `toggleBookmarkFilter toggles state`() {
        assertFalse(viewModel.branchManager.showBookmarksOnly.value)

        viewModel.branchManager.toggleBookmarkFilter()
        assertTrue(viewModel.branchManager.showBookmarksOnly.value)

        viewModel.branchManager.toggleBookmarkFilter()
        assertFalse(viewModel.branchManager.showBookmarksOnly.value)
    }

    // ==================== background ====================

    @Test
    fun `setChatBackground updates path and delegates`() = runTest {
        coEvery { chatRepository.updateChatBackground(any(), any()) } returns Unit

        viewModel.setChatBackground("/path/to/bg.jpg")
        advanceUntilIdle()

        assertEquals("/path/to/bg.jpg", viewModel.backgroundPath.value)
        coVerify { chatRepository.updateChatBackground(CHAT_ID, "/path/to/bg.jpg") }
    }

    @Test
    fun `clearChatBackground sets null`() = runTest {
        coEvery { chatRepository.updateChatBackground(any(), any()) } returns Unit

        viewModel.clearChatBackground()
        advanceUntilIdle()

        assertNull(viewModel.backgroundPath.value)
        coVerify { chatRepository.updateChatBackground(CHAT_ID, null) }
    }

    // ==================== chattiness ====================

    @Test
    fun `updateCharacterChattiness updates flow and persists`() = runTest {
        coEvery { characterRepository.updateCharacter(any()) } returns Unit

        viewModel.groupChatSettingsManager.updateCharacterChattiness(75)
        advanceUntilIdle()

        assertEquals(75, viewModel.groupChatSettingsManager.characterChattiness.value)
        coVerify { characterRepository.updateCharacter(match { it.chattiness == 75 }) }
    }

    @Test
    fun `updateGroupChattiness updates flow and persists`() = runTest {
        coEvery { chatRepository.updateGroupChattiness(any(), any()) } returns Unit

        viewModel.groupChatSettingsManager.updateGroupChattiness(80)
        advanceUntilIdle()

        assertEquals(80, viewModel.groupChatSettingsManager.groupChattiness.value)
        coVerify { chatRepository.updateGroupChattiness(CHAT_ID, 80) }
    }

    @Test
    fun `updateGroupCharacterChattiness updates map and persists`() = runTest {
        coEvery { groupChatRepository.updateCharacterChattiness(any(), any(), any()) } returns Unit

        viewModel.groupChatSettingsManager.updateGroupCharacterChattiness(42L, 60)
        advanceUntilIdle()

        assertEquals(60, viewModel.groupChatSettingsManager.groupCharacterChattiness.value[42L])
        coVerify { groupChatRepository.updateCharacterChattiness(CHAT_ID, 42L, 60) }
    }

    // ==================== TTS ====================

    @Test
    fun `speakMessage delegates to ttsHelper`() {
        val msg = MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Hello")

        viewModel.speechManager.speakMessage(msg)

        io.mockk.verify { ttsHelper.speak("Hello", 1L) }
    }

    @Test
    fun `stopSpeaking delegates to ttsHelper`() {
        viewModel.speechManager.stopSpeaking()

        io.mockk.verify { ttsHelper.stop() }
    }

    // ==================== CancellationException rethrow ====================

    @Test
    fun `sendMessage rethrows CancellationException`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } throws CancellationException()

        // CancellationException should propagate, not be caught as toast
        // isGenerating should still be reset in finally
        try {
            viewModel.streamingManager.sendMessage("Hello")
        } catch (_: CancellationException) {
            // expected
        }
        advanceUntilIdle()

        assertFalse(viewModel.streamingManager.isGenerating.value)
    }

    // ==================== classifyError (indirect) ====================

    @Test
    fun `sendMessage emits network error toast for UnknownHostException`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } throws java.net.UnknownHostException("unreachable")
        coEvery { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        var toastMessage: String? = null
        val job = launch(Dispatchers.Unconfined) {
            viewModel.toastMessage.collect { toastMessage = it }
        }

        viewModel.streamingManager.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("网络连接失败，请检查网络设置", toastMessage)
        assertFalse(viewModel.streamingManager.isGenerating.value)
        job.cancel()
    }

    @Test
    fun `sendMessage emits timeout error toast for SocketTimeoutException`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } throws java.net.SocketTimeoutException("timeout")
        coEvery { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        var toastMessage: String? = null
        val job = launch(Dispatchers.Unconfined) {
            viewModel.toastMessage.collect { toastMessage = it }
        }

        viewModel.streamingManager.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("请求超时，请稍后重试", toastMessage)
        job.cancel()
    }

    @Test
    fun `sendMessage emits rate limit toast for 429 error`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } throws RuntimeException("429 Too Many Requests")
        coEvery { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        var toastMessage: String? = null
        val job = launch(Dispatchers.Unconfined) {
            viewModel.toastMessage.collect { toastMessage = it }
        }

        viewModel.streamingManager.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("请求过于频繁，请等待后重试", toastMessage)
        job.cancel()
    }

    @Test
    fun `sendMessage emits server error toast for 5xx error`() = runTest {
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any())
        } throws RuntimeException("503 Service Unavailable")
        coEvery { proactiveDialogueUseCase.shouldScheduleProactive(any()) } returns null

        var toastMessage: String? = null
        val job = launch(Dispatchers.Unconfined) {
            viewModel.toastMessage.collect { toastMessage = it }
        }

        viewModel.streamingManager.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals("服务暂时不可用，请稍后重试", toastMessage)
        job.cancel()
    }

    // ==================== ChatActiveTracker ====================

    @Test
    fun `ChatActiveTracker isActive returns false for unknown chat`() {
        assertFalse(ChatActiveTracker.isActive(999L))
    }

    // ==================== pagination ====================

    @Test
    fun `displayMessages shows recent messages when total exceeds page size`() = runTest {
        val allMsgs = (1..60).map { i ->
            MessageEntity(id = i.toLong(), chatId = CHAT_ID, role = if (i % 2 == 0) "assistant" else "user", content = "Msg $i")
        }
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(allMsgs)
        coEvery { chatRepository.getMessagesPage(CHAT_ID, 50) } returns allMsgs.takeLast(50)
        viewModel = createViewModel()
        // displayMessages uses WhileSubscribed — must actively collect to trigger
        val displayJob = launch(Dispatchers.Unconfined) { viewModel.displayMessages.collect {} }
        val loadedJob = launch(Dispatchers.Unconfined) { viewModel.allMessagesLoaded.collect {} }
        advanceUntilIdle()

        assertEquals(60, viewModel.messages.value.size)
        assertEquals(50, viewModel.displayMessages.value.size)
        assertFalse(viewModel.allMessagesLoaded.value)
        displayJob.cancel()
        loadedJob.cancel()
    }

    @Test
    fun `displayMessages updates when messages flow emits new chat turns`() = runTest {
        val messageFlow = MutableStateFlow(
            listOf(
                MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Smoke chat is ready.")
            )
        )
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns messageFlow
        viewModel = createViewModel()
        val displayJob = launch(Dispatchers.Unconfined) { viewModel.displayMessages.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Smoke chat is ready."), viewModel.displayMessages.value.map { it.content })

        messageFlow.value = listOf(
            MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Smoke chat is ready."),
            MessageEntity(id = 2, chatId = CHAT_ID, role = "user", content = "real_smoke_send_clean"),
            MessageEntity(id = 3, chatId = CHAT_ID, role = "assistant", content = "Smoke reply from local stream.")
        )
        advanceUntilIdle()

        assertEquals(
            listOf("Smoke chat is ready.", "real_smoke_send_clean", "Smoke reply from local stream."),
            viewModel.displayMessages.value.map { it.content }
        )
        displayJob.cancel()
    }

    @Test
    fun `allMessagesLoaded is true when page size exceeds total`() = runTest {
        val msgs = (1..30).map { i ->
            MessageEntity(id = i.toLong(), chatId = CHAT_ID, role = "user", content = "Msg $i")
        }
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(msgs)
        coEvery { chatRepository.getMessagesPage(CHAT_ID, 50) } returns msgs
        viewModel = createViewModel()
        val displayJob = launch(Dispatchers.Unconfined) { viewModel.displayMessages.collect {} }
        val loadedJob = launch(Dispatchers.Unconfined) { viewModel.allMessagesLoaded.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.allMessagesLoaded.value)
        displayJob.cancel()
        loadedJob.cancel()
    }

    @Test
    fun `loadMoreMessages increases page size`() = runTest {
        val allMsgs = (1..120).map { i ->
            MessageEntity(id = i.toLong(), chatId = CHAT_ID, role = "user", content = "Msg $i")
        }
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(allMsgs)
        coEvery { chatRepository.getMessagesPage(CHAT_ID, 50) } returns allMsgs.takeLast(50)
        coEvery { chatRepository.getMessagesPage(CHAT_ID, 100) } returns allMsgs.takeLast(100)
        viewModel = createViewModel()
        val displayJob = launch(Dispatchers.Unconfined) { viewModel.displayMessages.collect {} }
        val loadedJob = launch(Dispatchers.Unconfined) { viewModel.allMessagesLoaded.collect {} }
        advanceUntilIdle()

        assertEquals(50, viewModel.displayMessages.value.size)

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        assertEquals(100, viewModel.displayMessages.value.size)
        displayJob.cancel()
        loadedJob.cancel()
    }

    @Test
    fun `loadMoreMessages caps at total message count`() = runTest {
        val allMsgs = (1..70).map { i ->
            MessageEntity(id = i.toLong(), chatId = CHAT_ID, role = "user", content = "Msg $i")
        }
        every { chatRepository.getMessagesForChat(CHAT_ID) } returns flowOf(allMsgs)
        coEvery { chatRepository.getMessagesPage(CHAT_ID, 50) } returns allMsgs.takeLast(50)
        coEvery { chatRepository.getMessagesPage(CHAT_ID, 70) } returns allMsgs
        viewModel = createViewModel()
        val displayJob = launch(Dispatchers.Unconfined) { viewModel.displayMessages.collect {} }
        val loadedJob = launch(Dispatchers.Unconfined) { viewModel.allMessagesLoaded.collect {} }
        advanceUntilIdle()

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        assertEquals(70, viewModel.displayMessages.value.size)
        assertTrue(viewModel.allMessagesLoaded.value)
        displayJob.cancel()
        loadedJob.cancel()
    }

    // ==================== generateImage ====================

    @Test
    fun `generateImage ignores blank prompt`() = runTest {
        viewModel.streamingManager.generateImage("")
        advanceUntilIdle()

        coVerify(exactly = 0) { imageGenerationService.generateImage(any(), any()) }
    }

    @Test
    fun `generateImage handles CancellationException correctly`() = runTest {
        coEvery { imageGenerationService.generateImage(any(), any()) } throws CancellationException()

        viewModel.streamingManager.generateImage("a cat")
        advanceUntilIdle()

        assertFalse(viewModel.streamingManager.isGenerating.value)
    }

    @Test
    fun `generateImage calls service with correct config`() = runTest {
        coEvery { imageGenerationService.generateImage(any(), any()) } returns "/tmp/image.png"
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any(), any())
        } returns null

        viewModel.streamingManager.generateImage("a cute cat")
        advanceUntilIdle()

        coVerify { imageGenerationService.generateImage("a cute cat", testConfig) }
    }

    @Test
    fun `generateImage sends message on success`() = runTest {
        coEvery { imageGenerationService.generateImage(any(), any()) } returns "/tmp/image.png"
        coEvery {
            sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any(), any())
        } returns null

        viewModel.streamingManager.generateImage("a cute cat")
        advanceUntilIdle()

        coVerify {
            sendMessageUseCase.sendSingleMessage(
                CHAT_ID,
                testCharacter,
                "/imagine a cute cat",
                testConfig,
                null,
                listOf("/tmp/image.png")
            )
        }
        coVerify(exactly = 0) { chatRepository.sendMessage(CHAT_ID, any(), "user", any(), any()) }
    }

    @Test
    fun `generateImage shows toast on service failure`() = runTest {
        coEvery { imageGenerationService.generateImage(any(), any()) } returns null

        val toasts = mutableListOf<String>()
        val job = launch { viewModel.toastMessage.collect { toasts.add(it) } }

        viewModel.streamingManager.generateImage("a cat")
        advanceUntilIdle()

        assertTrue(toasts.any { it.contains("图片生成失败") })
        job.cancel()
    }

    // ==================== getCharacterForMessage ====================

    @Test
    fun `getCharacterForMessage returns character in single chat`() = runTest {
        val msg = MessageEntity(id = 1, chatId = CHAT_ID, role = "assistant", content = "Hi")
        assertEquals(testCharacter, viewModel.getCharacterForMessage(msg))
    }

    // ==================== quick replies ====================

    @Test
    fun `executeQuickReply returns actions echoes and blocked reasons`() {
        val reply = QuickReplyEntity(setId = 1, label = "Draft", script = "/input hi")
        every { stScriptLiteExecutor.execute(reply, any(), any()) } returns StScriptExecutionResult(
            actions = listOf(StScriptAction.SetInput("hi")),
            echoes = listOf("ok", "", "ok"),
            blockedCommands = listOf(
                StScriptBlockedCommand(
                    command = StScriptCommand(type = StScriptCommandType.Send, argument = "blocked"),
                    reason = "Sending messages is not allowed"
                ),
                StScriptBlockedCommand(
                    command = StScriptCommand(type = StScriptCommandType.Send, argument = "again"),
                    reason = "Sending messages is not allowed"
                ),
                StScriptBlockedCommand(
                    command = StScriptCommand(type = StScriptCommandType.Send, argument = "blank"),
                    reason = ""
                )
            )
        )

        val result = viewModel.executeQuickReply(reply)

        assertEquals(listOf(StScriptAction.SetInput("hi")), result.actions)
        assertEquals(listOf("ok"), result.echoes)
        assertEquals(listOf("Sending messages is not allowed"), result.blockedReasons)
    }

    @Test
    fun `triggerQuickReplyAutomation maps automation result to ui result`() = runTest {
        val reply = QuickReplyEntity(setId = 1, label = "Open", script = "/input hi")
        coEvery {
            quickReplyAutomationTriggerUseCase("chat_open", CHARACTER_ID, CHAT_ID)
        } returns QuickReplyAutomationTriggerResult(
            automationId = "chat_open",
            executions = listOf(
                QuickReplyAutomationExecution(
                    reply = reply,
                    result = StScriptExecutionResult(
                        actions = listOf(StScriptAction.SetInput("hi")),
                        echoes = listOf("ready", "", "ready"),
                        blockedCommands = listOf(
                            StScriptBlockedCommand(
                                command = StScriptCommand(type = StScriptCommandType.Send, argument = "blocked"),
                                reason = "Command is not safe for auto-run"
                            ),
                            StScriptBlockedCommand(
                                command = StScriptCommand(type = StScriptCommandType.Send, argument = "again"),
                                reason = "Command is not safe for auto-run"
                            )
                        )
                    )
                ),
                QuickReplyAutomationExecution(
                    reply = reply.copy(id = 2, label = "Confirm"),
                    result = StScriptExecutionResult(
                        actions = listOf(StScriptAction.SetInput("second")),
                        echoes = listOf("ready", "next"),
                        blockedCommands = listOf(
                            StScriptBlockedCommand(
                                command = StScriptCommand(type = StScriptCommandType.Send, argument = "again"),
                                reason = "Command is not safe for auto-run"
                            )
                        )
                    ),
                    skippedReason = "Confirmation is required"
                )
            ),
            variables = mapOf("mood" to "calm")
        )

        val result = viewModel.triggerQuickReplyAutomation("chat_open")

        assertEquals(listOf(StScriptAction.SetInput("hi"), StScriptAction.SetInput("second")), result.actions)
        assertEquals(listOf("ready", "next"), result.echoes)
        assertEquals(
            listOf("Command is not safe for auto-run", "Confirmation is required"),
            result.blockedReasons
        )
        coVerify { quickReplyAutomationTriggerUseCase("chat_open", CHARACTER_ID, CHAT_ID) }
    }

    @Test
    fun `assistant reply committed event is exposed as one shot flow`() = runTest {
        val events = mutableListOf<Unit>()
        val job = launch(Dispatchers.Unconfined) {
            viewModel.assistantReplyCommitted.collect { events.add(it) }
        }

        viewModel.streamingManager.onAssistantReplyCommitted()
        advanceUntilIdle()

        assertEquals(1, events.size)
        job.cancel()
    }

    // ==================== Helper ====================

    private fun createViewModel(): ChatViewModel {
        return ChatViewModel(
            SavedStateHandle(mapOf("characterId" to CHARACTER_ID, "chatId" to CHAT_ID)),
            characterRepository,
            chatRepository,
            groupChatRepository,
            quickReplyRepository,
            configReader,
            settingsStore,
            promptInspectorBuilder,
            sendMessageUseCase,
            continueGenerationUseCase,
            proactiveMessageUseCase,
            proactiveDialogueUseCase,
            quickReplyAutomationTriggerUseCase,
            stScriptLiteExecutor,
            memoryExtractionUseCase,
            summaryUseCase,
            summaryRepository,
            spriteRepository,
            bgmRepository,
            emotionDetector,
            bgmPlayer,
            imageGenerationService,
            ttsHelper,
            sttHelper,
            markwon
        )
    }
}
