package com.tavern.lite.network

import android.util.Log
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.model.ChatStreamChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
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
    fun streamChat(
        messages: List<ChatMessage>,
        config: ApiConfig
    ): Flow<String> = flow {
        streamChatWithMetadata(messages, config).collect { chunk ->
            if (chunk.content.isNotEmpty()) emit(chunk.content)
        }
    }

    fun streamChatWithMetadata(
        messages: List<ChatMessage>,
        config: ApiConfig
    ): Flow<ChatStreamChunk> = flow {
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
            is ApiProvider.OpenRouter -> emitAll(streamOpenAI(
                messages,
                ApiProvider.OpenAI(baseUrl = "https://openrouter.ai/api/v1", apiKey = provider.apiKey, model = provider.model),
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
    ): Flow<ChatStreamChunk> = flow {
        val effectiveClient = if (config.readTimeoutSeconds != 300L) {
            client.newBuilder().readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS).build()
        } else client

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
            val resp = effectiveClient.newCall(request).execute()
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                val retryAfter = parseRetryAfterHeader(resp.header("Retry-After"))
                resp.close()
                throw ApiException(resp.code, errorBody, retryAfter)
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
            var completedNormally = false
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") { completedNormally = true; break }

                try {
                    parseOpenAIStreamChunk(data).forEach { emit(it) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("ChatApiService", "SSE chunk parse error", e)
                }
            }
            if (!completedNormally) {
                throw java.io.IOException("SSE stream ended without [DONE] marker")
            }
        } finally {
            reader.close()
            response.close()
        }

    }

    private fun streamClaude(
        messages: List<ChatMessage>,
        provider: ApiProvider.Claude,
        config: ApiConfig
    ): Flow<ChatStreamChunk> = flow {
        val systemMsg = messages.firstOrNull { it.role == "system" }
        val nonSystemMessages = messages.filter { it.role != "system" }
        val effectiveClient = if (config.readTimeoutSeconds != 300L) {
            client.newBuilder().readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS).build()
        } else client

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
            val resp = effectiveClient.newCall(request).execute()
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                val retryAfter = parseRetryAfterHeader(resp.header("Retry-After"))
                resp.close()
                throw ApiException(resp.code, errorBody, retryAfter)
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
            var completedNormally = false
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
                            if (!text.isNullOrEmpty()) emit(ChatStreamChunk(content = text))
                        }
                        "message_stop" -> { completedNormally = true; break }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("ChatApiService", "SSE chunk parse error", e)
                }
            }
            if (!completedNormally) {
                throw java.io.IOException("SSE stream ended without message_stop event")
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
    ): Flow<ChatStreamChunk> = flow {
        // Gemini API: 分离 system 消息和对话消息
        val systemMsg = messages.firstOrNull { it.role == "system" }
        val nonSystemMessages = messages.filter { it.role != "system" }
        val effectiveClient = if (config.readTimeoutSeconds != 300L) {
            client.newBuilder().readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS).build()
        } else client

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

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${provider.model}:streamGenerateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", provider.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = retryWithBackoff {
            val resp = effectiveClient.newCall(request).execute()
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                val retryAfter = parseRetryAfterHeader(resp.header("Retry-After"))
                resp.close()
                throw ApiException(resp.code, errorBody, retryAfter)
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
                                emit(ChatStreamChunk(content = text))
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("ChatApiService", "SSE chunk parse error", e)
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }

    internal fun buildMessagesArray(messages: List<ChatMessage>): JSONArray {
        return JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    if (msg.imageUrls.isNotEmpty()) {
                        // multimodal: content 为数组，包含 text + image_url 部分
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", msg.content)
                            })
                            msg.imageUrls.forEach { url ->
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", url)
                                    })
                                })
                            }
                        })
                    } else {
                        put("content", msg.content)
                    }
                    // 思维链模型要求传回 reasoning_content
                    if (msg.reasoningContent != null) {
                        put("reasoning_content", msg.reasoningContent)
                    }
                })
            }
        }
    }
}

internal fun parseOpenAIStreamChunk(data: String): List<ChatStreamChunk> {
    val chunk = JSONObject(data)
    val choices = chunk.getJSONArray("choices")
    if (choices.length() == 0) return emptyList()

    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return emptyList()
    val chunks = mutableListOf<ChatStreamChunk>()

    val reasoningObj = delta.opt("reasoning_content")
    if (reasoningObj != null && reasoningObj != JSONObject.NULL) {
        chunks.add(ChatStreamChunk(reasoningContent = reasoningObj.toString()))
    }

    val contentObj = delta.opt("content")
    if (contentObj != null && contentObj != JSONObject.NULL) {
        val content = contentObj.toString()
        if (content.isNotEmpty()) {
            chunks.add(ChatStreamChunk(content = content))
        }
    }

    return chunks
}

/** Domain 层 ChatMessage 的别名，保持网络层兼容 */
typealias NetworkChatMessage = ChatMessage
/** Domain 层 ChatStreamChunk 的别名，保持网络层兼容 */
typealias NetworkChatStreamChunk = ChatStreamChunk

class ApiException(
    val code: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null
) : Exception(message)

/**
 * 带指数退避的重试，仅重试网络错误和 5xx 服务端错误，不重试 4xx 客户端错误。
 * 429 响应优先使用 Retry-After 头部指定的等待时间。
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
                    lastException = e
                    // 429 限流：优先使用服务器返回的 Retry-After 时间
                    if (e.code == 429 && e.retryAfterSeconds != null) {
                        delayMs = (e.retryAfterSeconds * 1000).coerceIn(delayMs, 60_000L)
                    }
                }
                in 400..499 -> {
                    throw e
                }
                else -> {
                    lastException = e
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            lastException = e
        }
        if (attempt < maxRetries - 1) {
            delay(delayMs)
            delayMs *= 2
        }
    }
    throw lastException ?: Exception("Unknown retry error")
}

/**
 * 解析 Retry-After 头部，支持秒数格式（如 "30"）。
 * 返回等待秒数，解析失败返回 null。
 */
internal fun parseRetryAfterHeader(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return value.trim().toLongOrNull()
}
     