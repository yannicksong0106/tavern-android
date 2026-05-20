package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorldBookRepository @Inject constructor(
    private val worldBookDao: WorldBookDao,
    private val json: Json
) {
    fun getAllWorldBooks(): Flow<List<WorldBookEntity>> = worldBookDao.getAllWorldBooks()

    suspend fun getWorldBookById(id: Long): WorldBookEntity? = worldBookDao.getWorldBookById(id)

    suspend fun createWorldBook(name: String, description: String = ""): Long {
        return worldBookDao.insertWorldBook(WorldBookEntity(name = name, description = description))
    }

    suspend fun updateWorldBook(worldBook: WorldBookEntity) = worldBookDao.updateWorldBook(worldBook)

    suspend fun deleteWorldBook(worldBook: WorldBookEntity) = worldBookDao.deleteWorldBook(worldBook)

    fun getEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>> =
        worldBookDao.getAllEntries(worldBookId)

    fun getActiveEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>> =
        worldBookDao.getActiveEntries(worldBookId)

    suspend fun insertEntry(entry: WorldBookEntryEntity): Long = worldBookDao.insertEntry(entry)

    suspend fun updateEntry(entry: WorldBookEntryEntity) = worldBookDao.updateEntry(entry)

    suspend fun deleteEntry(entry: WorldBookEntryEntity) = worldBookDao.deleteEntry(entry)

    suspend fun matchEntries(worldBookId: Long, text: String): List<WorldBookEntryEntity> {
        val entries = worldBookDao.getMatchableEntries(worldBookId)
        val lowerText = text.lowercase()
        return entries.filter { entry ->
            entry.constant || matchEntry(entry, lowerText)
        }
    }

    /**
     * 递归匹配世界书条目。
     * 1. 第一轮：匹配 constant + 关键词命中的条目
     * 2. 后续轮：用已匹配条目的 content 作为新文本，匹配剩余条目
     * 3. excludeRecursion 的条目不会被递归触发
     * 4. preventRecursion 的条目不会被递归触发，但自身可以触发其他条目
     * 5. depth 字段控制条目最大扫描深度
     * 6. probability < 100 时按概率触发
     */
    suspend fun matchEntriesRecursive(
        worldBookId: Long,
        text: String,
        maxDepth: Int = 3
    ): List<WorldBookEntryEntity> {
        val allEntries = worldBookDao.getMatchableEntries(worldBookId)
        val lowerText = text.lowercase()

        // 第一轮：匹配 constant + 关键词命中
        val matched = mutableListOf<WorldBookEntryEntity>()
        val remaining = allEntries.toMutableList()

        val firstRound = remaining.filter { entry ->
            entry.constant || matchEntry(entry, lowerText)
        }.filter { entry ->
            // 概率过滤
            entry.probability >= 100 || (Math.random() * 100 < entry.probability)
        }

        matched.addAll(firstRound)
        remaining.removeAll(firstRound)

        // 递归匹配
        var depth = 1
        while (depth < maxDepth && remaining.isNotEmpty()) {
            val matchedContent = firstRound
                .filter { it.depth >= depth }  // 条目的 depth 控制最大扫描深度
                .joinToString(" ") { it.content }
                .lowercase()

            if (matchedContent.isBlank()) break

            val newMatches = remaining.filter { entry ->
                // excludeRecursion 的条目不会被递归触发
                !entry.excludeRecursion &&
                // preventRecursion 的条目不会被递归触发
                !entry.preventRecursion &&
                // 概率过滤
                (entry.probability >= 100 || (Math.random() * 100 < entry.probability)) &&
                // 关键词匹配
                matchEntry(entry, matchedContent)
            }

            if (newMatches.isEmpty()) break

            matched.addAll(newMatches)
            remaining.removeAll(newMatches)
            depth++
        }

        return matched
    }

    private fun matchEntry(entry: WorldBookEntryEntity, lowerText: String): Boolean {
        val primaryKeys: List<String> = try {
            json.decodeFromString(entry.keys)
        } catch (_: Exception) {
            emptyList()
        }
        val secondaryKeys: List<String> = try {
            json.decodeFromString(entry.keysSecondary)
        } catch (_: Exception) {
            emptyList()
        }

        if (primaryKeys.isEmpty() && secondaryKeys.isEmpty()) return false

        val primaryMatches = primaryKeys.map { key -> lowerText.contains(key.lowercase()) }
        val secondaryMatches = secondaryKeys.map { key -> lowerText.contains(key.lowercase()) }

        return if (entry.selective && secondaryKeys.isNotEmpty()) {
            // Selective mode: 使用 primary + secondary keys + logic
            when (entry.selectiveLogic) {
                0 -> // AND: primary AND secondary
                    primaryMatches.any { it } && secondaryMatches.any { it }
                1 -> // OR: primary OR secondary
                    primaryMatches.any { it } || secondaryMatches.any { it }
                2 -> // NOT: primary AND NOT secondary
                    primaryMatches.any { it } && secondaryMatches.none { it }
                else -> primaryMatches.any { it }
            }
        } else {
            // 非 selective：任一 primary key 匹配即可
            primaryMatches.any { it }
        }
    }
}
