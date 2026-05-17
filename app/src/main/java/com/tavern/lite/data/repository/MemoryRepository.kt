package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {
    fun getMemoriesForCharacter(characterId: Long): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesForCharacter(characterId)

    suspend fun getRelevantMemories(characterId: Long, userMessage: String, limit: Int = 5): List<MemoryEntity> {
        // 提取关键词（取最长的 3 个词）
        val keywords = userMessage.split(Regex("[\\s,，。！？.!?、；;：:\"\"''\\-]+"))
            .filter { it.length >= 2 }
            .sortedByDescending { it.length }
            .take(3)

        if (keywords.isEmpty()) {
            return memoryDao.getTopMemories(characterId, limit)
        }

        val results = mutableMapOf<Long, MemoryEntity>()
        for (keyword in keywords) {
            memoryDao.searchMemories(characterId, keyword, limit).forEach { results[it.id] = it }
        }

        // 如果关键词匹配不够，补充 top memories
        if (results.size < limit) {
            memoryDao.getTopMemories(characterId, limit).forEach { results[it.id] = it }
        }

        return results.values
            .sortedByDescending { it.importance * 0.7 - (System.currentTimeMillis() - it.lastAccessed) / 86400000.0 * 0.3 }
            .take(limit)
    }

    suspend fun touchMemories(memoryIds: List<Long>) {
        memoryIds.forEach { memoryDao.touchMemory(it) }
    }

    suspend fun addMemory(characterId: Long, content: String, importance: Int = 5, source: String = "manual"): Long {
        return memoryDao.insert(
            MemoryEntity(
                characterId = characterId,
                content = content,
                importance = importance,
                source = source
            )
        )
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.update(memory)
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteById(id)
    }

    suspend fun deleteAllForCharacter(characterId: Long) {
        memoryDao.deleteAllForCharacter(characterId)
    }
}
