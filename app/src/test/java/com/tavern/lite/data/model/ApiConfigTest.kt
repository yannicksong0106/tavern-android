package com.tavern.lite.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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

    // ==================== Serialization round-trip ====================

    @Test
    fun `ApiConfig with OpenAI provider round-trip`() {
        val original = ApiConfig(provider = ApiProvider.OpenAI(apiKey = "sk-test", model = "gpt-4o-mini"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.provider is ApiProvider.OpenAI)
    }

    @Test
    fun `ApiConfig with Claude provider round-trip`() {
        val original = ApiConfig(provider = ApiProvider.Claude(apiKey = "sk-test", model = "claude-3-haiku"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.provider is ApiProvider.Claude)
    }

    @Test
    fun `ApiConfig with Ollama provider round-trip`() {
        val original = ApiConfig(provider = ApiProvider.Ollama(baseUrl = "http://192.168.1.100:11434", model = "mistral"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.provider is ApiProvider.Ollama)
    }

    @Test
    fun `ApiConfig with KoboldAI provider round-trip`() {
        val original = ApiConfig(provider = ApiProvider.KoboldAI(apiKey = "key", model = "koboldcpp"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.provider is ApiProvider.KoboldAI)
    }

    @Test
    fun `ApiConfig with OpenRouter provider round-trip`() {
        val original = ApiConfig(provider = ApiProvider.OpenRouter(apiKey = "sk-or-test", model = "anthropic/claude-3"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.provider is ApiProvider.OpenRouter)
    }

    @Test
    fun `ApiConfig with Gemini provider round-trip`() {
        val original = ApiConfig(provider = ApiProvider.Gemini(apiKey = "AIza-test", model = "gemini-1.5-pro"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.provider is ApiProvider.Gemini)
    }

    @Test
    fun `ApiConfig with Custom provider round-trip`() {
        val original = ApiConfig(provider = ApiProvider.Custom(baseUrl = "http://localhost:8080", apiKey = "key", model = "my-model", displayName = "My API"))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.provider is ApiProvider.Custom)
    }

    @Test
    fun `ApiConfig with all params round-trip`() {
        val original = ApiConfig(
            provider = ApiProvider.OpenAI(),
            temperature = 1.2f, maxTokens = 4096, contextLength = 50,
            frequencyPenalty = 0.5f, presencePenalty = 0.3f, topP = 0.9f,
            userName = "Tester", readTimeoutSeconds = 600L
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ApiConfig>(encoded)
        assertEquals(original, decoded)
    }

    // ==================== StScript permissions & commands ====================

    @Test
    fun `StScriptPermissions defaults round-trip`() {
        val original = StScriptPermissions()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<StScriptPermissions>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.canReadVariables)
        assertTrue(decoded.canWriteVariables)
    }

    @Test
    fun `StScriptPermissions all flags round-trip`() {
        val original = StScriptPermissions(allowAutoRun = true, canSendMessages = true, canTriggerGeneration = true, canReadVariables = false, canWriteVariables = false)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<StScriptPermissions>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StScriptCommand isSafeForAutoRun for safe commands`() {
        for (type in StScriptCommandType.autoRunSafeCommands) {
            val cmd = StScriptCommand(type = type)
            assertTrue("$type should be safe", cmd.isSafeForAutoRun)
        }
    }

    @Test
    fun `StScriptCommand isSafeForAutoRun false for unsafe commands`() {
        val unsafe = StScriptCommandType.entries - StScriptCommandType.autoRunSafeCommands
        for (type in unsafe) {
            val cmd = StScriptCommand(type = type)
            assertTrue("$type should be unsafe", !cmd.isSafeForAutoRun)
        }
    }

    @Test
    fun `StScriptCommand round-trip with all fields`() {
        val original = StScriptCommand(type = StScriptCommandType.SetVar, argument = "value", variableName = "var1", displayText = "Set var1")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<StScriptCommand>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StScriptProgram isAutoExecutable true when all safe and allowAutoRun`() {
        val program = StScriptProgram(
            source = "/echo hello",
            commands = listOf(StScriptCommand(type = StScriptCommandType.Echo, argument = "hello")),
            permissions = StScriptPermissions(allowAutoRun = true)
        )
        assertTrue(program.isAutoExecutable)
    }

    @Test
    fun `StScriptProgram isAutoExecutable false when allowAutoRun is false`() {
        val program = StScriptProgram(
            source = "/echo hello",
            commands = listOf(StScriptCommand(type = StScriptCommandType.Echo)),
            permissions = StScriptPermissions(allowAutoRun = false)
        )
        assertTrue(!program.isAutoExecutable)
    }

    @Test
    fun `StScriptProgram isAutoExecutable false when contains unsafe command`() {
        val program = StScriptProgram(
            source = "/send hello",
            commands = listOf(StScriptCommand(type = StScriptCommandType.Send, argument = "hello")),
            permissions = StScriptPermissions(allowAutoRun = true)
        )
        assertTrue(!program.isAutoExecutable)
    }

    @Test
    fun `StScriptProgram round-trip`() {
        val original = StScriptProgram(
            source = "/setvar x 1\n/getvar x",
            commands = listOf(
                StScriptCommand(type = StScriptCommandType.SetVar, argument = "1", variableName = "x"),
                StScriptCommand(type = StScriptCommandType.GetVar, variableName = "x")
            ),
            permissions = StScriptPermissions(allowAutoRun = true, canReadVariables = true)
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<StScriptProgram>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StScriptCommandType enum round-trip`() {
        for (type in StScriptCommandType.entries) {
            val encoded = json.encodeToString(type)
            val decoded = json.decodeFromString<StScriptCommandType>(encoded)
            assertEquals(type, decoded)
        }
    }
}
