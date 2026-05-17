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
    private val worldBookDao: WorldBookDao
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

    private fun matchEntry(entry: WorldBookEntryEntity, lowerText: String): Boolean {
        val primaryKeys: List<String> = try {
            Json.decodeFromString(entry.keys)
        } catch (_: Exception) {
            emptyList()
        }
        val secondaryKeys: List<String> = try {
            Json.decodeFromString(entry.keysSecondary)
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
