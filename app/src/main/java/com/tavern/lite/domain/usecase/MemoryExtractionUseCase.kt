package com.tavern.lite.domain.usecase

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryConsolidator
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.domain.port.MemoryExtractorPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractionUseCase @Inject constructor(
    private val memoryExtractorService: MemoryExtractorPort,
    private val memoryConsolidator: MemoryConsolidator,
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
) {
    /**
     * 通用记忆提取：正则快速提取 + LLM 批量提取（每 N 轮一次）
     *
     * @param chatId 聊天 ID
     * @param characterId 角色 ID
     * @param characterName 角色名
     * @param userContent 用户消息内容
     * @param config API 配置（LLM 提取需要）
     * @param currentMessageCount 当前消息计数（外部传入时使用，null 则按 chatId 查库真实条数）
     */
    suspend fun extractIfNeeded(
        chatId: Long,
        characterId: Long,
        characterName: String,
        userContent: String,
        config: ApiConfig? = null,
        currentMessageCount: Int? = null,
    ) {
        // 正则快速提取
        val quickFacts = memoryExtractorService.extractQuickFacts(characterId, userContent, chatId, null)
        if (quickFacts.isNotEmpty()) {
            memoryConsolidator.insertWithDedup(quickFacts)
        }

        // LLM 批量提取（每 10 轮一次）。
        // 计数按 chatId 查库真实条数：此类是 @Singleton，原先的共享可变 messageCount 会跨聊天累加，
        // 且只在 ChatViewModel init 时 seed 一次，导致提取节奏错乱 + 并发 ViewModel 间竞态（X 审计 CONFIRMED）。
        val count = currentMessageCount ?: chatRepository.getMessageCount(chatId)
        if (memoryExtractorService.shouldExtract(count) && config != null) {
            val allMessages = chatRepository.getRecentMessages(chatId, 30)
            val llmFacts = memoryExtractorService.extractWithLLM(
                characterId, allMessages.reversed(), characterName, config, chatId
            )
            if (llmFacts.isNotEmpty()) {
                memoryConsolidator.insertWithDedup(llmFacts)
                memoryConsolidator.maybeConsolidate(characterId)
            }
        }
    }
}
