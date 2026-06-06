package com.tavern.lite.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigTest {

    // ==================== ApiConfig defaults ====================

    @Test
    fun `ApiConfig has correct default temperature`() {
        val config = ApiConfig()
        assertEquals(0.8f, config.temperature)
    }

    @Test
    fun `ApiConfig has correct default maxTokens`() {
        val config = ApiConfig()
        assertEquals(2048, config.maxTokens)
    }

    @Test
    fun `ApiConfig has correct default contextLength`() {
        val config = ApiConfig()
        assertEquals(20, config.contextLength)
    }

    @Test
    fun `ApiConfig has correct default penalties`() {
        val config = ApiConfig()
        assertEquals(0f, config.frequencyPenalty)
        assertEquals(0f, config.presencePenalty)
        assertEquals(1f, config.topP)
    }

    @Test
    fun `ApiConfig has correct default userName`() {
        val config = ApiConfig()
        assertEquals("User", config.userName)
    }

    @Test
    fun `ApiConfig default provider is OpenAI`() {
        val config = ApiConfig()
        assert(config.provider is ApiProvider.OpenAI)
    }

    // ==================== ApiProvider displayName ====================

    @Test
    fun `OpenAI displayName`() {
        assertEquals("OpenAI", ApiProvider.OpenAI().displayName)
    }

    @Test
    fun `Claude displayName`() {
        assertEquals("Claude", ApiProvider.Claude().displayName)
    }

    @Test
    fun `Ollama displayName`() {
        assertEquals("Ollama", ApiProvider.Ollama().displayName)
    }

    @Test
    fun `KoboldAI displayName`() {
        assertEquals("KoboldAI", ApiProvider.KoboldAI().displayName)
    }

    @Test
    fun `OpenRouter displayName`() {
        assertEquals("OpenRouter", ApiProvider.OpenRouter().displayName)
    }

    @Test
    fun `Gemini displayName`() {
        assertEquals("Gemini", ApiProvider.Gemini().displayName)
    }

    @Test
    fun `Custom displayName defaults to 自定义`() {
        assertEquals("自定义", ApiProvider.Custom().displayName)
    }

    @Test
    fun `Custom displayName can be overridden`() {
        val provider = ApiProvider.Custom(displayName = "My API")
        assertEquals("My API", provider.displayName)
    }

    // ==================== ApiProvider defaults ====================

    @Test
    fun `OpenAI default baseUrl`() {
        assertEquals("https://api.openai.com/v1", ApiProvider.OpenAI().baseUrl)
    }

    @Test
    fun `OpenAI default model`() {
        assertEquals("gpt-4o", ApiProvider.OpenAI().model)
    }

    @Test
    fun `Claude default model`() {
        assertEquals("claude-sonnet-4-20250514", ApiProvider.Claude().model)
    }

    @Test
    fun `Ollama default baseUrl`() {
        assertEquals("http://localhost:11434", ApiProvider.Ollama().baseUrl)
    }

    @Test
    fun `Gemini default model`() {
        assertEquals("gemini-2.0-flash", ApiProvider.Gemini().model)
    }
}
