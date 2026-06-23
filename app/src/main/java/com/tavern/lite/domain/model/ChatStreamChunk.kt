package com.tavern.lite.domain.model

/**
 * 流式响应 chunk — Domain 层数据模型
 */
data class ChatStreamChunk(
    val content: String = "",
    val reasoningContent: String? = null,
)
