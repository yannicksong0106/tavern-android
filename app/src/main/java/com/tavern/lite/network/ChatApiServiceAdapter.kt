package com.tavern.lite.network

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.model.ChatStreamChunk
import com.tavern.lite.domain.port.ChatApiPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChatApiService 适配器 — 将 domain 层 ChatApiPort 调用委托给 network 层 ChatApiService
 */
@Singleton
class ChatApiServiceAdapter @Inject constructor(
    private val delegate: ChatApiService
) : ChatApiPort {

    override fun streamChat(messages: List<ChatMessage>, config: ApiConfig): Flow<String> {
        return delegate.streamChat(messages, config)
    }

    override fun streamChatWithMetadata(messages: List<ChatMessage>, config: ApiConfig): Flow<ChatStreamChunk> {
        return delegate.streamChatWithMetadata(messages, config)
    }
}
