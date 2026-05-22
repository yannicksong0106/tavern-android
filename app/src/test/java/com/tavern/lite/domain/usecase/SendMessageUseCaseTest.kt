package com.tavern.lite.domain.usecase

import com.tavern.lite.network.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SendMessageUseCase internal utility methods.
 * We test the pure logic functions directly since they are internal.
 */
class SendMessageUseCaseTest {

    // === cleanCharacterPrefix tests ===

    @Test
    fun `cleanCharacterPrefix removes bracket prefix with colon`() {
        val result = cleanCharacterPrefix("[Alice]: Hello there", "Alice")
        assertEquals("Hello there", result)
    }

    @Test
    fun `cleanCharacterPrefix removes bracket prefix with chinese colon`() {
        val result = cleanCharacterPrefix("[Alice]：你好", "Alice")
        assertEquals("你好", result)
    }

    @Test
    fun `cleanCharacterPrefix removes bracket prefix with space`() {
        val result = cleanCharacterPrefix("[Alice] Hello", "Alice")
        assertEquals("Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix removes bracket prefix with tab`() {
        val result = cleanCharacterPrefix("[Alice]\tHello", "Alice")
        assertEquals("Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix returns original when no prefix`() {
        val result = cleanCharacterPrefix("Just a normal message", "Alice")
        assertEquals("Just a normal message", result)
    }

    @Test
    fun `cleanCharacterPrefix handles empty response`() {
        val result = cleanCharacterPrefix("", "Alice")
        assertEquals("", result)
    }

    @Test
    fun `cleanCharacterPrefix handles whitespace-only after prefix`() {
        val result = cleanCharacterPrefix("[Alice]   ", "Alice")
        assertEquals("", result)
    }

    @Test
    fun `cleanCharacterPrefix trims leading whitespace`() {
        val result = cleanCharacterPrefix("  [Alice]: Hello  ", "Alice")
        assertEquals("Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix handles multi-line content`() {
        val result = cleanCharacterPrefix("[Alice]: Line 1\nLine 2", "Alice")
        assertEquals("Line 1\nLine 2", result)
    }

    @Test
    fun `cleanCharacterPrefix does not remove wrong name`() {
        val result = cleanCharacterPrefix("[Bob]: Hello", "Alice")
        assertEquals("[Bob]: Hello", result)
    }

    // === attachReasoningContent tests ===

    @Test
    fun `attachReasoningContent returns original when no reasoning`() {
        val messages = listOf(
            ChatMessage("user", "Hello"),
            ChatMessage("assistant", "Hi!")
        )
        val result = attachReasoningContent(messages, null)
        assertEquals(messages, result)
    }

    @Test
    fun `attachReasoningContent attaches to last assistant message`() {
        val messages = listOf(
            ChatMessage("user", "Hello"),
            ChatMessage("assistant", "Hi!"),
            ChatMessage("user", "How are you?"),
            ChatMessage("assistant", "I'm good")
        )
        val result = attachReasoningContent(messages, "thinking...")
        assertEquals(4, result.size)
        assertEquals("thinking...", result[3].reasoningContent)
        assertEquals(null, result[1].reasoningContent) // Not the first assistant
    }

    @Test
    fun `attachReasoningContent does not modify when no assistant message`() {
        val messages = listOf(
            ChatMessage("user", "Hello"),
            ChatMessage("user", "World")
        )
        val result = attachReasoningContent(messages, "thinking...")
        assertEquals(messages, result)
    }

    @Test
    fun `attachReasoningContent handles single assistant message`() {
        val messages = listOf(ChatMessage("assistant", "Hello"))
        val result = attachReasoningContent(messages, "reasoning")
        assertEquals("reasoning", result[0].reasoningContent)
    }

    @Test
    fun `attachReasoningContent preserves other messages`() {
        val messages = listOf(
            ChatMessage("user", "Q1"),
            ChatMessage("assistant", "A1"),
            ChatMessage("user", "Q2"),
            ChatMessage("assistant", "A2")
        )
        val result = attachReasoningContent(messages, "thought")
        assertEquals("Q1", result[0].content)
        assertEquals("A1", result[1].content)
        assertEquals("Q2", result[2].content)
        assertEquals("A2", result[3].content)
        assertEquals("thought", result[3].reasoningContent)
    }
}

// === Helper functions that mirror the internal methods ===

private fun cleanCharacterPrefix(response: String, charName: String): String {
    val trimmed = response.trim()
    val prefix = "[$charName]"
    if (!trimmed.startsWith(prefix)) return trimmed
    val afterPrefix = trimmed.substring(prefix.length)
    var i = 0
    while (i < afterPrefix.length && (afterPrefix[i] == ':' || afterPrefix[i] == '：' || afterPrefix[i] == ' ' || afterPrefix[i] == '\t')) {
        i++
    }
    return afterPrefix.substring(i).trim()
}

private fun attachReasoningContent(messages: List<ChatMessage>, reasoning: String?): List<ChatMessage> {
    if (reasoning == null) return messages
    for (i in messages.indices.reversed()) {
        if (messages[i].role == "assistant") {
            return messages.toMutableList().also {
                it[i] = it[i].copy(reasoningContent = reasoning)
            }
        }
    }
    return messages
}
