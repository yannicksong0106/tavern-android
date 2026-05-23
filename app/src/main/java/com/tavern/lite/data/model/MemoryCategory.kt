package com.tavern.lite.data.model

enum class MemoryCategory(
    val key: String,
    val label: String,
    val emoji: String,
    val isCore: Boolean
) {
    FACT("fact", "事实", "\uD83D\uDCCB", true),
    EMOTION("emotion", "情感", "\uD83D\uDC9C", true),
    PREFERENCE("preference", "偏好", "\u2B50", true),
    EVENT("event", "事件", "\uD83D\uDCCC", true),
    HABIT("habit", "习惯", "\uD83D\uDD04", true),
    CHARACTER_CONSISTENCY("character_consistency", "角色核心", "\uD83C\uDFAD", true),
    TEMPORARY("temporary", "临时记忆", "\u23F1\uFE0F", false);

    companion object {
        fun fromKey(key: String): MemoryCategory =
            entries.find { it.key == key } ?: FACT

        val coreCategories: List<MemoryCategory> = entries.filter { it.isCore }
        val temporaryCategories: List<MemoryCategory> = entries.filter { !it.isCore }

        /** Map legacy category names to new ones */
        fun migrateLegacy(key: String): String = when (key) {
            "user_info" -> FACT.key
            "relationship" -> FACT.key
            "commitment" -> EVENT.key
            else -> key
        }
    }
}
