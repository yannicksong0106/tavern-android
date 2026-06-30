package com.tavern.lite.data.model

import com.tavern.lite.data.store.TtsSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModelSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ==================== TtsSettings ====================

    @Test
    fun `TtsSettings default values round-trip`() {
        val original = TtsSettings()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<TtsSettings>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `TtsSettings custom values round-trip`() {
        val original = TtsSettings(
            enabled = false,
            engine = "openai",
            speed = 1.5f,
            pitch = 0.8f,
            voice = "en-US-Wavenet-D",
            openAiEndpoint = "https://api.openai.com/v1",
            openAiApiKey = "sk-test-key",
            openAiModel = "tts-1-hd",
            openAiVoice = "nova"
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<TtsSettings>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `TtsSettings partial decode uses defaults for missing fields`() {
        val partial = """{"enabled":false,"engine":"openai"}"""
        val decoded = json.decodeFromString<TtsSettings>(partial)
        assertFalse(decoded.enabled)
        assertEquals("openai", decoded.engine)
        assertEquals(1.0f, decoded.speed)
        assertEquals("tts-1", decoded.openAiModel)
    }

    // ==================== BubbleStyleConfig ====================

    @Test
    fun `BubbleStyleConfig default values round-trip`() {
        val original = BubbleStyleConfig()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BubbleStyleConfig>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BubbleStyleConfig custom values round-trip`() {
        val original = BubbleStyleConfig(
            userBubbleColor = 0xFFBB86FC,
            assistantBubbleColor = 0xFF03DAC6,
            cornerRadius = 24,
            fontSize = 18,
            dynamicColor = true
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BubbleStyleConfig>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BubbleStyleConfig partial decode uses defaults`() {
        val partial = """{"cornerRadius":32,"dynamicColor":true}"""
        val decoded = json.decodeFromString<BubbleStyleConfig>(partial)
        assertEquals(32, decoded.cornerRadius)
        assertTrue(decoded.dynamicColor)
        assertEquals(0L, decoded.userBubbleColor)
        assertEquals(15, decoded.fontSize)
    }

    // ==================== QuickReplySet + QuickReply ====================

    @Test
    fun `QuickReplySet default values round-trip`() {
        val original = QuickReplySet(name = "Test Set")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<QuickReplySet>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `QuickReplySet with replies round-trip`() {
        val original = QuickReplySet(
            id = 1,
            name = "Combat Actions",
            scope = QuickReplyScope.Character,
            characterId = 42,
            enabled = true,
            displayOrder = 5,
            replies = listOf(
                QuickReply(label = "Attack", script = "/send Attack!", displayOrder = 1),
                QuickReply(label = "Defend", script = "/send Defend!", enabled = false, displayOrder = 2),
                QuickReply(label = "Auto Trigger", script = "/trigger event", automationId = "chat_open", displayOrder = 0)
            )
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<QuickReplySet>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `QuickReplySet activeReplies filters disabled and sorts by displayOrder`() {
        val set = QuickReplySet(
            name = "Test",
            replies = listOf(
                QuickReply(label = "C", script = "c", displayOrder = 3, enabled = true),
                QuickReply(label = "A", script = "a", displayOrder = 1, enabled = true),
                QuickReply(label = "B", script = "b", displayOrder = 2, enabled = false),
                QuickReply(label = "D", script = "d", displayOrder = 0, enabled = true)
            )
        )
        val active = set.activeReplies()
        assertEquals(3, active.size)
        assertEquals("D", active[0].label)
        assertEquals("A", active[1].label)
        assertEquals("C", active[2].label)
    }

    @Test
    fun `QuickReplySet activeReplies empty when all disabled`() {
        val set = QuickReplySet(
            name = "Test",
            replies = listOf(
                QuickReply(label = "A", script = "a", enabled = false),
                QuickReply(label = "B", script = "b", enabled = false)
            )
        )
        assertTrue(set.activeReplies().isEmpty())
    }

    @Test
    fun `QuickReply isAutomationTrigger is true when automationId is non-blank`() {
        val reply = QuickReply(label = "Test", script = "/trigger", automationId = "chat_open")
        assertTrue(reply.isAutomationTrigger)
    }

    @Test
    fun `QuickReply isAutomationTrigger is false when automationId is null`() {
        val reply = QuickReply(label = "Test", script = "/send")
        assertFalse(reply.isAutomationTrigger)
    }

    @Test
    fun `QuickReply isAutomationTrigger is false when automationId is blank`() {
        val reply = QuickReply(label = "Test", script = "/send", automationId = "  ")
        assertFalse(reply.isAutomationTrigger)
    }

    @Test
    fun `QuickReplyScope enum round-trip`() {
        for (scope in QuickReplyScope.entries) {
            val encoded = json.encodeToString(scope)
            val decoded = json.decodeFromString<QuickReplyScope>(encoded)
            assertEquals(scope, decoded)
        }
    }

    // ==================== WebSearchConfig ====================

    @Test
    fun `WebSearchConfig default values round-trip`() {
        val original = WebSearchConfig()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WebSearchConfig>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `WebSearchConfig custom values round-trip`() {
        val original = WebSearchConfig(
            enabled = true,
            engine = SearchEngine.GOOGLE,
            apiKey = "AIza-test-key",
            maxResults = 10,
            autoSearch = true
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WebSearchConfig>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SearchEngine enum round-trip`() {
        for (engine in SearchEngine.entries) {
            val encoded = json.encodeToString(engine)
            val decoded = json.decodeFromString<SearchEngine>(encoded)
            assertEquals(engine, decoded)
        }
    }
}
