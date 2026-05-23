package com.tavern.lite.network

import android.util.Log
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            is ApiProvider.KoboldAI -> emitAll(streamOpenAI(
                messages,
                ApiProvider.OpenAI(baseUrl = "${provider.baseUrl}/v1", apiKey = provider.apiKey, model = provider.model),
                config
            ))
            is ApiProvider.Gemini -> emitAll(streamGemini(messages, provider, config))
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

        val response = retryWithBackoff {
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                resp.close()
                throw ApiException(resp.code, errorBody)
            }
            resp
        }

        val body2 = response.body
        if (body2 == null) {
            response.close()
            return@flow
        }
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

                    val contentObj = delta?.opt("content")
                    if (contentObj != null && contentObj != JSONObject.NULL) {
                        val content = contentObj.toString()
                        if (content.isNotEmpty()) {
                            emit(content)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ChatApiService", "SSE chunk parse error", e)
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

        val response = retryWithBackoff {
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                resp.close()
                throw ApiException(resp.code, errorBody)
            }
            resp
        }

        val body2 = response.body
        if (body2 == null) {
            response.close()
            return@flow
        }
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
                } catch (e: Exception) {
                    Log.w("ChatApiService", "SSE chunk parse error", e)
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }

    private fun streamGemini(
        messages: List<ChatMessage>,
        provider: ApiProvider.Gemini,
        config: ApiConfig
    ): Flow<String> = flow {
        // Gemini API: 分离 system 消息和对话消息
        val systemMsg = messages.firstOrNull { it.role == "system" }
        val nonSystemMessages = messages.filter { it.role != "system" }

        val contents = JSONArray()
        nonSystemMessages.forEach { msg ->
            val role = when (msg.role) {
                "user" -> "user"
                "assistant" -> "model"
                else -> "user"
            }
            contents.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", msg.content) })
                })
            })
        }

        val body = JSONObject().apply {
            put("contents", contents)
            if (systemMsg != null) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemMsg.content) })
                    })
                })
            }
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", config.maxTokens)
                put("temperature", config.temperature.toDouble())
                if (config.topP < 1f) put("topP", config.topP.toDouble())
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${provider.model}:streamGenerateContent?key=${provider.apiKey}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = retryWithBackoff {
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                resp.close()
                throw ApiException(resp.code, errorBody)
            }
            resp
        }

        val body2 = response.body
        if (body2 == null) {
            response.close()
            return@flow
        }
        val reader = BufferedReader(InputStreamReader(body2.byteStream()))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()

                try {
                    val chunk = JSONObject(data)
                    val candidates = chunk.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (!text.isNullOrEmpty()) {
                                emit(text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ChatApiService", "SSE chunk parse error", e)
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

/**
 * 带指数退避的重试，仅重试网络错误和 5xx 服务端错误，不重试 4xx 客户端错误。
 */
private suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    var delayMs = initialDelayMs
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: ApiException) {
            when (e.code) {
                429, 502, 503, 504 -> {
                    // 可重试的状态码：限流、网关错误、服务不可用
                    lastException = e
                }
                in 400..499 -> {
                    // 其他 4xx 客户端错误不重试
                    throw e
                }
                else -> {
                    // 5xx 服务端错误可重试
                    lastException = e
                }
            }
        } catch (e: Exception) {
            lastException = e
        }
        if (attempt < maxRetries - 1) {
            delay(delayMs)
            delayMs *= 2
        }
    }
    throw lastException ?: Exception("Unknown retry error")
}
