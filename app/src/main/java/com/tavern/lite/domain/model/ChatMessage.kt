package com.tavern.lite.domain.model

/**
 * 聊天消息 — Domain 层数据模型
 * 用于 prompt 构建和 API 通信的数据载体
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val imageUrls: List<String> = emptyList()
)
