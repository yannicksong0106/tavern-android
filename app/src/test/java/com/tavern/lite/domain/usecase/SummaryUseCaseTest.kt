package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.SummaryEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.SummaryRepository
import com.tavern.lite.domain.port.ChatApiPort
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryUseCaseTest {

    @MockK private lateinit var chatApiService: ChatApiPort
    @MockK private lateinit var summaryRepository: SummaryRepository
    @MockK private lateinit var chatRepository: ChatRepository

    private lateinit var useCase: SummaryUseCase

    private val testConfig = ApiConfig(
        provider = ApiProvider.OpenAI(apiKey = "test-key", model = "gpt-4o"),
        contextLength = 100,
        userName = "User"
    )

    private fun makeMessage(id: Long, role: String, content: String) = MessageEntity(
        id = id,
        chatId = 1L,
        role = role,
        content = content
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase = SummaryUseCase(chatApiService, summaryRepository, chatRepository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== shouldGenerateSummary ====================

    @Test
    fun `shouldGenerateSummary returns false when message count below minimum`() = runTest {
        coEvery { chatRepository.getMessageCount(1L) } returns 5

        val result = useCase.shouldGenerateSummary(1L)

        assertFalse(result)
    }

    @Test
    fun `shouldGenerateSummary returns false when messages since last summary below threshold`() = runTest {
        coEvery { chatRepository.getMessageCount(1L) } returns 20
        coEvery { summaryRepository.getLatestSummary(1L) } returns SummaryEntity(
            id = 1, chatId = 1L, content = "Previous summary",
            messageRangeStart = 1, messageRangeEnd = 15, tokenCount = 100, createdAt = System.currentTimeMillis()
        )
        coEvery { chatRepository.getMessageCountSince(1L, 15L) } returns 1

        val result = useCase.shouldGenerateSummary(1L)

        assertFalse(result)
    }

    @Test
    fun `shouldGenerateSummary returns true when enough messages since last summary`() = runTest {
        coEvery { chatRepository.getMessageCount(1L) } returns 60
        coEvery { summaryRepository.getLatestSummary(1L) } returns SummaryEntity(
            id = 1, chatId = 1L, content = "Previous summary",
            messageRangeStart = 1, messageRangeEnd = 10, tokenCount = 100, createdAt = System.currentTimeMillis()
        )
        coEvery { chatRepository.getMessageCountSince(1L, 10L) } returns 50

        val result = useCase.shouldGenerateSummary(1L)

        assertTrue(result)
    }

    @Test
    fun `shouldGenerateSummary returns true when no existing summary and enough messages`() = runTest {
        coEvery { chatRepository.getMessageCount(1L) } returns 60
        coEvery { summaryRepository.getLatestSummary(1L) } returns null
        coEvery { chatRepository.getMessageCountSince(1L, 0L) } returns 60

        val result = useCase.shouldGenerateSummary(1L)

        assertTrue(result)
    }

    // ==================== generateSummary ====================

    @Test
    fun `generateSummary returns null when too few messages`() = runTest {
        coEvery { summaryRepository.getLatestSummary(1L) } returns null
        coEvery { chatRepository.getAllMessagesForChat(1L) } returns (1..5).map { makeMessage(it.toLong(), "user", "msg") }

        val result = useCase.generateSummary(1L, testConfig, "Alice")

        assertNull(result)
    }

    @Test
    fun `generateSummary generates and saves summary`() = runTest {
        val messages = (1..20).map { makeMessage(it.toLong(), if (it % 2 == 0) "assistant" else "user", "Message $it") }
        coEvery { summaryRepository.getLatestSummary(1L) } returns null
        coEvery { chatRepository.getAllMessagesForChat(1L) } returns messages
        every { chatApiService.streamChat(any(), any()) } returns flowOf("This is a summary of the conversation.")
        coEvery { summaryRepository.saveSummary(any(), any(), any(), any(), any()) } returns 1L

        val result = useCase.generateSummary(1L, testConfig, "Alice")

        assertEquals("This is a summary of the conversation.", result)
        coVerify { summaryRepository.saveSummary(eq(1L), any(), eq(1L), eq(20L), any()) }
    }

    @Test
    fun `generateSummary appends to existing summary`() = runTest {
        val existingSummary = SummaryEntity(
            id = 1, chatId = 1L, content = "Old summary",
            messageRangeStart = 1, messageRangeEnd = 10, tokenCount = 50, createdAt = System.currentTimeMillis()
        )
        val messages = (11..30).map { makeMessage(it.toLong(), if (it % 2 == 0) "assistant" else "user", "Message $it") }
        coEvery { summaryRepository.getLatestSummary(1L) } returns existingSummary
        coEvery { chatRepository.getAllMessagesForChat(1L) } returns messages
        every { chatApiService.streamChat(any(), any()) } returns flowOf("Updated summary with new content.")
        coEvery { summaryRepository.saveSummary(any(), any(), any(), any(), any()) } returns 1L

        val result = useCase.generateSummary(1L, testConfig, "Alice")

        assertEquals("Updated summary with new content.", result)
        coVerify { summaryRepository.saveSummary(eq(1L), any(), eq(11L), eq(30L), any()) }
    }

    @Test
    fun `generateSummary returns null on blank API response`() = runTest {
        val messages = (1..20).map { makeMessage(it.toLong(), "user", "Message $it") }
        coEvery { summaryRepository.getLatestSummary(1L) } returns null
        coEvery { chatRepository.getAllMessagesForChat(1L) } returns messages
        every { chatApiService.streamChat(any(), any()) } returns flowOf("")

        val result = useCase.generateSummary(1L, testConfig, "Alice")

        assertNull(result)
    }

    @Test
    fun `generateSummary returns null on API failure`() = runTest {
        val messages = (1..20).map { makeMessage(it.toLong(), "user", "Message $it") }
        coEvery { summaryRepository.getLatestSummary(1L) } returns null
        coEvery { chatRepository.getAllMessagesForChat(1L) } returns messages
        every { chatApiService.streamChat(any(), any()) } returns flow { throw RuntimeException("API error") }

        val result = useCase.generateSummary(1L, testConfig, "Alice")

        assertNull(result)
    }

    @Test
    fun `generateSummary rethrows CancellationException`() = runTest {
        val messages = (1..20).map { makeMessage(it.toLong(), "user", "Message $it") }
        coEvery { summaryRepository.getLatestSummary(1L) } returns null
        coEvery { chatRepository.getAllMessagesForChat(1L) } returns messages
        every { chatApiService.streamChat(any(), any()) } returns flow { throw CancellationException("Cancelled") }

        var caught: Throwable? = null
        try {
            useCase.generateSummary(1L, testConfig, "Alice")
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(caught is CancellationException)
    }

    // ==================== getLatestSummaryText ====================

    @Test
    fun `getLatestSummaryText returns null when no summary exists`() = runTest {
        coEvery { summaryRepository.getLatestSummary(1L) } returns null

        val result = useCase.getLatestSummaryText(1L)

        assertNull(result)
    }

    @Test
    fun `getLatestSummaryText returns content when summary exists`() = runTest {
        coEvery { summaryRepository.getLatestSummary(1L) } returns SummaryEntity(
            id = 1, chatId = 1L, content = "Summary text",
            messageRangeStart = 1, messageRangeEnd = 10, tokenCount = 50, createdAt = System.currentTimeMillis()
        )

        val result = useCase.getLatestSummaryText(1L)

        assertEquals("Summary text", result)
    }

    // ==================== deleteAllSummaries ====================

    @Test
    fun `deleteAllSummaries calls repository`() = runTest {
        coEvery { summaryRepository.deleteAllForChat(1L) } just runs

        useCase.deleteAllSummaries(1L)

        coVerify { summaryRepository.deleteAllForChat(1L) }
    }
}
