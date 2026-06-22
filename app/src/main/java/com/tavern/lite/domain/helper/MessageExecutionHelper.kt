package com.tavern.lite.domain.helper

import android.util.Log
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.domain.usecase.MemoryExtractionUseCase
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import com.tavern.lite.util.cleanCharacterPrefix
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageExecutionHelper @Inject constructor(
    val chatRepository: ChatRepository,
    val chatApiService: ChatApiService,
    val worldBookRepository: WorldBookRepository,
    val memoryRepository: MemoryRepository,
    val personaRepository: PersonaRepository,
    val scriptRepository: ScriptRepository,
    val memoryExtractionUseCase: MemoryExtractionUseCase,
) {
    data class ExecutionResult(
        val assistantMsgId: Long? = null,
        val fullResponse: String = "",
        val processedUserContent: String = "",
        val reasoningContent: String? = null,
    )

    /**
     * 核心执行流程：流式 API → 清理前缀 → 保存 → 正则脚本 → 记忆提取
     * @param previousReasoningContent 上一轮的思维链内容，传回给 API 以维持上下文
     */
    suspend fun executeAndSave(
        chatId: Long,
        characterId: Long,
        characterName: String,
        promptMessages: List<ChatMessage>,
        config: ApiConfig,
        processedUserContent: String,
        previousReasoningContent: String? = null,
    ): ExecutionResult? {
        val responseBuffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        try {
            val messagesWithReasoning = attachReasoningContent(promptMessages, previousReasoningContent)
            chatApiService.streamChatWithMetadata(messagesWithReasoning, config).collect { chunk ->
                responseBuffer.append(chunk.content)
                chunk.reasoningContent?.let { reasoningBuffer.append(it) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val errorMsg = when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.net.SocketException -> "[网络连接失败，请检查网络设置]"
                is java.io.IOException -> "[网络连接异常: ${e.message?.take(50) ?: "未知错误"}]"
                else -> "[生成失败: ${e.message?.take(80) ?: "未知错误"}]"
            }
            chatRepository.sendMessage(chatId, errorMsg, "assistant", characterId)
            return null
        }
        val reasoningContent = reasoningBuffer.takeIf { it.isNotEmpty() }?.toString()

        var fullResponse = responseBuffer.toString()
        if (fullResponse.isBlank()) return null

        val cleanContent = fullResponse.cleanCharacterPrefix(characterName)
        if (cleanContent.isBlank()) return null

        val assistantMsgId = chatRepository.sendMessage(chatId, cleanContent, "assistant", characterId)

        val processedReply = scriptRepository.applyScripts(characterId, cleanContent, 1)
        if (processedReply != cleanContent) {
            chatRepository.updateMessageContent(assistantMsgId, processedReply)
        }

        memoryExtractionUseCase.extractIfNeeded(chatId, characterId, characterName, processedUserContent, config)

        return ExecutionResult(
            assistantMsgId = assistantMsgId,
            fullResponse = cleanContent,
            processedUserContent = processedUserContent,
            reasoningContent = reasoningContent
        )
    }

    /**
     * 为 promptMessages 中最后一条 assistant 消息附加 reasoning_content
     * @param reasoning 上一轮的思维链内容，通过 ExecutionResult 传递，避免全局可变状态
     */
    fun attachReasoningContent(messages: List<ChatMessage>, reasoning: String?): List<ChatMessage> {
        if (reasoning == null) return messages
        for (i in messages.indices.reversed()) {
            if (messages[i].role == "assistant") {
                return messages.toMutableList().also {
                    it[i] = it[i].copy(reasoningContent = reasoning)
                }
            }
        }
        return messages
    }

    suspend fun personasafe(characterId: Long) =
        try { personaRepository.getEffectivePersona(characterId) } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("MessageHelper", "获取 persona 失败", e); null
        }
}
