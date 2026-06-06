package com.tavern.lite.ui.screens.chat

import com.tavern.lite.network.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptInspectorTest {

    @Test
    fun `format labels messages by role and index`() {
        val result = PromptInspectorFormatter.format(
            listOf(
                ChatMessage(role = "system", content = "rules"),
                ChatMessage(role = "user", content = "hello")
            )
        )

        assertTrue(result.contains("### 1. SYSTEM\nrules"))
        assertTrue(result.contains("### 2. USER\nhello"))
    }

    @Test
    fun `format includes image count`() {
        val result = PromptInspectorFormatter.format(
            listOf(ChatMessage(role = "user", content = "see this", imageUrls = listOf("data:image/png;base64,abc")))
        )

        assertTrue(result.contains("[images: 1]"))
    }

    @Test
    fun `empty prompt formats as empty string`() {
        assertEquals("", PromptInspectorFormatter.format(emptyList()))
    }
}
