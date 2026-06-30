package com.tavern.lite.domain.usecase

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.port.ChatApiPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TestConnectionUseCaseTest {

    private val chatApiService: ChatApiPort = mockk()
    private val useCase = TestConnectionUseCase(chatApiService)

    @Test
    fun `invoke sends small connection prompt and returns preview`() = runTest {
        val messagesSlot = slot<List<ChatMessage>>()
        val configSlot = slot<ApiConfig>()
        every { chatApiService.streamChat(capture(messagesSlot), capture(configSlot)) } returns flowOf("hello", " world")

        val result = useCase(ApiConfig(maxTokens = 2048))

        assertEquals("hello world", result)
        assertEquals(50, configSlot.captured.maxTokens)
        assertEquals(listOf(ChatMessage(role = "user", content = "Say 'hello' in one word.")), messagesSlot.captured)
    }

    @Test
    fun `invoke limits reply preview length`() = runTest {
        every { chatApiService.streamChat(any(), any()) } returns flowOf("x".repeat(120))

        val result = useCase(ApiConfig())

        assertEquals(100, result.length)
    }
}
