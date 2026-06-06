package com.tavern.lite.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatApiServiceTest {

    private lateinit var service: ChatApiService

    @Before
    fun setUp() {
        service = ChatApiService(okhttp3.OkHttpClient())
    }

    // ── buildMessagesArray ─────────────────────────────────────────────

    @Test
    fun `buildMessagesArray single text message`() {
        val messages = listOf(ChatMessage(role = "user", content = "hello"))
        val arr = service.buildMessagesArray(messages)

        assertEquals(1, arr.length())
        val obj = arr.getJSONObject(0)
        assertEquals("user", obj.getString("role"))
        assertEquals("hello", obj.getString("content"))
    }

    @Test
    fun `buildMessagesArray system and user messages`() {
        val messages = listOf(
            ChatMessage(role = "system", content = "You are helpful"),
            ChatMessage(role = "user", content = "hi"),
            ChatMessage(role = "assistant", content = "hello!")
        )
        val arr = service.buildMessagesArray(messages)

        assertEquals(3, arr.length())
        assertEquals("system", arr.getJSONObject(0).getString("role"))
        assertEquals("user", arr.getJSONObject(1).getString("role"))
        assertEquals("assistant", arr.getJSONObject(2).getString("role"))
    }

    @Test
    fun `buildMessagesArray multimodal with image urls`() {
        val messages = listOf(
            ChatMessage(
                role = "user",
                content = "describe this",
                imageUrls = listOf("https://example.com/img1.png", "data:image/png;base64,abc")
            )
        )
        val arr = service.buildMessagesArray(messages)
        val msg = arr.getJSONObject(0)

        // content should be an array (multimodal format)
        val content = msg.getJSONArray("content")
        assertEquals(3, content.length()) // 1 text + 2 images

        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("describe this", content.getJSONObject(0).getString("text"))

        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertEquals("https://example.com/img1.png",
            content.getJSONObject(1).getJSONObject("image_url").getString("url"))

        assertEquals("image_url", content.getJSONObject(2).getString("type"))
        assertEquals("data:image/png;base64,abc",
            content.getJSONObject(2).getJSONObject("image_url").getString("url"))
    }

    @Test
    fun `buildMessagesArray text-only message has string content`() {
        val messages = listOf(ChatMessage(role = "user", content = "plain text"))
        val arr = service.buildMessagesArray(messages)
        val msg = arr.getJSONObject(0)

        // content should be a plain string, not an array
        assertTrue(msg.get("content") is String)
        assertEquals("plain text", msg.getString("content"))
    }

    @Test
    fun `buildMessagesArray includes reasoning content`() {
        val messages = listOf(
            ChatMessage(role = "assistant", content = "answer", reasoningContent = "thinking...")
        )
        val arr = service.buildMessagesArray(messages)
        val msg = arr.getJSONObject(0)

        assertEquals("thinking...", msg.getString("reasoning_content"))
    }

    @Test
    fun `buildMessagesArray omits reasoning content when null`() {
        val messages = listOf(ChatMessage(role = "assistant", content = "answer"))
        val arr = service.buildMessagesArray(messages)
        val msg = arr.getJSONObject(0)

        assertNull(msg.opt("reasoning_content"))
    }

    @Test
    fun `buildMessagesArray empty list`() {
        val arr = service.buildMessagesArray(emptyList())
        assertEquals(0, arr.length())
    }

    @Test
    fun `buildMessagesArray multiple images with text`() {
        val messages = listOf(
            ChatMessage(
                role = "user",
                content = "compare these",
                imageUrls = listOf("img1.png", "img2.png", "img3.png")
            )
        )
        val arr = service.buildMessagesArray(messages)
        val content = arr.getJSONObject(0).getJSONArray("content")

        assertEquals(4, content.length()) // 1 text + 3 images
        assertEquals("text", content.getJSONObject(0).getString("type"))
        for (i in 1..3) {
            assertEquals("image_url", content.getJSONObject(i).getString("type"))
        }
    }

    // ── ChatMessage data class ─────────────────────────────────────────

    @Test
    fun `ChatMessage default values`() {
        val msg = ChatMessage(role = "user", content = "hi")

        assertEquals("user", msg.role)
        assertEquals("hi", msg.content)
        assertNull(msg.reasoningContent)
        assertTrue(msg.imageUrls.isEmpty())
    }

    @Test
    fun `ChatMessage equality`() {
        val a = ChatMessage(role = "user", content = "hi")
        val b = ChatMessage(role = "user", content = "hi")
        assertEquals(a, b)
    }

    // ── ApiException ───────────────────────────────────────────────────

    @Test
    fun `ApiException stores code and message`() {
        val ex = ApiException(429, "rate limited", 30)

        assertEquals(429, ex.code)
        assertEquals("rate limited", ex.message)
        assertEquals(30L, ex.retryAfterSeconds)
    }

    @Test
    fun `ApiException null retry after`() {
        val ex = ApiException(500, "server error")

        assertEquals(500, ex.code)
        assertNull(ex.retryAfterSeconds)
    }

    // ── parseRetryAfterHeader ──────────────────────────────────────────

    @Test
    fun `parseRetryAfterHeader valid seconds`() {
        assertEquals(30L, parseRetryAfterHeader("30"))
    }

    @Test
    fun `parseRetryAfterHeader null returns null`() {
        assertNull(parseRetryAfterHeader(null))
    }

    @Test
    fun `parseRetryAfterHeader blank returns null`() {
        assertNull(parseRetryAfterHeader(""))
        assertNull(parseRetryAfterHeader("   "))
    }

    @Test
    fun `parseRetryAfterHeader non-numeric returns null`() {
        assertNull(parseRetryAfterHeader("abc"))
    }

    @Test
    fun `parseRetryAfterHeader trims whitespace`() {
        assertEquals(10L, parseRetryAfterHeader("  10  "))
    }

    @Test
    fun `parseRetryAfterHeader zero`() {
        assertEquals(0L, parseRetryAfterHeader("0"))
    }
}
