package com.tavern.lite.domain.usecase

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryConsolidator
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.network.MemoryExtractorService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MemoryExtractionUseCaseTest {

    private lateinit var memoryExtractorService: MemoryExtractorService
    private lateinit var memoryConsolidator: MemoryConsolidator
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: MemoryExtractionUseCase

    @Before
    fun setup() {
        memoryExtractorService = mockk(relaxed = true)
        memoryConsolidator = mockk(relaxed = true)
        memoryRepository = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        useCase = MemoryExtractionUseCase(
            memoryExtractorService = memoryExtractorService,
            memoryConsolidator = memoryConsolidator,
            memoryRepository = memoryRepository,
            chatRepository = chatRepository
        )
    }

    @Test
    fun `setMessageCount and getMessageCount work correctly`() {
        assertEquals(0, useCase.getMessageCount())

        useCase.setMessageCount(5)
        assertEquals(5, useCase.getMessageCount())

        useCase.setMessageCount(0)
        assertEquals(0, useCase.getMessageCount())
    }

    @Test
    fun `extractIfNeeded does quick extraction always`() = runTest {
        coEvery { memoryExtractorService.extractQuickFacts(any(), any(), any(), any()) } returns emptyList()
        every { memoryExtractorService.shouldExtract(any()) } returns false

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "Hello, my name is Bob"
        )

        coVerify { memoryExtractorService.extractQuickFacts(10L, "Hello, my name is Bob", 1L, null) }
    }

    @Test
    fun `extractIfNeeded inserts quick facts when found`() = runTest {
        val quickFacts = listOf(mockk<com.tavern.lite.data.db.entity.MemoryAtomEntity>(relaxed = true))
        coEvery { memoryExtractorService.extractQuickFacts(any(), any(), any(), any()) } returns quickFacts
        every { memoryExtractorService.shouldExtract(any()) } returns false

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "My name is Bob"
        )

        coVerify { memoryConsolidator.insertWithDedup(quickFacts) }
    }

    @Test
    fun `extractIfNeeded skips LLM extraction when shouldExtract returns false`() = runTest {
        coEvery { memoryExtractorService.extractQuickFacts(any(), any(), any(), any()) } returns emptyList()
        every { memoryExtractorService.shouldExtract(5) } returns false

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "Hello",
            currentMessageCount = 5
        )

        coVerify(exactly = 0) { memoryExtractorService.extractWithLLM(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `extractIfNeeded skips LLM extraction when config is null`() = runTest {
        coEvery { memoryExtractorService.extractQuickFacts(any(), any(), any(), any()) } returns emptyList()
        every { memoryExtractorService.shouldExtract(any()) } returns true

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "Hello",
            config = null,
            currentMessageCount = 10
        )

        coVerify(exactly = 0) { memoryExtractorService.extractWithLLM(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `extractIfNeeded does LLM extraction when conditions met`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))
        coEvery { memoryExtractorService.extractQuickFacts(any(), any(), any(), any()) } returns emptyList()
        every { memoryExtractorService.shouldExtract(10) } returns true
        coEvery { chatRepository.getRecentMessages(1L, 30) } returns emptyList()
        coEvery { memoryExtractorService.extractWithLLM(any(), any(), any(), any(), any()) } returns emptyList()

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "Hello",
            config = config,
            currentMessageCount = 10
        )

        coVerify { memoryExtractorService.extractWithLLM(10L, any(), "Alice", config, 1L) }
    }

    @Test
    fun `extractIfNeeded consolidates after LLM extraction`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))
        val llmFacts = listOf(mockk<com.tavern.lite.data.db.entity.MemoryAtomEntity>(relaxed = true))
        coEvery { memoryExtractorService.extractQuickFacts(any(), any(), any(), any()) } returns emptyList()
        every { memoryExtractorService.shouldExtract(10) } returns true
        coEvery { chatRepository.getRecentMessages(1L, 30) } returns emptyList()
        coEvery { memoryExtractorService.extractWithLLM(any(), any(), any(), any(), any()) } returns llmFacts

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "Hello",
            config = config,
            currentMessageCount = 10
        )

        coVerify { memoryConsolidator.insertWithDedup(llmFacts) }
        coVerify { memoryConsolidator.maybeConsolidate(10L) }
    }

    @Test
    fun `extractIfNeeded uses internal counter when currentMessageCount is null`() = runTest {
        coEvery { memoryExtractorService.extractQuickFacts(any(), any(), any(), any()) } returns emptyList()
        every { memoryExtractorService.shouldExtract(any()) } returns false

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "Hello"
        )

        assertEquals(1, useCase.getMessageCount())

        useCase.extractIfNeeded(
            chatId = 1L,
            characterId = 10L,
            characterName = "Alice",
            userContent = "Hello again"
        )

        assertEquals(2, useCase.getMessageCount())
    }
}
