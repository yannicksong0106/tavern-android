package com.tavern.lite.network

import android.util.Log
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.domain.port.MemoryExtractorPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
class MemoryExtractorService @Inject constructor(
    private val client: OkHttpClient
) : MemoryExtractorPort {

    companion object {
        private const val TAG = "MemoryExtractor"
        private const val EXTRACTION_INTERVAL = 10
        private const val MAX_MESSAGES_FOR_EXTRACTION = 30

        // Regex patterns for quick extraction
        private val NAME_PATTERNS = listOf(
            Regex("我叫(.{1,20})[，。,.]"),
            Regex("我的名字是(.{1,20})[，。,.]"),
            Regex("叫我(.{1,10})就好"),
            Regex("我是(.{1,20})[，。,.]"),
        )
        private val AGE_PATTERNS = listOf(
            Regex("我(今年)?(\\d{1,3})岁"),
            Regex("我(今年)?(\\d{1,3})了"),
        )
        private val LIKE_PATTERNS = listOf(
            Regex("我(?:很|非常|特别|最)?喜欢([^，。！？,!?]{1,30})"),
        )
        private val DISLIKE_PATTERNS = listOf(
            Regex("我(?:很|非常|特别|最)?讨厌([^，。！？,!?]{1,30})"),
            Regex("我不喜欢([^，。！？,!?]{1,30})"),
        )
        private val EVENT_PATTERNS = listOf(
            Regex("(?:我(?:会|一定|必须)|我(?:答应|承诺))([^。！!]{1,50})"),
            Regex("(?:永远|一直|始终)([^。！!]{1,30})"),
        )
    }

    /**
     * Determine if extraction should run based on message count.
     */
    override fun shouldExtract(totalMessages: Int): Boolean {
        return totalMessages > 0 && totalMessages % EXTRACTION_INTERVAL == 0
    }

    /**
     * Quick regex-based extraction from a single user message.
     * Returns facts that can be stored immediately without LLM call.
     */
    override fun extractQuickFacts(
        characterId: Long,
        userMessage: String,
        chatId: Long?,
        messageId: Long?
    ): List<MemoryAtomEntity> {
        val facts = mutableListOf<MemoryAtomEntity>()
        val now = System.currentTimeMillis()

        for (pattern in NAME_PATTERNS) {
            pattern.find(userMessage)?.groupValues?.getOrNull(1)?.let { name ->
                facts.add(
                    MemoryAtomEntity(
                        characterId = characterId,
                        content = "用户的名字是${name.trim()}",
                        category = "fact",
                        importance = 8,
                        source = "regex",
                        sourceChatId = chatId,
                        sourceMessageId = messageId,
                        createdAt = now,
                        lastAccessed = now
                    )
                )
            }
        }

        for (pattern in AGE_PATTERNS) {
            pattern.find(userMessage)?.groupValues?.getOrNull(2)?.let { age ->
                facts.add(
                    MemoryAtomEntity(
                        characterId = characterId,
                        content = "用户${age}岁",
                        category = "fact",
                        importance = 7,
                        source = "regex",
                        sourceChatId = chatId,
                        sourceMessageId = messageId,
                        createdAt = now,
                        lastAccessed = now
                    )
                )
            }
        }

        for (pattern in LIKE_PATTERNS) {
            pattern.find(userMessage)?.groupValues?.getOrNull(1)?.let { thing ->
                facts.add(
                    MemoryAtomEntity(
                        characterId = characterId,
                        content = "用户喜欢${thing.trim()}",
                        category = "preference",
                        importance = 6,
                        source = "regex",
                        sourceChatId = chatId,
                        sourceMessageId = messageId,
                        createdAt = now,
                        lastAccessed = now
                    )
                )
            }
        }
        for (pattern in DISLIKE_PATTERNS) {
            pattern.find(userMessage)?.groupValues?.getOrNull(1)?.let { thing ->
                facts.add(
                    MemoryAtomEntity(
                        characterId = characterId,
                        content = "用户讨厌${thing.trim()}",
                        category = "preference",
                        importance = 6,
                        source = "regex",
                        sourceChatId = chatId,
                        sourceMessageId = messageId,
                        createdAt = now,
                        lastAccessed = now
                    )
                )
            }
        }

        for (pattern in EVENT_PATTERNS) {
            pattern.find(userMessage)?.value?.let { commitment ->
                facts.add(
                    MemoryAtomEntity(
                        characterId = characterId,
                        content = "承诺: ${commitment.trim()}",
                        category = "event",
                        importance = 8,
                        source = "regex",
                        sourceChatId = chatId,
                        sourceMessageId = messageId,
                        createdAt = now,
                        lastAccessed = now
                    )
                )
            }
        }

        return facts
    }

    /**
     * LLM-based batch extraction from recent conversation.
     * Extracts structured facts in all categories.
     */
    override suspend fun extractWithLLM(
        characterId: Long,
        messages: List<MessageEntity>,
        characterName: String,
        config: ApiConfig,
        chatId: Long?
    ): List<MemoryAtomEntity> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext emptyList()

        val recentMessages = messages.takeLast(MAX_MESSAGES_FOR_EXTRACTION)
        val conversationText = recentMessages.joinToString("\n") { msg ->
            val role = if (msg.role == "user") "用户" else characterName
            "$role: ${msg.content.take(300)}"
        }

        val prompt = buildExtractionPrompt(characterName, conversationText)
        val responseText = callLLM(prompt, config) ?: return@withContext emptyList()

        parseExtractedFacts(characterId, responseText, chatId)
    }

    private fun buildExtractionPrompt(characterName: String, conversation: String): String {
        return """你是一个记忆提取助手。分析以下对话，提取值得长期记住的重要事实。

对话内容:
$conversation

请提取以下类别的事实（JSON 数组格式）:
- fact: 客观事实（用户的姓名、年龄、职业、所在地等客观信息）
- emotion: 情感状态（用户的情绪、感受、对事物的态度）
- preference: 偏好（用户喜欢/不喜欢的事物、审美偏好、习惯偏好）
- event: 事件与约定（发生的事情、承诺、约定、决定）
- habit: 习惯（行为模式、日常习惯、固定做法）
- character_consistency: ${characterName}的性格特征、外貌、背景故事、说过的重要承诺（必须保持一致的信息）
- temporary: 临时信息（当前上下文中的短期信息，会在几轮对话后过期）

输出格式（严格 JSON，不要其他文字）:
[{"content":"事实描述","category":"类别","importance":1-10,"expires_hours":0}]

规则:
1. 每个事实简洁明了，不超过50字
2. character_consistency 类型最重要（importance 8-10），因为角色人设不能崩
3. temporary 类型的 expires_hours 设置为 2-6 小时
4. 其他类型的 expires_hours 设为 0（永不过期）
5. 重复或微不足道的信息不要提取
6. 如果对话中没有值得记忆的信息，返回空数组 []
7. importance: 1=可忽略, 5=一般, 10=关键"""
    }

    private fun callLLM(prompt: String, config: ApiConfig): String? {
        return try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            when (val provider = config.provider) {
                is ApiProvider.OpenAI -> callOpenAINonStreaming(messages, provider, config)
                is ApiProvider.Claude -> callClaudeNonStreaming(messages, provider, config)
                is ApiProvider.Ollama -> callOpenAINonStreaming(
                    messages,
                    ApiProvider.OpenAI(baseUrl = "${provider.baseUrl}/v1", model = provider.model),
                    config
                )
                is ApiProvider.KoboldAI -> callOpenAINonStreaming(
                    messages,
                    ApiProvider.OpenAI(baseUrl = "${provider.baseUrl}/v1", apiKey = provider.apiKey, model = provider.model),
                    config
                )
                is ApiProvider.Gemini -> callGeminiNonStreaming(messages, provider, config)
                is ApiProvider.OpenRouter -> callOpenAINonStreaming(
                    messages,
                    ApiProvider.OpenAI(baseUrl = "https://openrouter.ai/api/v1", apiKey = provider.apiKey, model = provider.model),
                    config
                )
                is ApiProvider.Custom -> callOpenAINonStreaming(
                    messages,
                    ApiProvider.OpenAI(baseUrl = provider.baseUrl, apiKey = provider.apiKey, model = provider.model),
                    config
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "LLM call failed: ${e.message}")
            null
        }
    }

    private fun callOpenAINonStreaming(
        messages: JSONArray,
        provider: ApiProvider.OpenAI,
        config: ApiConfig
    ): String? {
        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", messages)
            put("max_tokens", 1024)
            put("temperature", 0.3)
            put("stream", false)
        }

        val request = Request.Builder()
            .url("${provider.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        return try {
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null
            JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "callOpenAI 解析失败", e)
            null
        } finally {
            response.close()
        }
    }

    private fun callClaudeNonStreaming(
        messages: JSONArray,
        provider: ApiProvider.Claude,
        config: ApiConfig
    ): String? {
        val body = JSONObject().apply {
            put("model", provider.model)
            put("max_tokens", 1024)
            put("stream", false)
            put("system", "你是一个记忆提取助手。请严格按照要求的 JSON 格式输出。")
            put("messages", messages)
        }

        val request = Request.Builder()
            .url("${provider.baseUrl}/messages")
            .addHeader("x-api-key", provider.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        return try {
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)
            val content = json.getJSONArray("content")
            val textBlock = (0 until content.length())
                .map { content.getJSONObject(it) }
                .firstOrNull { it.getString("type") == "text" }
            textBlock?.getString("text")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "callClaude 解析失败", e)
            null
        } finally {
            response.close()
        }
    }

    private fun callGeminiNonStreaming(
        messages: JSONArray,
        provider: ApiProvider.Gemini,
        config: ApiConfig
    ): String? {
        val contents = JSONArray()
        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            val role = when (msg.getString("role")) {
                "user" -> "user"
                "assistant" -> "model"
                else -> "user"
            }
            contents.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", msg.getString("content")) })
                })
            })
        }

        val body = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1024)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${provider.model}:generateContent?key=${provider.apiKey}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        return try {
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)
            val candidates = json.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            parts.getJSONObject(0).getString("text")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "callGemini 解析失败", e)
            null
        } finally {
            response.close()
        }
    }

    private fun parseExtractedFacts(
        characterId: Long,
        responseText: String,
        chatId: Long?
    ): List<MemoryAtomEntity> {
        val now = System.currentTimeMillis()
        val facts = mutableListOf<MemoryAtomEntity>()

        try {
            // Try to find JSON array in response
            // Look for the last complete JSON array to avoid matching brackets in conversation text
            val jsonEnd = responseText.lastIndexOf(']')
            if (jsonEnd < 0) return emptyList()

            // Find the matching opening bracket by counting from the end
            var depth = 0
            var jsonStart = -1
            for (i in jsonEnd downTo 0) {
                when (responseText[i]) {
                    ']' -> depth++
                    '[' -> {
                        depth--
                        if (depth == 0) {
                            jsonStart = i
                            break
                        }
                    }
                }
            }
            if (jsonStart < 0) return emptyList()

            val jsonStr = responseText.substring(jsonStart, jsonEnd + 1)
            val arr = JSONArray(jsonStr)

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val content = obj.optString("content", "").trim()
                val category = obj.optString("category", "fact").trim()
                val importance = obj.optInt("importance", 5).coerceIn(1, 10)

                val expiresHours = obj.optInt("expires_hours", 0)
                val expiresAt = if (expiresHours > 0) now + expiresHours * 3600_000L else null

                if (content.isNotBlank() && isValidCategory(category)) {
                    facts.add(
                        MemoryAtomEntity(
                            characterId = characterId,
                            content = content,
                            category = category,
                            importance = importance,
                            source = "llm",
                            sourceChatId = chatId,
                            createdAt = now,
                            lastAccessed = now,
                            expiresAt = expiresAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Failed to parse extracted memory facts: ${e.message}, response length: ${responseText.length}")
        }

        return facts
    }

    private fun isValidCategory(category: String): Boolean {
        return category in listOf(
            "fact", "emotion", "preference", "event", "habit",
            "character_consistency", "temporary"
        )
    }
}
