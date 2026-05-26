package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.CategoryCount
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val memoryAtomDao: MemoryAtomDao
) {
    companion object {
        private val KEYWORD_SPLIT_REGEX = Regex("[\\s,，。！？.!?、；;：:\"\"''\\-]+")
    }
    fun getMemoriesForCharacter(characterId: Long): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesForCharacter(characterId)

    suspend fun getRelevantMemories(characterId: Long, userMessage: String, limit: Int = 5): List<MemoryEntity> {
        // 提取关键词（取最长的 3 个词）
        val keywords = userMessage.split(KEYWORD_SPLIT_REGEX)
            .filter { it.length >= 2 }
            .sortedByDescending { it.length }
            .take(3)

        if (keywords.isEmpty()) {
            return memoryDao.getTopMemories(characterId, limit)
        }

        // Single query with OR conditions for all keywords (avoids N+1 queries)
        val results = memoryDao.searchMemoriesMultiKeyword(characterId, keywords, limit)
            .associateBy { it.id }
            .toMutableMap()

        // 如果关键词匹配不够，补充 top memories
        if (results.size < limit) {
            memoryDao.getTopMemories(characterId, limit).forEach { results[it.id] = it }
        }

        return results.values
            .sortedByDescending { it.importance * 0.7 - (System.currentTimeMillis() - it.lastAccessed) / 86400000.0 * 0.3 }
            .take(limit)
    }

    suspend fun touchMemories(memoryIds: List<Long>) {
        if (memoryIds.isNotEmpty()) {
            memoryDao.touchMemories(memoryIds)
        }
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

    // ==================== Memory Atom methods ====================

    fun getAtomsForCharacter(characterId: Long): Flow<List<MemoryAtomEntity>> =
        memoryAtomDao.getAtomsForCharacter(characterId)

    fun getCategoryCounts(characterId: Long): Flow<List<CategoryCount>> =
        memoryAtomDao.getCategoryCounts(characterId)

    fun getAtomsByCategory(characterId: Long, category: String): Flow<List<MemoryAtomEntity>> =
        memoryAtomDao.getAtomsByCategoryFlow(characterId, category)

    fun searchAtoms(characterId: Long, query: String): Flow<List<MemoryAtomEntity>> =
        memoryAtomDao.searchAtomsFlow(characterId, query)

    fun getLastExtractionTime(characterId: Long): Flow<Long?> =
        memoryAtomDao.getLastExtractionTime(characterId)

    suspend fun insertAtom(atom: MemoryAtomEntity): Long =
        memoryAtomDao.insert(atom)

    suspend fun updateAtom(atom: MemoryAtomEntity) =
        memoryAtomDao.update(atom)

    suspend fun deleteAtom(id: Long) =
        memoryAtomDao.deleteById(id)

    suspend fun deleteAllAtomsForCharacter(characterId: Long) =
        memoryAtomDao.deleteAllForCharacter(characterId)

    suspend fun purgeExpired() =
        memoryAtomDao.purgeExpired()
}
