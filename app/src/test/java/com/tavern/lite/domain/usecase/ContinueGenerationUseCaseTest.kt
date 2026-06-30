package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.AuthorNoteRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.model.ChatStreamChunk
import com.tavern.lite.domain.port.ChatApiPort
import com.tavern.lite.domain.port.PromptBuilderPort
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueGenerationUseCaseTest {

    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var worldBookRepository: WorldBookRepository
    @MockK private lateinit var memoryRepository: MemoryRepository
    @MockK private lateinit var authorNoteRepository: AuthorNoteRepository
    @MockK private lateinit var scriptRepository: ScriptRepository
    @MockK private lateinit var presetRepository: PresetRepository
    @MockK private lateinit var memoryExtractionUseCase: MemoryExtractionUseCase
    @MockK private lateinit var helper: MessageExecutionHelper
    @MockK private lateinit var chatApiService: ChatApiPort
    @MockK private lateinit var promptBuilder: PromptBuilderPort

    private lateinit var useCase: ContinueGenerationUseCase
    private val testDispatcher = StandardTestDispatcher()

    private val testCharacter = CharacterEntity(id = 42, name = "Alice", chattiness = 50)
    private val testConfig = ApiConfig()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        // Mock PromptBuilderPort
        every { promptBuilder.build(any<com.tavern.lite.domain.model.PromptConfig>()) } returns listOf(
            ChatMessage("system", "You are Alice"),
            ChatMessage("user", "Hello")
        )

        // Helper setup
        every { helper.chatApiService } returns chatApiService
        every { helper.attachReasoningContent(any(), any()) } returnsArgument 0
        coEvery { helper.personasafe(any()) } returns null

        // Default DAO mocks
        coEvery { chatRepository.getRecentMessages(any(), any()) } returns listOf(
            MessageEntity(id = 1, chatId = 100, role = "user", content = "Hello"),
            MessageEntity(id = 2, chatId = 100, role = "assistant", content = "Hi!")
        )
        coEvery { chatRepository.getLastUserMessage(any()) } returns MessageEntity(id = 1, chatId = 100, role = "user", content = "Hello")
        coEvery { worldBookRepository.matchEntriesRecursive(any(), any()) } returns emptyList()
        coEvery { memoryRepository.getRelevantAtoms(any(), any()) } returns emptyList()
        coEvery { memoryRepository.touchAtoms(any()) } returns Unit
        coEvery { memoryRepository.getRelevantMemories(any(), any()) } returns emptyList()
        coEvery { authorNoteRepository.getAuthorNoteSync(any()) } returns null
        coEvery { presetRepository.resolveEffectivePreset(any(), any()) } returns null
        coEvery { scriptRepository.applyScripts(any(), any(), any()) } returnsArgument 1
        coEvery { memoryExtractionUseCase.extractIfNeeded(any(), any(), any(), any(), any()) } returns Unit

        useCase = ContinueGenerationUseCase(
            chatRepository, worldBookRepository, memoryRepository,
            authorNoteRepository, scriptRepository, presetRepository, memoryExtractionUseCase, helper, promptBuilder
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ==================== continueGeneration ====================

    @Test
    fun `continueGeneration returns result on successful streaming`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "World"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        val result = useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        assertNotNull(result)
        assertEquals(2L, result?.assistantMsgId)
        assertEquals("World", result?.fullResponse)
    }

    @Test
    fun `continueGeneration appends content to last assistant message`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = " more text"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        coVerify { chatRepository.appendToMessage(2, " more text") }
    }

    @Test
    fun `continueGeneration returns null for empty response`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = ""))

        val result = useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        assertNull(result)
        coVerify(exactly = 0) { chatRepository.appendToMessage(any(), any()) }
    }

    @Test
    fun `continueGeneration returns null for blank response`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "   "))

        val result = useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        assertNull(result)
    }

    @Test
    fun `continueGeneration handles multi-chunk streaming`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Hello"), ChatStreamChunk(content = " "), ChatStreamChunk(content = "World"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        val result = useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        assertNotNull(result)
        assertEquals("Hello World", result?.fullResponse)
    }

    @Test
    fun `continueGeneration applies scripts to response`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "raw response"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit
        coEvery { scriptRepository.applyScripts(any(), any(), any()) } returns "processed response"

        useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        coVerify { chatRepository.updateMessageContent(2, "processed response") }
    }

    @Test
    fun `continueGeneration triggers memory extraction`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "response"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        coVerify { memoryExtractionUseCase.extractIfNeeded(100, 42, "Alice", "", testConfig) }
    }

    @Test
    fun `continueGeneration loads world book entries when character has worldBookId`() = runTest {
        val charWithWorldBook = testCharacter.copy(worldBookId = 10)
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "response"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        useCase.continueGeneration(100, 42, charWithWorldBook, 2, "Hi!", testConfig)

        coVerify { worldBookRepository.matchEntriesRecursive(10, any()) }
    }

    @Test
    fun `continueGeneration loads memory atoms`() = runTest {
        val atoms = listOf(MemoryAtomEntity(id = 1, characterId = 42, content = "fact", category = "personality", importance = 8))
        coEvery { memoryRepository.getRelevantAtoms(42, 10) } returns atoms
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "response"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        coVerify { memoryRepository.touchAtoms(listOf(1)) }
    }

    @Test
    fun `continueGeneration loads memories when no atoms available`() = runTest {
        coEvery { memoryRepository.getRelevantAtoms(any(), any()) } returns emptyList()
        coEvery { memoryRepository.getRelevantMemories(any(), any()) } returns listOf(mockk())
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "response"))
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        coVerify { memoryRepository.getRelevantMemories(42, "") }
    }

    @Test
    fun `continueGeneration handles network error with error message`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } throws java.net.UnknownHostException("unreachable")
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit

        val result = useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        assertNull(result)
        coVerify { chatRepository.appendToMessage(2, match { it.contains("网络连接失败") }) }
    }

    @Test
    fun `continueGeneration handles timeout error`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } throws java.net.SocketTimeoutException("timeout")
        coEvery { chatRepository.appendToMessage(any(), any()) } returns Unit

        val result = useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)

        assertNull(result)
        coVerify { chatRepository.appendToMessage(2, match { it.contains("网络连接失败") }) }
    }

    @Test
    fun `continueGeneration rethrows CancellationException`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } throws CancellationException()

        var thrown = false
        try {
            useCase.continueGeneration(100, 42, testCharacter, 2, "Hi!", testConfig)
        } catch (_: CancellationException) {
            thrown = true
        }

        assert(thrown)
        // Should NOT append error message
        coVerify(exactly = 0) { chatRepository.appendToMessage(any(), any()) }
    }

    // ==================== regenerate ====================

    @Test
    fun `regenerate returns result on successful streaming`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "New reply"))
        coEvery { chatRepository.addSwipe(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        val result = useCase.regenerate(100, 42, testCharacter, 5, "Hello", testConfig)

        assertNotNull(result)
        assertEquals(5L, result?.assistantMsgId)
        assertEquals("New reply", result?.fullResponse)
    }

    @Test
    fun `regenerate adds swipe to message`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Alternative reply"))
        coEvery { chatRepository.addSwipe(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        useCase.regenerate(100, 42, testCharacter, 5, "Hello", testConfig)

        coVerify { chatRepository.addSwipe(5, "Alternative reply") }
        coVerify { chatRepository.updateMessageContent(5, "Alternative reply") }
    }

    @Test
    fun `regenerate returns null for empty response`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = ""))

        val result = useCase.regenerate(100, 42, testCharacter, 5, "Hello", testConfig)

        assertNull(result)
        coVerify(exactly = 0) { chatRepository.addSwipe(any(), any()) }
    }

    @Test
    fun `regenerate applies scripts to response`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "raw"))
        coEvery { chatRepository.addSwipe(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit
        coEvery { scriptRepository.applyScripts(any(), any(), any()) } returns "processed"

        useCase.regenerate(100, 42, testCharacter, 5, "Hello", testConfig)

        coVerify { chatRepository.updateMessageContent(5, "processed") }
    }

    @Test
    fun `regenerate handles network error with swipe error message`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } throws java.net.UnknownHostException("fail")
        coEvery { chatRepository.addSwipe(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        val result = useCase.regenerate(100, 42, testCharacter, 5, "Hello", testConfig)

        assertNull(result)
        coVerify { chatRepository.addSwipe(5, match { it.contains("网络连接失败") }) }
    }

    @Test
    fun `regenerate rethrows CancellationException`() = runTest {
        every { chatApiService.streamChatWithMetadata(any(), any()) } throws CancellationException()

        var thrown = false
        try {
            useCase.regenerate(100, 42, testCharacter, 5, "Hello", testConfig)
        } catch (_: CancellationException) {
            thrown = true
        }

        assert(thrown)
    }

    @Test
    fun `regenerate loads world book entries from user message`() = runTest {
        val charWithWorldBook = testCharacter.copy(worldBookId = 10)
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "response"))
        coEvery { chatRepository.addSwipe(any(), any()) } returns Unit
        coEvery { chatRepository.updateMessageContent(any(), any()) } returns Unit

        useCase.regenerate(100, 42, charWithWorldBook, 5, "user query", testConfig)

        coVerify { worldBookRepository.matchEntriesRecursive(10, "user query") }
    }
}
