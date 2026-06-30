package com.tavern.lite.domain.usecase

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.port.ChatApiPort
import javax.inject.Inject

class TestConnectionUseCase @Inject constructor(
    private val chatApiService: ChatApiPort
) {
    suspend operator fun invoke(config: ApiConfig): String {
        val testConfig = config.copy(maxTokens = TEST_MAX_TOKENS)
        val messages = listOf(
            ChatMessage(role = "user", content = TEST_PROMPT)
        )
        val result = StringBuilder()
        chatApiService.streamChat(messages, testConfig).collect { chunk ->
            result.append(chunk)
        }
        return result.toString().take(MAX_REPLY_PREVIEW_LENGTH)
    }

    private companion object {
        const val TEST_PROMPT = "Say 'hello' in one word."
        const val TEST_MAX_TOKENS = 50
        const val MAX_REPLY_PREVIEW_LENGTH = 100
    }
}
