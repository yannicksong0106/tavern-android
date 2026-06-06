package com.tavern.lite.domain.helper

import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.domain.usecase.MemoryExtractionUseCase
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MessageExecutionHelperTest {

    private lateinit var helper: MessageExecutionHelper

    @Before
    fun setup() {
        helper = MessageExecutionHelper(
            chatRepository = mockk(),
            chatApiService = mockk(),
            worldBookRepository = mockk(),
            memoryRepository = mockk(),
            personaRepository = mockk(),
            scriptRepository = mockk(),
            memoryExtractionUseCase = mockk()
        )
    }

    @Test
    fun `attachReasoningContent returns original messages when reasoning is null`() {
        val messages = listOf(
            ChatMessage("system", "You are a helper"),
            ChatMessage("user", "Hello"),
            ChatMessage("assistant", "Hi there")
        )

        val result = helper.attachReasoningContent(messages, null)

        assertEquals(messages, result)
        assertNull(result.last().reasoningContent)
    }

    @Test
    fun `attachReasoningContent attaches to last assistant message`() {
        val messages = listOf(
            ChatMessage("system", "You are a helper"),
            ChatMessage("user", "Hello"),
            ChatMessage("assistant", "Hi there")
        )

        val result = helper.attachReasoningContent(messages, "I was thinking...")

        assertEquals(3, result.size)
        assertNull(result[0].reasoningContent)
        assertNull(result[1].reasoningContent)
        assertEquals("I was thinking...", result[2].reasoningContent)
    }

    @Test
    fun `attachReasoningContent returns original when no assistant messages`() {
        val messages = listOf(
            ChatMessage("system", "You are a helper"),
            ChatMessage("user", "Hello")
        )

        val result = helper.attachReasoningContent(messages, "thinking")

        assertEquals(messages, result)
    }

    @Test
    fun `attachReasoningContent attaches to last assistant in multi-turn`() {
        val messages = listOf(
            ChatMessage("user", "Q1"),
            ChatMessage("assistant", "A1"),
            ChatMessage("user", "Q2"),
            ChatMessage("assistant", "A2")
        )

        val result = helper.attachReasoningContent(messages, "deep thought")

        assertNull(result[1].reasoningContent) // first assistant
        assertEquals("deep thought", result[3].reasoningContent) // last assistant
    }

    @Test
    fun `attachReasoningContent does not mutate original list`() {
        val messages = listOf(
            ChatMessage("user", "Hello"),
            ChatMessage("assistant", "Hi")
        )

        helper.attachReasoningContent(messages, "thinking")

        assertNull(messages[1].reasoningContent)
    }
}
