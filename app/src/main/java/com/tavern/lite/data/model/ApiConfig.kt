package com.tavern.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfig(
    val provider: ApiProvider = ApiProvider.OpenAI(),
    val temperature: Float = 0.8f,
    val maxTokens: Int = 2048,
    val contextLength: Int = 20, // 最近 N 条消息作为上下文
    val frequencyPenalty: Float = 0f,
    val presencePenalty: Float = 0f,
    val topP: Float = 1f,
    val userName: String = "User",
)

@Serializable
sealed class ApiProvider {
    abstract val displayName: String

    @Serializable
    data class OpenAI(
        val baseUrl: String = "https://api.openai.com/v1",
        val apiKey: String = "",
        val model: String = "gpt-4o"
    ) : ApiProvider() {
        override val displayName = "OpenAI"
    }

    @Serializable
    data class Claude(
        val baseUrl: String = "https://api.anthropic.com/v1",
        val apiKey: String = "",
        val model: String = "claude-sonnet-4-20250514"
    ) : ApiProvider() {
        override val displayName = "Claude"
    }

    @Serializable
    data class Ollama(
        val baseUrl: String = "http://localhost:11434",
        val model: String = "llama3"
    ) : ApiProvider() {
        override val displayName = "Ollama"
    }

    @Serializable
    data class KoboldAI(
        val baseUrl: String = "http://localhost:5001",
        val apiKey: String = "",
        val model: String = "kobold"
    ) : ApiProvider() {
        override val displayName = "KoboldAI"
    }

    @Serializable
    data class Gemini(
        val apiKey: String = "",
        val model: String = "gemini-2.0-flash"
    ) : ApiProvider() {
        override val displayName = "Gemini"
    }

    @Serializable
    data class Custom(
        val baseUrl: String = "",
        val apiKey: String = "",
        val model: String = "",
        override val displayName: String = "自定义"
    ) : ApiProvider()
}
