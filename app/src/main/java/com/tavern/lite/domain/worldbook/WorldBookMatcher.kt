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
     * 匹配世界书条目并返回追踪信息
     */
    fun matchEntriesWithTrace(
        entries: List<WorldBookEntryEntity>,
        text: String,
        keysForEntry: (WorldBookEntryEntity) -> Pair<List<String>, List<String>>
    ): WorldBookMatchResult {
        val lowerText = text.lowercase()
        val matchedEntries = mutableListOf<WorldBookEntryEntity>()
        val traces = mutableListOf<WorldBookMatchTrace>()

        for (entry in entries) {
            if (entry.constant) {
                matchedEntries.add(entry)
                traces.add(WorldBookMatchTrace(
                    entry = entry,
                    matchType = WorldBookMatchTrace.MatchType.CONSTANT,
                    matchedKeywords = emptyList(),
                    depth = 0,
                    passedProbability = true
                ))
            } else {
                val keys = keysForEntry(entry)
                if (matchesEntry(entry, lowerText, keys)) {
                    matchedEntries.add(entry)
                    traces.add(WorldBookMatchTrace(
                        entry = entry,
                        matchType = getMatchType(entry, keys),
                        matchedKeywords = getMatchedKeywords(entry, lowerText, keys),
                        depth = 0,
                        passedProbability = true
                    ))
                }
            }
        }

        return WorldBookMatchResult(
            entries = matchedEntries,
            traces = traces,
            depth = 0,
            passedProbability = true
        )
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
     * 递归匹配世界书条目并返回追踪信息
     */
    fun matchEntriesRecursiveWithTrace(
        entries: List<WorldBookEntryEntity>,
        text: String,
        maxDepth: Int = 3,
        keysForEntry: (WorldBookEntryEntity) -> Pair<List<String>, List<String>>,
        randomPercent: () -> Double = { Random.Default.nextDouble() * 100 }
    ): WorldBookMatchResult {
        val lowerText = text.lowercase()
        val matched = mutableListOf<WorldBookEntryEntity>()
        val traces = mutableListOf<WorldBookMatchTrace>()
        val remaining = entries.toMutableList()

        val firstRound = remaining.filter { entry ->
            (entry.constant || matchesEntry(entry, lowerText, keysForEntry(entry))) && passesProbability(entry, randomPercent)
        }

        for (entry in firstRound) {
            matched.add(entry)
            traces.add(WorldBookMatchTrace(
                entry = entry,
                matchType = if (entry.constant) WorldBookMatchTrace.MatchType.CONSTANT else getMatchType(entry, keysForEntry(entry)),
                matchedKeywords = if (entry.constant) emptyList() else getMatchedKeywords(entry, lowerText, keysForEntry(entry)),
                depth = 0,
                passedProbability = true
            ))
        }
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

            for (entry in newMatches) {
                matched.add(entry)
                traces.add(WorldBookMatchTrace(
                    entry = entry,
                    matchType = WorldBookMatchTrace.MatchType.RECURSIVE,
                    matchedKeywords = getMatchedKeywords(entry, matchedContent, keysForEntry(entry)),
                    depth = depth,
                    passedProbability = true
                ))
            }
            remaining.removeAll(newMatches)
            depth++
        }

        return WorldBookMatchResult(
            entries = matched,
            traces = traces,
            depth = depth,
            passedProbability = true
        )
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

    private fun getMatchType(
        entry: WorldBookEntryEntity,
        keys: Pair<List<String>, List<String>>
    ): WorldBookMatchTrace.MatchType {
        val (primaryKeys, secondaryKeys) = keys
        return if (entry.selective && secondaryKeys.isNotEmpty()) {
            when (entry.selectiveLogic) {
                0 -> WorldBookMatchTrace.MatchType.SELECTIVE_AND
                1 -> WorldBookMatchTrace.MatchType.SELECTIVE_OR
                2 -> WorldBookMatchTrace.MatchType.SELECTIVE_NOT
                else -> WorldBookMatchTrace.MatchType.PRIMARY_KEY
            }
        } else {
            WorldBookMatchTrace.MatchType.PRIMARY_KEY
        }
    }

    private fun getMatchedKeywords(
        entry: WorldBookEntryEntity,
        lowerText: String,
        keys: Pair<List<String>, List<String>>
    ): List<String> {
        val (primaryKeys, secondaryKeys) = keys
        val matched = mutableListOf<String>()
        matched.addAll(primaryKeys.filter { lowerText.contains(it) })
        matched.addAll(secondaryKeys.filter { lowerText.contains(it) })
        return matched
    }

    private companion object {
        const val SELECTIVE_AND = 0
        const val SELECTIVE_OR = 1
        const val SELECTIVE_NOT = 2
    }
}
