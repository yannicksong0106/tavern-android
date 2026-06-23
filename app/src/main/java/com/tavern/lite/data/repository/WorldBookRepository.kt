package com.tavern.lite.data.repository

import android.util.Log
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.domain.worldbook.WorldBookMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorldBookRepository @Inject constructor(
    private val worldBookDao: WorldBookDao,
    private val json: Json,
    private val worldBookMatcher: WorldBookMatcher
) {
    // Cache decoded+lowercased keys by entry ID to avoid repeated JSON parsing (LRU, max 256)
    private val keyCache = object : LinkedHashMap<Long, Pair<List<String>, List<String>>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<List<String>, List<String>>>?): Boolean {
            return size > 256
        }
    }

    private fun getCachedKeys(entry: WorldBookEntryEntity): Pair<List<String>, List<String>> {
        return keyCache.getOrPut(entry.id) {
            val primary = try {
                json.decodeFromString<List<String>>(entry.keys).map { it.lowercase() }
            } catch (e: Exception) {
                Log.w("WorldBookRepository", "Failed to decode keys for entry ${entry.id}: ${e.message}", e)
                emptyList()
            }
            val secondary = try {
                json.decodeFromString<List<String>>(entry.keysSecondary).map { it.lowercase() }
            } catch (e: Exception) {
                Log.w("WorldBookRepository", "Failed to decode secondaryKeys for entry ${entry.id}: ${e.message}", e)
                emptyList()
            }
            primary to secondary
        }
    }
    fun getAllWorldBooks(): Flow<List<WorldBookEntity>> = worldBookDao.getAllWorldBooks()

    suspend fun getWorldBookById(id: Long): WorldBookEntity? = worldBookDao.getWorldBookById(id)

    suspend fun createWorldBook(name: String, description: String = ""): Long {
        return worldBookDao.insertWorldBook(WorldBookEntity(name = name, description = description))
    }

    suspend fun updateWorldBook(worldBook: WorldBookEntity) = worldBookDao.updateWorldBook(worldBook)

    suspend fun deleteWorldBook(worldBook: WorldBookEntity) {
        keyCache.clear()
        worldBookDao.deleteWorldBook(worldBook)
    }

    fun getEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>> =
        worldBookDao.getAllEntries(worldBookId)

    fun getActiveEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>> =
        worldBookDao.getActiveEntries(worldBookId)

    suspend fun insertEntry(entry: WorldBookEntryEntity): Long = worldBookDao.insertEntry(entry)

    suspend fun updateEntry(entry: WorldBookEntryEntity) {
        keyCache.remove(entry.id)
        worldBookDao.updateEntry(entry)
    }

    suspend fun deleteEntry(entry: WorldBookEntryEntity) {
        keyCache.remove(entry.id)
        worldBookDao.deleteEntry(entry)
    }

    suspend fun matchEntries(worldBookId: Long, text: String): List<WorldBookEntryEntity> {
        val entries = worldBookDao.getMatchableEntries(worldBookId)
        return worldBookMatcher.matchEntries(entries, text) { entry -> getCachedKeys(entry) }
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
        val entries = worldBookDao.getMatchableEntries(worldBookId)
        return worldBookMatcher.matchEntriesRecursive(entries, text, maxDepth, keysForEntry = { entry -> getCachedKeys(entry) })
    }
}
