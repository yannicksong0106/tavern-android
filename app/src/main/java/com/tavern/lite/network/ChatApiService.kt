package com.tavern.lite.network

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatApiService @Inject constructor(
    private val client: OkHttpClient
) {
    // 最近一次流式响应中的 reasoning_content（思维链），用于传回给 API
    @Volatile
    var lastReasoningContent: String? = null
        private set

    fun streamChat(
        messages: List<ChatMessage>,
        config: ApiConfig
    ): Flow<String> = flow {
        when (val provider = config.provider) {
            is ApiProvider.OpenAI -> emitAll(streamOpenAI(messages, provider, config))
            is ApiProvider.Claude -> emitAll(streamClaude(messages, provider, config))
            is ApiProvider.Ollama -> emitAll(streamOpenAI(
                messages,
                ApiProvider.OpenAI(baseUrl = "${provider.baseUrl}/v1", model = provider.model),
                config
            ))
            is ApiProvider.Custom -> emitAll(streamOpenAI(messages, provider.let {
                ApiProvider.OpenAI(baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model)
            }, config))
        }
    }.flowOn(Dispatchers.IO)

    private fun streamOpenAI(
        messages: List<ChatMessage>,
        provider: ApiProvider.OpenAI,
        config: ApiConfig
    ): Flow<String> = flow {
        lastReasoningContent = null
        val reasoningBuffer = StringBuilder()

        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", buildMessagesArray(messages))
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature.toDouble())
            put("stream", true)
            if (config.topP < 1f) put("top_p", config.topP.toDouble())
            if (config.frequencyPenalty != 0f) put("frequency_penalty", config.frequencyPenalty.toDouble())
            if (config.presencePenalty != 0f) put("presence_penalty", config.presencePenalty.toDouble())
        }

        val request = Request.Builder()
            .url("${provider.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw ApiException(response.code, errorBody)
        }

        val body2 = response.body ?: return@flow
        val reader = BufferedReader(InputStreamReader(body2.byteStream()))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val chunk = JSONObject(data)
                    val delta = chunk.getJSONArray("choices")
                        .getJSONObject(0)
                        .optJSONObject("delta")

                    // 收集 reasoning_content（思维链），不 emit 给用户
                    val reasoningObj = delta?.opt("reasoning_content")
                    if (reasoningObj != null && reasoningObj != JSONObject.NULL) {
                        reasoningBuffer.append(reasoningObj.toString())
                    }

                    // 修复: optString 在 JSON null 时返回 "null" 字符串
                    // 用 opt() 检查实际值，过滤掉 null 和 JSONObject.NULL
                    val contentObj = delta?.opt("content")
                    if (contentObj != null && contentObj != JSONObject.NULL) {
                        val content = contentObj.toString()
                        if (content.isNotEmpty()) {
                            emit(content)
                        }
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        } finally {
            reader.close()
            response.close()
        }

        if (reasoningBuffer.isNotEmpty()) {
            lastReasoningContent = reasoningBuffer.toString()
        }
    }

    private fun streamClaude(
        messages: List<ChatMessage>,
        provider: ApiProvider.Claude,
        config: ApiConfig
    ): Flow<String> = flow {
        val systemMsg = messages.firstOrNull { it.role == "system" }
        val nonSystemMessages = messages.filter { it.role != "system" }

        val body = JSONObject().apply {
            put("model", provider.model)
            put("max_tokens", config.maxTokens)
            put("stream", true)
            if (systemMsg != null) put("system", systemMsg.content)
            put("messages", JSONArray().apply {
                nonSystemMessages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }

        val request = Request.Builder()
            .url("${provider.baseUrl}/messages")
            .addHeader("x-api-key", provider.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw ApiException(response.code, errorBody)
        }

        val body2 = response.body ?: return@flow
        val reader = BufferedReader(InputStreamReader(body2.byteStream()))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()

                try {
                    val chunk = JSONObject(data)
                    when (chunk.optString("type")) {
                        "content_block_delta" -> {
                            val delta = chunk.optJSONObject("delta")
                            val text = delta?.optString("text")
                            if (!text.isNullOrEmpty()) emit(text)
                        }
                        "message_stop" -> break
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }

    private fun buildMessagesArray(messages: List<ChatMessage>): JSONArray {
        return JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                    // 思维链模型要求传回 reasoning_content
                    if (msg.reasoningContent != null) {
                        put("reasoning_content", msg.reasoningContent)
                    }
                })
            }
        }
    }
}

data class ChatMessage(
    val role: String,
    val content: String,
    val reasoningContent: String? = null
)

class ApiException(val code: Int, override val message: String) : Exception(message)
