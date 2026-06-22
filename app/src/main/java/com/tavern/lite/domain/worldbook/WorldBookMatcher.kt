package com.tavern.lite.domain.worldbook

import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 世界书匹配引擎
 * 负责关键词匹配、selective logic、递归匹配等复杂匹配逻辑
 */
@Singleton
class WorldBookMatcher @Inject constructor() {

    /**
     * 匹配世界书条目
     */
    fun matchEntries(
        entries: List<WorldBookEntryEntity>,
        text: String,
        keysForEntry: (WorldBookEntryEntity) -> Pair<List<String>, List<String>>
    ): List<WorldBookEntryEntity> {
        val lowerText = text.lowercase()
        return entries.filter { entry ->
            entry.constant || matchesEntry(entry, lowerText, keysForEntry(entry))
        }
    }

    /**
     * 递归匹配世界书条目
     * 1. 第一轮：匹配 constant + 关键词命中的条目
     * 2. 后续轮：用已匹配条目的 content 作为新文本，匹配剩余条目
     * 3. excludeRecursion 的条目不会被递归触发
     * 4. preventRecursion 的条目不会被递归触发，但自身可以触发其他条目
     * 5. depth 字段控制条目最大扫描深度
     * 6. probability < 100 时按概率触发
     */
    fun matchEntriesRecursive(
        entries: List<WorldBookEntryEntity>,
        text: String,
        maxDepth: Int = 3,
        keysForEntry: (WorldBookEntryEntity) -> Pair<List<String>, List<String>>,
        randomPercent: () -> Double = { Random.Default.nextDouble() * 100 }
    ): List<WorldBookEntryEntity> {
        val lowerText = text.lowercase()
        val matched = mutableListOf<WorldBookEntryEntity>()
        val remaining = entries.toMutableList()

        val firstRound = remaining.filter { entry ->
            (entry.constant || matchesEntry(entry, lowerText, keysForEntry(entry))) && passesProbability(entry, randomPercent)
        }

        matched.addAll(firstRound)
        remaining.removeAll(firstRound)

        var depth = 1
        while (depth < maxDepth && remaining.isNotEmpty()) {
            val matchedContent = matched
                .filter { it.depth >= depth }
                .joinToString(" ") { it.content }
                .lowercase()

            if (matchedContent.isBlank()) break

            val newMatches = remaining.filter { entry ->
                !entry.excludeRecursion &&
                    !entry.preventRecursion &&
                    passesProbability(entry, randomPercent) &&
                    matchesEntry(entry, matchedContent, keysForEntry(entry))
            }

            if (newMatches.isEmpty()) break

            matched.addAll(newMatches)
            remaining.removeAll(newMatches)
            depth++
        }

        return matched
    }

    /**
     * 检查单个条目是否匹配
     */
    fun matchesEntry(
        entry: WorldBookEntryEntity,
        lowerText: String,
        keys: Pair<List<String>, List<String>>
    ): Boolean {
        val (primaryKeys, secondaryKeys) = keys
        if (primaryKeys.isEmpty() && secondaryKeys.isEmpty()) return false

        val primaryMatches = primaryKeys.map { key -> lowerText.contains(key) }
        val secondaryMatches = secondaryKeys.map { key -> lowerText.contains(key) }

        return if (entry.selective && secondaryKeys.isNotEmpty()) {
            when (entry.selectiveLogic) {
                SELECTIVE_AND -> primaryMatches.any { it } && secondaryMatches.any { it }
                SELECTIVE_OR -> primaryMatches.any { it } || secondaryMatches.any { it }
                SELECTIVE_NOT -> primaryMatches.any { it } && secondaryMatches.none { it }
                else -> primaryMatches.any { it }
            }
        } else {
            primaryMatches.any { it }
        }
    }

    private fun passesProbability(
        entry: WorldBookEntryEntity,
        randomPercent: () -> Double
    ): Boolean = entry.probability >= 100 || randomPercent() < entry.probability

    private companion object {
        const val SELECTIVE_AND = 0
        const val SELECTIVE_OR = 1
        const val SELECTIVE_NOT = 2
    }
}
