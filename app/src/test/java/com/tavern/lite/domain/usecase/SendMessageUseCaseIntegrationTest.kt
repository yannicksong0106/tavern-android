package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.data.repository.AuthorNoteRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.model.ChatStreamChunk
import com.tavern.lite.domain.port.ChatApiPort
import com.tavern.lite.domain.port.PromptBuilderPort
import com.tavern.lite.domain.port.WebSearchPort
import com.tavern.lite.data.model.WebSearchConfig
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    @MockK private lateinit var chatApiService: ChatApiPort
    @MockK private lateinit var worldBookRepository: WorldBookRepository
    @MockK private lateinit var memoryRepository: MemoryRepository
    @MockK private lateinit var authorNoteRepository: AuthorNoteRepository
    @MockK private lateinit var scriptRepository: ScriptRepository
    @MockK private lateinit var presetRepository: PresetRepository
    @MockK private lateinit var memoryExtractionUseCase: MemoryExtractionUseCase
    @MockK private lateinit var summaryUseCase: SummaryUseCase
    @MockK private lateinit var webSearchService: WebSearchPort
    @MockK private lateinit var settingsStore: SettingsStore
    @MockK private lateinit var promptBuilder: PromptBuilderPort

    private lateinit var helper: MessageExecutionHelper
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
        helper = MessageExecutionHelper(
            chatRepository = chatRepository,
            chatApiService = chatApiService,
            worldBookRepository = worldBookRepository,
            memoryRepository = memoryRepository,
            personaRepository = mockk(), // not used directly by SendMessageUseCase
            scriptRepository = scriptRepository,
            memoryExtractionUseCase = memoryExtractionUseCase
        )
        useCase = SendMessageUseCase(
            chatRepository = chatRepository,
            worldBookRepository = worldBookRepository,
            memoryRepository = memoryRepository,
            authorNoteRepository = authorNoteRepository,
            scriptRepository = scriptRepository,
            presetRepository = presetRepository,
            helper = helper,
            summaryUseCase = summaryUseCase,
            promptBuilder = promptBuilder,
            webSearchService = webSearchService,
            settingsStore = settingsStore,
            appScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun stubDefaults() {
        coEvery { memoryRepository.getRelevantAtoms(any(), any()) } returns emptyList()
        coEvery { memoryRepository.touchAtoms(any()) } just runs
        coEvery { memoryRepository.getRelevantMemories(any(), any(), any()) } returns emptyList()
        coEvery { authorNoteRepository.getAuthorNoteSync(any()) } returns null
        coEvery { scriptRepository.applyScripts(any(), any(), any()) } returns ""
        coEvery { chatRepository.getRecentMessages(any(), any()) } returns emptyList()
        coEvery { chatRepository.sendMessage(any(), any(), any(), any(), any()) } returns 100L
        coEvery { chatRepository.getMessageById(any()) } returns null
        coEvery { chatRepository.appendToMessage(any(), any()) } just runs
        coEvery { chatRepository.updateMessageContent(any(), any()) } just runs
        coEvery { chatRepository.addSwipe(any(), any()) } just runs
        every { promptBuilder.build(any()) } returns listOf(ChatMessage("system", "test"))
        every { promptBuilder.buildGroupChat(any()) } returns listOf(ChatMessage("system", "test"))
        every { promptBuilder.buildProactive(any()) } returns listOf(ChatMessage("system", "test"))
        every { promptBuilder.buildGroupProactive(any()) } returns listOf(ChatMessage("system", "test"))
        coEvery { memoryExtractionUseCase.extractIfNeeded(any(), any(), any(), any(), any()) } just runs
        coEvery { summaryUseCase.getLatestSummaryText(any()) } returns null
        coEvery { summaryUseCase.shouldGenerateSummary(any(), any()) } returns false
        coEvery { presetRepository.resolveEffectivePreset(any(), any()) } returns null
        coEvery { settingsStore.webSearchConfigFlow } returns flowOf(WebSearchConfig())
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = ""))
    }

    // ==================== sendSingleMessage ====================

    @Test
    fun `sendSingleMessage saves user message and returns assistant result`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Hi there!"))
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hi there!"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNotNull(result)
        assertEquals(101L, result!!.assistantMsgId)
        assertEquals("Hi there!", result.fullResponse)
        assertEquals("Hello", result.processedUserContent)
    }

    @Test
    fun `sendSingleMessage returns reasoning content from metadata stream`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(
            ChatStreamChunk(reasoningContent = "think-1 "),
            ChatStreamChunk(content = "Hi "),
            ChatStreamChunk(reasoningContent = "think-2"),
            ChatStreamChunk(content = "there!")
        )
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hi there!"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNotNull(result)
        assertEquals("Hi there!", result!!.fullResponse)
        assertEquals("think-1 think-2", result.reasoningContent)
    }

    @Test
    fun `sendSingleMessage skips user message when content is blank`() = runTest {
        stubDefaults()
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Proactive response"))
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
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Response"))
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
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = ""))

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNull(result)
    }

    @Test
    fun `sendSingleMessage saves error message on network failure`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flow { throw UnknownHostException("DNS lookup failed") }

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNull(result)
        coVerify { chatRepository.sendMessage(eq(1L), eq("[网络连接失败，请检查网络设置]"), eq("assistant"), any(), any()) }
    }

    @Test
    fun `sendSingleMessage saves error message on timeout`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flow { throw SocketTimeoutException("Read timed out") }

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNull(result)
        coVerify { chatRepository.sendMessage(eq(1L), eq("[网络连接失败，请检查网络设置]"), eq("assistant"), any(), any()) }
    }

    @Test
    fun `sendSingleMessage saves generic error on unknown exception`() = runTest {
        stubDefaults()
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Hello"), eq(0)) } returns "Hello"
        coEvery { chatRepository.sendMessage(eq(1L), eq("Hello"), eq("user"), any(), any()) } returns 100L
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flow { throw RuntimeException("Something unexpected") }

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
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flow { throw CancellationException("User cancelled") }

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
        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Raw reply"))
        coEvery { chatRepository.sendMessage(eq(1L), eq("Raw reply"), eq("assistant"), any(), any()) } returns 101L
        coEvery { scriptRepository.applyScripts(eq(1L), eq("Raw reply"), eq(1)) } returns "Processed reply"

        val result = useCase.sendSingleMessage(1L, testCharacter, "Hello", testConfig)

        assertNotNull(result)
        coVerify { chatRepository.updateMessageContent(eq(101L), eq("Processed reply")) }
    }

    // ==================== sendGroupMessage ====================

    @Test
    fun `sendGroupMessage returns results for each character`() = runTest {
        stubDefaults()
        val char1 = CharacterEntity(id = 1, name = "Alice", description = "AI 1", personality = "Kind", firstMes = "Hi")
        val char2 = CharacterEntity(id = 2, name = "Bob", description = "AI 2", personality = "Funny", firstMes = "Hey")

        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Alice speaks")) andThen flowOf(ChatStreamChunk(content = "Bob speaks"))
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

        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flow { throw IOException("Fail") } andThen flowOf(ChatStreamChunk(content = "Bob speaks"))
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

        every { chatApiService.streamChatWithMetadata(any(), any()) } returns flowOf(ChatStreamChunk(content = "Bob answers"))
        coEvery { chatRepository.sendMessage(any(), eq("Bob answers"), eq("assistant"), any(), any()) } returns 101L

        val result = useCase.sendDirectMessage(
            chatId = 1L, characters = listOf(char1, char2),
            targetCharacter = char2, userContent = "@Bob what do you think?", config = testConfig
        )

        assertNotNull(result)
        assertEquals("Bob answers", result!!.fullResponse)
    }
}
