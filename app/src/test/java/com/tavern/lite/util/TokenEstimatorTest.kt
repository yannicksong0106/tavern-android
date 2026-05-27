package com.tavern.lite.util

import com.tavern.lite.network.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `estimateText returns 0 for empty string`() {
        assertEquals(0, TokenEstimator.estimateText(""))
    }

    @Test
    fun `estimateText estimates ASCII text roughly 4 chars per token`() {
        // "hello" = 5 chars -> (5+3)/4 = 2 tokens
        assertEquals(2, TokenEstimator.estimateText("hello"))
    }

    @Test
    fun `estimateText estimates CJK text roughly 1 token per char`() {
        assertEquals(4, TokenEstimator.estimateText("你好世界"))
        assertEquals(3, TokenEstimator.estimateText("あいう"))
    }

    @Test
    fun `estimateText handles mixed ASCII and CJK`() {
        // "hi你好" -> "hi" = (2+3)/4=1, "你"=1, "好"=1 -> 3 tokens
        assertEquals(3, TokenEstimator.estimateText("hi你好"))
    }

    @Test
    fun `estimateText handles long ASCII text`() {
        // 100 chars -> (100+3)/4 = 25 tokens
        val text = "a".repeat(100)
        assertEquals(25, TokenEstimator.estimateText(text))
    }

    @Test
    fun `estimateText handles Japanese hiragana and katakana`() {
        // "あいう" (hiragana) = 3 chars = 3 tokens
        assertEquals(3, TokenEstimator.estimateText("あいう"))
        // "アイウ" (katakana) = 3 chars = 3 tokens
        assertEquals(3, TokenEstimator.estimateText("アイウ"))
    }

    @Test
    fun `estimateText handles Korean`() {
        // "안녕" = 2 chars = 2 tokens
        assertEquals(2, TokenEstimator.estimateText("안녕"))
    }

    @Test
    fun `estimateMessages returns overhead plus content tokens`() {
        val messages = listOf(
            ChatMessage(role = "user", content = "hi"),      // overhead=4, content=1 -> 5
            ChatMessage(role = "assistant", content = "hello") // overhead=4, content=2 -> 6
        )
        assertEquals(11, TokenEstimator.estimateMessages(messages))
    }

    @Test
    fun `estimateMessages includes reasoning content tokens`() {
        val messages = listOf(
            ChatMessage(role = "assistant", content = "ok", reasoningContent = "thinking")
        )
        // overhead=4, content=1, reasoning=2 -> 7
        assertEquals(7, TokenEstimator.estimateMessages(messages))
    }

    @Test
    fun `estimateMessages returns 0 for empty list`() {
        assertEquals(0, TokenEstimator.estimateMessages(emptyList()))
    }

    @Test
    fun `formatTokenCount formats under 1000 as integer`() {
        assertEquals("500", TokenEstimator.formatTokenCount(500))
        assertEquals("0", TokenEstimator.formatTokenCount(0))
    }

    @Test
    fun `formatTokenCount formats 1000 and above with k suffix`() {
        assertEquals("1.0k", TokenEstimator.formatTokenCount(1000))
        assertEquals("4.5k", TokenEstimator.formatTokenCount(4500))
        assertEquals("12.3k", TokenEstimator.formatTokenCount(12300))
    }
}
