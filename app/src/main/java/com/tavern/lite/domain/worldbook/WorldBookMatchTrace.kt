package com.tavern.lite.domain.worldbook

import com.tavern.lite.data.db.entity.WorldBookEntryEntity

/**
 * 世界书匹配追踪信息
 * 用于追踪每条匹配的来源和匹配方式
 */
data class WorldBookMatchTrace(
    /** 匹配的条目 */
    val entry: WorldBookEntryEntity,
    /** 匹配方式 */
    val matchType: MatchType,
    /** 匹配的关键词 */
    val matchedKeywords: List<String>,
    /** 匹配深度（递归匹配时） */
    val depth: Int,
    /** 是否通过概率过滤 */
    val passedProbability: Boolean
) {
    enum class MatchType {
        /** 常量条目（始终匹配） */
        CONSTANT,
        /** 主关键词匹配 */
        PRIMARY_KEY,
        /** 副关键词匹配 */
        SECONDARY_KEY,
        /** Selective AND 匹配 */
        SELECTIVE_AND,
        /** Selective OR 匹配 */
        SELECTIVE_OR,
        /** Selective NOT 匹配 */
        SELECTIVE_NOT,
        /** 递归匹配 */
        RECURSIVE
    }
}

/**
 * 世界书匹配结果
 * 包含匹配的条目列表和追踪信息
 */
data class WorldBookMatchResult(
    /** 匹配的条目列表 */
    val entries: List<WorldBookEntryEntity>,
    /** 每条匹配的追踪信息 */
    val traces: List<WorldBookMatchTrace>,
    /** 匹配深度 */
    val depth: Int,
    /** 是否通过概率过滤 */
    val passedProbability: Boolean
)
