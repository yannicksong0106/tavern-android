package com.tavern.lite.domain.usecase

import android.util.Log
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.SummaryRepository
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryUseCase @Inject constructor(
    private val chatApiService: ChatApiService,
    private val summaryRepository: SummaryRepository,
    private val chatRepository: ChatRepository,
) {
    companion object {
        private const val TAG = "SummaryUseCase"
        private const val DEFAULT_MESSAGE_THRESHOLD = 50
        private const val MIN_MESSAGES_TO_SUMMARIZE = 10
        private const val MAX_MESSAGES_FOR_SUMMARY = 40
    }

    /**
     * 检查是否应该生成摘要
     */
    suspend fun shouldGenerateSummary(chatId: Long, messageThreshold: Int = DEFAULT_MESSAGE_THRESHOLD): Boolean {
        val count = chatRepository.getMessageCount(chatId)
        if (count < MIN_MESSAGES_TO_SUMMARIZE) return false
        val existing = summaryRepository.getLatestSummary(chatId)
        val lastSummaryEndId = existing?.messageRangeEnd ?: 0L
        val messagesSince = chatRepository.getRecentMessages(chatId, 2)
            .let { msgs ->
                if (msgs.isEmpty()) return@let count
                val newestId = msgs.first().id // reversed order: newest first
                // Estimate messages since last summary
                val allRecent = chatRepository.getAllMessagesForChat(chatId)
                allRecent.count { it.id > lastSummaryEndId }
            }
        return messagesSince >= messageThreshold
    }

    /**
     * 生成对话摘要
     *
     * @param chatId 聊天 ID
     * @param config API 配置
     * @param characterName 角色名
     * @return 生成的摘要文本，失败返回 null
     */
    suspend fun generateSummary(
        chatId: Long,
        config: ApiConfig,
        characterName: String,
    ): String? {
        val existingSummary = summaryRepository.getLatestSummary(chatId)
        val lastSummaryEndId = existingSummary?.messageRangeEnd ?: 0L

        // 获取上次摘要之后的消息
        val allMessages = chatRepository.getAllMessagesForChat(chatId)
        val newMessages = if (lastSummaryEndId > 0) {
            allMessages.filter { it.id > lastSummaryEndId }
        } else {
            allMessages
        }

        if (newMessages.size < MIN_MESSAGES_TO_SUMMARIZE) {
            Log.d(TAG, "消息数量不足 (${newMessages.size} < $MIN_MESSAGES_TO_SUMMARIZE)，跳过摘要")
            return null
        }

        // 截取最近的消息用于生成摘要
        val messagesForSummary = newMessages.takeLast(MAX_MESSAGES_FOR_SUMMARY)

        val conversationText = messagesForSummary.joinToString("\n") { msg ->
            val role = when (msg.role) {
                "user" -> config.userName.ifBlank { "用户" }
                "assistant" -> characterName
                else -> msg.role
            }
            "$role: ${msg.content.take(500)}"
        }

        val prompt = buildSummaryPrompt(characterName, conversationText, existingSummary?.content)
        val promptMessages = listOf(
            ChatMessage(role = "user", content = prompt)
        )

        val responseBuffer = StringBuilder()
        try {
            chatApiService.streamChat(promptMessages, config).collect { chunk ->
                responseBuffer.append(chunk)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "摘要生成失败: ${e.message}")
            return null
        }

        val summary = responseBuffer.toString().trim()
        if (summary.isBlank()) return null

        // 保存摘要
        val firstMsgId = messagesForSummary.first().id
        val lastMsgId = messagesForSummary.last().id
        val tokenCount = estimateTokenCount(summary)

        summaryRepository.saveSummary(
            chatId = chatId,
            content = summary,
            messageRangeStart = firstMsgId,
            messageRangeEnd = lastMsgId,
            tokenCount = tokenCount
        )

        Log.i(TAG, "摘要生成成功: ${messagesForSummary.size} 条消息 → ${tokenCount} tokens")
        return summary
    }

    /**
     * 获取最新的摘要文本
     */
    suspend fun getLatestSummaryText(chatId: Long): String? =
        summaryRepository.getLatestSummary(chatId)?.content

    /**
     * 手动触发摘要
     */
    suspend fun generateManualSummary(
        chatId: Long,
        config: ApiConfig,
        characterName: String,
    ): String? = generateSummary(chatId, config, characterName)

    /**
     * 删除指定聊天的所有摘要
     */
    suspend fun deleteAllSummaries(chatId: Long) =
        summaryRepository.deleteAllForChat(chatId)

    private fun buildSummaryPrompt(
        characterName: String,
        conversation: String,
        existingSummary: String?,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("你是一个对话摘要助手。请将以下对话内容压缩为简洁的摘要。")
        sb.appendLine()
        sb.appendLine("要求：")
        sb.appendLine("1. 保留重要的事实、约定、承诺、决定")
        sb.appendLine("2. 保留角色 ($characterName) 的关键性格特征和行为模式")
        sb.appendLine("3. 保留用户的重要信息（姓名、偏好、情感状态）")
        sb.appendLine("4. 保留剧情发展和关键转折点")
        sb.appendLine("5. 忽略寒暄、重复、无意义的内容")
        sb.appendLine("6. 使用简洁的叙述体，不要对话格式")
        sb.appendLine("7. 长度控制在 200-500 字以内")
        sb.appendLine()

        if (!existingSummary.isNullOrBlank()) {
            sb.appendLine("已有摘要（请在此基础上追加新内容）：")
            sb.appendLine(existingSummary)
            sb.appendLine()
            sb.appendLine("新对话内容：")
        } else {
            sb.appendLine("对话内容：")
        }
        sb.appendLine(conversation)
        sb.appendLine()
        sb.appendLine("请输出完整的摘要（不要加任何前缀说明）：")

        return sb.toString()
    }

    private fun estimateTokenCount(text: String): Int {
        // 粗略估算：中文约 1.5 字/token，英文约 4 字符/token
        val chineseChars = text.count { it.code > 0x4E00 }
        val otherChars = text.length - chineseChars
        return (chineseChars / 1.5 + otherChars / 4).toInt()
    }
}
