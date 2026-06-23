package com.tavern.lite.domain.port

import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.model.PromptConfig

/**
 * Prompt 构建结果（含 section 追踪）
 */
data class PromptBuildResult(
    val messages: List<ChatMessage>,
    val sections: List<PromptSectionInfo>
)

/**
 * Prompt section 信息（Domain 层表示）
 */
data class PromptSectionInfo(
    val source: String,
    val content: String,
    val tokenEstimate: Int,
    val priority: Int = 0
)

/**
 * Prompt 构建器接口 — Domain 层端口
 * 由 network 层的 PromptBuilderAdapter 实现
 */
interface PromptBuilderPort {
    fun build(config: PromptConfig): List<ChatMessage>
    fun buildWithSections(config: PromptConfig): PromptBuildResult
    fun buildGroupChat(config: PromptConfig): List<ChatMessage>
    fun buildProactive(config: PromptConfig): List<ChatMessage>
    fun buildGroupProactive(config: PromptConfig): List<ChatMessage>
    fun invalidateCache()
}
