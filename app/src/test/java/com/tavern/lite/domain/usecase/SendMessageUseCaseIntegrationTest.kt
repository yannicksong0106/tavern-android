package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class SendMessageUseCaseIntegrationTest {

    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var chatApiService: ChatApiService
    @MockK private lateinit var worldBookRepository: WorldBookRepository
    @MockK private lateinit var memoryAtomDao: MemoryAtomDao
    @MockK private lateinit var memoryRepository: MemoryRepository
    @MockK private lateinit var authorNoteDao: AuthorNoteDao
    @MockK private lateinit var personaRepository: PersonaRepository
    @MockK private lateinit var scriptRepository: ScriptRepository
    @MockK private lateinit var memoryExtractionUseCase: MemoryExtractionUseCase

    private lateinit var useCase: SendMessageUseCase

    private val testConfig = ApiConfig(
        provider = ApiProvider.OpenAI(apiKey = "test-key", model = "gpt-4o"),
        contextLength = 100,
        userName = "User"
    )

    private val testCharacter = CharacterEntity(
        id = 1,
        name = "Alice",
        description = "A friendly AI assistant",
        personality = "Kind and helpful",
        firstMes = "Hello! I'm Alice."
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase = SendMessageUseCase(
            chatRepository = chatRepository,
            chatApiService = chatApiService,
            worldBookRepository = worldBookRepository,
            memoryAtomDao = memoryAtomDao,
            memoryRepository = memoryRepository,
            authorNoteDao = authorNoteDao,
            personaRepository = personaRepository,
            scriptRepository = scriptRepository,
            memoryExtractionUseCase = memoryExtractionUseCase
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun stubDefaults() {
        coEvery { memoryAtomDao.getRelevantAtoms(any(), any(), any()) } returns emptyList()
        coEvery { memoryAtomDao.touchAtoms(any(), any()) } just runs
        coEvery { memoryRepository.getRelevantMemories(any(), any(), any()) } returns emptyList()
        coEvery { authorNoteDao.getAuthorNoteSync(any()) } returns null
        coEvery { personaRepository.getEffectivePersona(any()) } returns null
        coEvery { scriptRepository.applyScripts(any(), any(), any()) } returns ""
        coEvery { chatRepository.getRecentMessages(any(), any()) } returns emptyList()
        coEvery { chatRepository.sendMessage(any(), any(), any(), any(), any()) } returns 100L
        coEvery { chatRepository.getMessageById(any()) } returns null
        coEvery { chatRepository.appendToMessage(any(), any()) } just runs
        coEvery { chatRepository.updateMessageContent(any(), any()) } just runs
        coEvery { chatRepository.addSwipe(any(), any()) } just runs
        coEvery { memoryExtractionUseCase.extractIfNeeded(any(), any(), any(), any(), any()) } just runs
        every { chatApiService.streamChat(any(), any()) } returns flowOf("")
    }

    // ==================== sendSingleMessage ====================

    @Test
    fun `sendSingleMessage saves user message and returns assistant result`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flowOf("Hi there!")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hi there!"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNotNull(result)
        assertEquals(101L, result!!.assistantMsgId)
        assertEquals("Hi there!", result.fullResponse)
        assertEquals("Hello", result.processedUserContent)
    }

    @Test
    fun `sendSingleMessage skips user message when content is blank`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flowOf("Proactive response")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(eq(1L), eq("Proactive response"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendSingleMessage(1L, testCharacter, "", testConfig)

        assertNotNull(result)
        coVerify(exactly = 0) { chatRepository.sendMessage(eq(1L), eq(""), eq("user"), any(), any()) }
    }

    @Test
    fun `sendSingleMessage applies scripts to user content`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Modified Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Modified Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flowOf("Response")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(eq(1L), eq("Response"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNotNull(result)
        assertEquals("Modified Hello", result!!.processedUserContent)
    }

    @Test
    fun `sendSingleMessage returns null on blank API response`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flowOf("")
        every { chatApiService.lastReasoningContent } returns null

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNull(result)
    }

    @Test
    fun `sendSingleMessage saves error message on network failure`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flow { throw UnknownHostException("DNS lookup failed") }

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNull(result)
        coVerify { chatRepository.sendMessage(eq(1L), eq("[网络连接失败，请检查网络设置]"), eq("assistant"), any(), any()) }
    }

    @Test
    fun `sendSingleMessage saves error message on timeout`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flow { throw SocketTimeoutException("Read timed out") }

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNull(result)
        coVerify { chatRepository.sendMessage(eq(1L), eq("[网络连接失败，请检查网络设置]"), eq("assistant"), any(), any()) }
    }

    @Test
    fun `sendSingleMessage saves generic error on unknown exception`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flow { throw RuntimeException("Something unexpected") }

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNull(result)
        coVerify {
            chatRepository.sendMessage(
                eq(1L),
                match { it.startsWith("[生成失败:") },
                eq("assistant"),
                any(),
                any()
            )
        }
    }

    @Test
    fun `sendSingleMessage rethrows CancellationException`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flow { throw CancellationException("User cancelled") }

        var caught: Throwable? = null
        try {
            useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(caught is CancellationException)
    }

    @Test
    fun `sendSingleMessage applies scripts to assistant reply`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChat(any(), any()) } returns flowOf("Raw reply")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(eq(1L), eq("Raw reply"), eq("assistant"), any(), any()) } returns 101L
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Raw reply"), eq(1)) } returns "Processed reply"

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNotNull(result)
        coVerify { chatRepository.updateMessageContent(eq(101L), eq("Processed reply")) }
    }

    // ==================== continueGeneration ====================

    @Test
    fun `continueGeneration appends to last assistant message`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flowOf(" continued")
        every { chatApiService.lastReasoningContent } returns null

        val result = useCase.continueGeneration(
            chatId = 1L, characterId = 1L, character = testCharacter,
            lastAssistantMsgId = 50L, lastAssistantContent = "Original", config = testConfig
        )

        assertNotNull(result)
        assertEquals(50L, result!!.assistantMsgId)
        assertEquals(" continued", result.fullResponse)
        coVerify { chatRepository.appendToMessage(eq(50L), eq(" continued")) }
    }

    @Test
    fun `continueGeneration returns null on blank response`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flowOf("")
        every { chatApiService.lastReasoningContent } returns null

        val result = useCase.continueGeneration(
            chatId = 1L, characterId = 1L, character = testCharacter,
            lastAssistantMsgId = 50L, lastAssistantContent = "Original", config = testConfig
        )

        assertNull(result)
        coVerify(exactly = 0) { chatRepository.appendToMessage(any(), any()) }
    }

    @Test
    fun `continueGeneration saves error to message on IOException`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flow { throw IOException("Connection reset") }

        val result = useCase.continueGeneration(
            chatId = 1L, characterId = 1L, character = testCharacter,
            lastAssistantMsgId = 50L, lastAssistantContent = "Original", config = testConfig
        )

        assertNull(result)
        coVerify { chatRepository.appendToMessage(eq(50L), match { it.contains("网络连接异常") }) }
    }

    @Test
    fun `continueGeneration rethrows CancellationException`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flow { throw CancellationException() }

        var caught: Throwable? = null
        try {
            useCase.continueGeneration(
                chatId = 1L, characterId = 1L, character = testCharacter,
                lastAssistantMsgId = 50L, lastAssistantContent = "Original", config = testConfig
            )
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(caught is CancellationException)
    }

    // ==================== regenerate ====================

    @Test
    fun `regenerate adds swipe and updates message content`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flowOf("New variant")
        every { chatApiService.lastReasoningContent } returns null

        val result = useCase.regenerate(
            chatId = 1L, characterId = 1L, character = testCharacter,
            messageId = 30L, userMessageContent = "Tell me a joke", config = testConfig
        )

        assertNotNull(result)
        assertEquals(30L, result!!.assistantMsgId)
        assertEquals("New variant", result.fullResponse)
        assertEquals("Tell me a joke", result.processedUserContent)
        coVerify { chatRepository.addSwipe(eq(30L), eq("New variant")) }
        coVerify { chatRepository.updateMessageContent(eq(30L), eq("New variant")) }
    }

    @Test
    fun `regenerate returns null on blank response`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flowOf("")
        every { chatApiService.lastReasoningContent } returns null

        val result = useCase.regenerate(
            chatId = 1L, characterId = 1L, character = testCharacter,
            messageId = 30L, userMessageContent = "Tell me a joke", config = testConfig
        )

        assertNull(result)
        coVerify(exactly = 0) { chatRepository.addSwipe(any(), any()) }
    }

    @Test
    fun `regenerate saves error swipe on network failure`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flow { throw SocketTimeoutException("Timeout") }

        val result = useCase.regenerate(
            chatId = 1L, characterId = 1L, character = testCharacter,
            messageId = 30L, userMessageContent = "Tell me a joke", config = testConfig
        )

        assertNull(result)
        coVerify { chatRepository.addSwipe(eq(30L), eq("[网络连接失败，请检查网络设置]")) }
        coVerify { chatRepository.updateMessageContent(eq(30L), eq("[网络连接失败，请检查网络设置]")) }
    }

    @Test
    fun `regenerate rethrows CancellationException`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flow { throw CancellationException() }

        var caught: Throwable? = null
        try {
            useCase.regenerate(
                chatId = 1L, characterId = 1L, character = testCharacter,
                messageId = 30L, userMessageContent = "Tell me a joke", config = testConfig
            )
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(caught is CancellationException)
    }

    @Test
    fun `regenerate applies scripts to new content`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flowOf("New variant")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { scriptRepository.applyScripts(eq(1L), eq("New variant"), eq(1)) } returns "Scripted variant"

        val result = useCase.regenerate(
            chatId = 1L, characterId = 1L, character = testCharacter,
            messageId = 30L, userMessageContent = "Tell me a joke", config = testConfig
        )

        assertNotNull(result)
        coVerify { chatRepository.updateMessageContent(eq(30L), eq("Scripted variant")) }
    }

    // ==================== sendProactiveMessage ====================

    @Test
    fun `sendProactiveMessage returns null when chat history is empty`() = runTest {
        stubDefaults()
        coEvery { chatRepository.getRecentMessages(any(), any()) } returns emptyList()

        val result = useCase.sendProactiveMessage(1L, testCharacter, testConfig)

        assertNull(result)
        verify(exactly = 0) { chatApiService.streamChat(any(), any()) }
    }

    @Test
    fun `sendProactiveMessage sends message when history exists`() = runTest {
        stubDefaults()
        val history = listOf(MessageEntity(id = 1, chatId = 1, role = "user", content = "Hi"))
        coEvery { chatRepository.getRecentMessages(any(), any()) } returns history
        every { chatApiService.streamChat(any(), any()) } returns flowOf("I missed you!")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(eq(1L), eq("I missed you!"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendProactiveMessage(1L, testCharacter, testConfig)

        assertNotNull(result)
        assertEquals("I missed you!", result!!.fullResponse)
    }

    // ==================== sendGroupMessage ====================

    @Test
    fun `sendGroupMessage returns results for each character`() = runTest {
        stubDefaults()
        val char1 = CharacterEntity(id = 1, name = "Alice", description = "AI 1", personality = "Kind", firstMes = "Hi")
        val char2 = CharacterEntity(id = 2, name = "Bob", description = "AI 2", personality = "Funny", firstMes = "Hey")

        every { chatApiService.streamChat(any(), any()) } returns flowOf("Alice speaks") andThen flowOf("Bob speaks")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(any(), eq("Alice speaks"), eq("assistant"), any(), any()) } returns 101L
        coEvery { chatRepository.sendMessage(any(), eq("Bob speaks"), eq("assistant"), any(), any()) } returns 102L
        coEvery { chatRepository.getMessageById(any()) } returns null

        val results = useCase.sendGroupMessage(1L, listOf(char1, char2), "Hello everyone", testConfig)

        assertEquals(2, results.size)
        assertEquals(1L, results[0].first)
        assertEquals("Alice speaks", results[0].second.fullResponse)
        assertEquals(2L, results[1].first)
        assertEquals("Bob speaks", results[1].second.fullResponse)
    }

    @Test
    fun `sendGroupMessage skips character on API failure but continues others`() = runTest {
        stubDefaults()
        val char1 = CharacterEntity(id = 1, name = "Alice", description = "AI 1", personality = "Kind", firstMes = "Hi")
        val char2 = CharacterEntity(id = 2, name = "Bob", description = "AI 2", personality = "Funny", firstMes = "Hey")

        every { chatApiService.streamChat(any(), any()) } returns flow { throw IOException("Fail") } andThen flowOf("Bob speaks")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(any(), any(), eq("assistant"), any(), any()) } returns 101L
        coEvery { chatRepository.getMessageById(any()) } returns null

        val results = useCase.sendGroupMessage(1L, listOf(char1, char2), "Hello", testConfig)

        assertEquals(1, results.size)
        assertEquals(2L, results[0].first)
    }

    // ==================== sendDirectMessage ====================

    @Test
    fun `sendDirectMessage targets specific character`() = runTest {
        stubDefaults()
        val char1 = CharacterEntity(id = 1, name = "Alice", description = "AI 1", personality = "Kind", firstMes = "Hi")
        val char2 = CharacterEntity(id = 2, name = "Bob", description = "AI 2", personality = "Funny", firstMes = "Hey")

        every { chatApiService.streamChat(any(), any()) } returns flowOf("Bob answers")
        every { chatApiService.lastReasoningContent } returns null
        coEvery { chatRepository.sendMessage(any(), eq("Bob answers"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendDirectMessage(
            chatId = 1L, characters = listOf(char1, char2),
            targetCharacter = char2, userContent = "@Bob what do you think?", config = testConfig
        )

        assertNotNull(result)
        assertEquals("Bob answers", result!!.fullResponse)
    }

    // ==================== reasoning content ====================

    @Test
    fun `attachReasoningContent returns original when no reasoning stored`() {
        val messages = listOf(
            ChatMessage("user", "Hello"),
            ChatMessage("assistant", "Hi!")
        )
        val result = useCase.attachReasoningContent(messages)
        assertEquals(messages, result)
    }

    @Test
    fun `attachReasoningContent attaches to last assistant message after API call`() = runTest {
        stubDefaults()
        every { chatApiService.streamChat(any(), any()) } returns flowOf("Response")
        every { chatApiService.lastReasoningContent } returns "Deep thinking..."
        coEvery { chatRepository.sendMessage(any(), any(), any(), any(), any()) } returns 100L

        useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        val messages = listOf(
            ChatMessage("user", "Hello"),
            ChatMessage("assistant", "Response")
        )
        val result = useCase.attachReasoningContent(messages)
        assertEquals("Deep thinking...", result[1].reasoningContent)
        assertNull(result[0].reasoningContent)
    }
}
