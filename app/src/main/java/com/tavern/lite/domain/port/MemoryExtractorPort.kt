package com.tavern.lite.domain.port

import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig

/**
 * 记忆提取服务端口 — Domain 层端口
 * 由 network 层 MemoryExtractorService 实现
 */
interface MemoryExtractorPort {
    fun extractQuickFacts(characterId: Long, userMessage: String, chatId: Long?, messageId: Long?): List<MemoryAtomEntity>
    fun shouldExtract(totalMessages: Int): Boolean
    suspend fun extractWithLLM(characterId: Long, messages: List<MessageEntity>, characterName: String, config: ApiConfig, chatId: Long?): List<MemoryAtomEntity>
}
