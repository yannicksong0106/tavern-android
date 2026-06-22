package com.tavern.lite.network

/**
 * Prompt 段落数据类
 * 用于追踪每段 prompt 的来源、内容和 token 估算
 */
data class PromptSection(
    /** 来源标识 */
    val source: String,
    /** 实际内容 */
    val content: String,
    /** 估算 token 数（简化实现：content.length / 4） */
    val tokenEstimate: Int,
    /** 优先级（用于排序，0 = 最高） */
    val priority: Int = 0
) {
    companion object {
        /**
         * 从内容创建 PromptSection，自动估算 token 数
         */
        fun create(source: String, content: String, priority: Int = 0): PromptSection {
            return PromptSection(
                source = source,
                content = content,
                tokenEstimate = content.length / 4,
                priority = priority
            )
        }
    }
}

/**
 * Prompt 来源常量
 */
object PromptSource {
    /** 系统提示词 */
    const val SYSTEM = "system"

    /** 世界书 */
    const val WORLD_BOOK = "world_book"

    /** 扁平记忆（旧版） */
    const val MEMORY = "memory"

    /** 角色一致性记忆（人设红线） */
    const val CHARACTER_CONSISTENCY = "character_consistency"

    /** 作者注释 */
    const val AUTHOR_NOTE = "author_note"

    /** 预设 */
    const val PRESET = "preset"

    /** 搜索结果 */
    const val SEARCH = "search"

    /** 摘要 */
    const val SUMMARY = "summary"

    /** 用户人格 */
    const val PERSONA = "persona"

    /** 示例对话 */
    const val EXAMPLE_DIALOG = "example_dialog"

    /** 用户消息 */
    const val USER_MESSAGE = "user_message"

    /** 聊天历史 */
    const val CHAT_HISTORY = "chat_history"

    /** 图片 URL */
    const val IMAGE_URL = "image_url"

    /** 群聊角色信息 */
    const val GROUP_CHARACTERS = "group_characters"
}
