package com.tavern.lite.domain.usecase

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryConsolidator
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.network.MemoryExtractorService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractionUseCase @Inject constructor(
    private val memoryExtractorService: MemoryExtractorService,
    private val memoryConsolidator: MemoryConsolidator,
    private val memoryAtomDao: MemoryAtomDao,
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
) {
    private var messageCount: Int = 0

    /**
     * 通用记忆提取：正则快速提取 + LLM 批量提取（每 N 轮一次）
     *
     * @param chatId 聊天 ID
     * @param characterId 角色 ID
     * @param characterName 角色名
     * @param userContent 用户消息内容
     * @param config API 配置（LLM 提取需要）
     * @param currentMessageCount 当前消息计数（外部传入时使用，null 则用内部计数）
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

        // LLM 批量提取（每 10 轮一次）
        val count = currentMessageCount ?: ++messageCount
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

    /**
     * 设置消息计数（用于 ViewModel 同步）
     */
    fun setMessageCount(count: Int) {
        messageCount = count
    }

    /**
     * 获取当前消息计数
     */
    fun getMessageCount(): Int = messageCount
}
