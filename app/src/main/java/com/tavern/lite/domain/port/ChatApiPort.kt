package com.tavern.lite.domain.port

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.model.ChatStreamChunk
import kotlinx.coroutines.flow.Flow

/**
 * 聊天 API 服务接口 — Domain 层端口
 * 由 network 层实现，domain 层通过此接口调用 API
 */
interface ChatApiPort {
    fun streamChat(messages: List<ChatMessage>, config: ApiConfig): Flow<String>
    fun streamChatWithMetadata(messages: List<ChatMessage>, config: ApiConfig): Flow<ChatStreamChunk>
}
