package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryRepositoryTest {

    private lateinit var repository: MemoryRepository
    private lateinit var fakeDao: FakeMemoryDao

    @Before
    fun setup() {
        fakeDao = FakeMemoryDao()
        repository = MemoryRepository(fakeDao)
    }

    @Test
    fun `addMemory inserts with correct fields`() = runTest {
        val id = repository.addMemory(1, "User likes cats", importance = 8, source = "llm")
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
        val mem = fakeDao.inserted[0]
        assertEquals(1L, mem.characterId)
        assertEquals("User likes cats", mem.content)
        assertEquals(8, mem.importance)
        assertEquals("llm", mem.source)
    }

    @Test
    fun `addMemory uses default importance and source`() = runTest {
        repository.addMemory(1, "Something")
        val mem = fakeDao.inserted[0]
        assertEquals(5, mem.importance)
        assertEquals("manual", mem.source)
    }

    @Test
    fun `deleteMemory calls deleteById`() = runTest {
        repository.deleteMemory(42)
        assertEquals(42L, fakeDao.deletedId)
    }

    @Test
    fun `deleteAllForCharacter calls dao`() = runTest {
        repository.deleteAllForCharacter(7)
        assertEquals(7L, fakeDao.deletedCharacterId)
    }

    @Test
    fun `updateMemory calls dao update`() = runTest {
        val mem = MemoryEntity(id = 1, characterId = 1, content = "old")
        repository.updateMemory(mem)
        assertNotNull(fakeDao.updatedMemory)
        assertEquals("old", fakeDao.updatedMemory!!.content)
    }

    @Test
    fun `touchMemories calls dao with ids`() = runTest {
        repository.touchMemories(listOf(1, 2, 3))
        assertEquals(listOf(1L, 2L, 3L), fakeDao.touchedIds)
    }

    @Test
    fun `touchMemories does nothing for empty list`() = runTest {
        repository.touchMemories(emptyList())
        assertNull(fakeDao.touchedIds)
    }

    @Test
    fun `getRelevantMemories falls back to top memories when no keywords`() = runTest {
        // Single-char words are filtered out (length < 2)
        fakeDao.topMemories.add(MemoryEntity(id = 1, characterId = 1, content = "hi", importance = 5))
        val result = repository.getRelevantMemories(1, "我 你 他")
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun `getRelevantMemories searches by keywords and supplements with top`() = runTest {
        fakeDao.searchResults["cats"] = listOf(
            MemoryEntity(id = 1, characterId = 1, content = "likes cats", importance = 8)
        )
        fakeDao.topMemories.add(MemoryEntity(id = 2, characterId = 1, content = "general", importance = 3))
        val result = repository.getRelevantMemories(1, "my cats are cute", limit = 5)
        // Should contain both the keyword match and the top memory supplement
        assertTrue(result.any { it.id == 1L })
    }

    @Test
    fun `getRelevantMemories deduplicates by id`() = runTest {
        val mem = MemoryEntity(id = 1, characterId = 1, content = "cats and dogs", importance = 7)
        fakeDao.searchResults["cats"] = listOf(mem)
        fakeDao.searchResults["dogs"] = listOf(mem) // same id
        fakeDao.topMemories.add(mem)
        val result = repository.getRelevantMemories(1, "cats and dogs", limit = 5)
        assertEquals(1, result.size)
    }
}

private class FakeMemoryDao : MemoryDao {
    val inserted = mutableListOf<MemoryEntity>()
    val searchResults = mutableMapOf<String, List<MemoryEntity>>()
    val topMemories = mutableListOf<MemoryEntity>()
    var deletedId: Long? = null
    var deletedCharacterId: Long? = null
    var updatedMemory: MemoryEntity? = null
    var touchedIds: List<Long>? = null
    private var nextId = 1L

    override fun getMemoriesForCharacter(characterId: Long): Flow<List<MemoryEntity>> = flowOf(emptyList())
    override suspend fun searchMemories(characterId: Long, keyword: String, limit: Int): List<MemoryEntity> =
        searchResults[keyword]?.take(limit) ?: emptyList()
    override suspend fun getTopMemories(characterId: Long, limit: Int, now: Long): List<MemoryEntity> =
        topMemories.take(limit)
    override suspend fun insert(memory: MemoryEntity): Long {
        val id = nextId++
        inserted.add(memory.copy(id = id))
        return id
    }
    override suspend fun update(memory: MemoryEntity) { updatedMemory = memory }
    override suspend fun deleteById(id: Long) { deletedId = id }
    override suspend fun deleteAllForCharacter(characterId: Long) { deletedCharacterId = characterId }
    override suspend fun touchMemory(id: Long, now: Long) {}
    override suspend fun touchMemories(ids: List<Long>, now: Long) { touchedIds = ids }
    override suspend fun getAllMemories(): List<MemoryEntity> = inserted.toList()
}
