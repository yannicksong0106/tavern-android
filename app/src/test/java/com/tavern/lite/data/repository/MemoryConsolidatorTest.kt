package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.CategoryCount
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class MemoryConsolidatorTest {

    private lateinit var consolidator: MemoryConsolidator
    private lateinit var fakeDao: FakeMemoryAtomDao

    @Before
    fun setup() {
        fakeDao = FakeMemoryAtomDao()
        val mockDb = mock(com.tavern.lite.data.db.TavernDatabase::class.java)
        consolidator = MemoryConsolidator(fakeDao, mockDb).also {
            it.transactionOverride = { block -> block() }
        }
    }

    @Test
    fun `insertWithDedup inserts all when no duplicates`() = runTest {
        val atoms = listOf(
            MemoryAtomEntity(id = 0, characterId = 1, content = "User likes cats", category = "fact"),
            MemoryAtomEntity(id = 0, characterId = 1, content = "User is tall", category = "fact")
        )
        val count = consolidator.insertWithDedup(atoms)
        assertEquals(2, count)
        assertEquals(2, fakeDao.inserted.size)
    }

    @Test
    fun `insertWithDedup skips exact substring match`() = runTest {
        fakeDao.existingAtoms.add(
            MemoryAtomEntity(id = 1, characterId = 1, content = "User likes cats very much", category = "fact")
        )
        fakeDao.categoryAtoms["fact"] = listOf(
            MemoryAtomEntity(id = 1, characterId = 1, content = "User likes cats very much", category = "fact")
        )
        val atoms = listOf(
            MemoryAtomEntity(id = 0, characterId = 1, content = "likes cats", category = "fact")
        )
        val count = consolidator.insertWithDedup(atoms)
        assertEquals(0, count)
        assertEquals(0, fakeDao.inserted.size)
        assertTrue(fakeDao.touchedAtomIds.contains(1L))
    }

    @Test
    fun `insertWithDedup skips keyword-similar atom`() = runTest {
        val existing = MemoryAtomEntity(id = 1, characterId = 1, content = "user likes cats and dogs", category = "fact", importance = 5)
        fakeDao.existingAtoms.add(existing)
        fakeDao.categoryAtoms["fact"] = listOf(existing)
        val atoms = listOf(
            MemoryAtomEntity(id = 0, characterId = 1, content = "user likes cats and birds", category = "fact", importance = 3)
        )
        val count = consolidator.insertWithDedup(atoms)
        assertEquals(0, count)
    }

    @Test
    fun `insertWithDedup supersedes old atom when new is more important`() = runTest {
        val existing = MemoryAtomEntity(id = 1, characterId = 1, content = "user likes cats and dogs", category = "fact", importance = 3)
        fakeDao.existingAtoms.add(existing)
        fakeDao.categoryAtoms["fact"] = listOf(existing)
        val atoms = listOf(
            MemoryAtomEntity(id = 0, characterId = 1, content = "user likes cats and birds", category = "fact", importance = 8)
        )
        val count = consolidator.insertWithDedup(atoms)
        assertEquals(1, count)
        assertTrue(fakeDao.supersededIds.contains(1L))
    }

    @Test
    fun `maybeConsolidate does nothing below threshold`() = runTest {
        fakeDao.atomCount = 30
        consolidator.maybeConsolidate(1)
        assertTrue(fakeDao.purgedCharacterIds.isEmpty())
    }

    @Test
    fun `maybeConsolidate triggers consolidation at threshold`() = runTest {
        fakeDao.atomCount = 50
        consolidator.maybeConsolidate(1)
        assertTrue(fakeDao.purgedCharacterIds.contains(1L))
    }

    @Test
    fun `consolidate purges superseded atoms`() = runTest {
        consolidator.consolidate(1)
        assertTrue(fakeDao.purgedCharacterIds.contains(1L))
    }

    @Test
    fun `consolidate resolves conflicts keeping newest and most important`() = runTest {
        val now = System.currentTimeMillis()
        fakeDao.categoryAtoms["fact"] = listOf(
            MemoryAtomEntity(id = 1, characterId = 1, content = "user is a student at school", category = "fact", importance = 5, createdAt = now - 1000),
            MemoryAtomEntity(id = 2, characterId = 1, content = "user is a student at university", category = "fact", importance = 7, createdAt = now)
        )
        consolidator.consolidate(1)
        // Keywords: {user, student, school} vs {user, student, university} → overlap=2/3=0.67 >= 0.6
        // id=2 is newer and more important, id=1 should be superseded
        assertTrue(fakeDao.supersededIds.contains(1L))
    }
}

private class FakeMemoryAtomDao : MemoryAtomDao {
    val inserted = mutableListOf<MemoryAtomEntity>()
    val existingAtoms = mutableListOf<MemoryAtomEntity>()
    val categoryAtoms = mutableMapOf<String, List<MemoryAtomEntity>>()
    val supersededIds = mutableListOf<Long>()
    val touchedAtomIds = mutableListOf<Long>()
    val purgedCharacterIds = mutableListOf<Long>()
    var atomCount = 0
    private var nextId = 1L

    override fun getAtomsForCharacter(characterId: Long): Flow<List<MemoryAtomEntity>> = flowOf(emptyList())
    override fun getAtomCountFlow(characterId: Long): Flow<Int> = flowOf(atomCount)
    override suspend fun getAtomsByCategory(characterId: Long, category: String, limit: Int): List<MemoryAtomEntity> =
        categoryAtoms[category]?.take(limit) ?: emptyList()
    override suspend fun searchAtoms(characterId: Long, keyword: String, limit: Int): List<MemoryAtomEntity> = emptyList()
    override suspend fun getTopAtoms(characterId: Long, limit: Int): List<MemoryAtomEntity> = emptyList()
    override suspend fun getCharacterConsistencyAtoms(characterId: Long, limit: Int): List<MemoryAtomEntity> = emptyList()
    override suspend fun getHighPriorityAtoms(characterId: Long): List<MemoryAtomEntity> = emptyList()
    override suspend fun findSimilar(characterId: Long, text: String): MemoryAtomEntity? {
        return existingAtoms.find { it.characterId == characterId && it.content.contains(text) }
    }
    override suspend fun getById(id: Long): MemoryAtomEntity? = existingAtoms.find { it.id == id }
    override suspend fun insert(atom: MemoryAtomEntity): Long {
        val id = nextId++
        inserted.add(atom.copy(id = id))
        return id
    }
    override suspend fun insertAll(atoms: List<MemoryAtomEntity>): List<Long> {
        return atoms.map { insert(it) }
    }
    override suspend fun update(atom: MemoryAtomEntity) {}
    override suspend fun supersede(id: Long) { supersededIds.add(id) }
    override suspend fun touchAtoms(ids: List<Long>, now: Long) { touchedAtomIds.addAll(ids) }
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteAllForCharacter(characterId: Long) {}
    override suspend fun purgeSuperseded(characterId: Long) { purgedCharacterIds.add(characterId) }
    override suspend fun getAtomCount(characterId: Long): Int = atomCount
    override suspend fun getRecentAtoms(characterId: Long, limit: Int): List<MemoryAtomEntity> = emptyList()
    override suspend fun getRelevantAtoms(characterId: Long, limit: Int, now: Long): List<MemoryAtomEntity> = emptyList()
    override suspend fun getAllMemoryAtoms(): List<MemoryAtomEntity> = inserted.toList()
    // New queries for visual memory library
    override fun getCategoryCounts(characterId: Long): Flow<List<CategoryCount>> = flowOf(emptyList())
    override fun getAtomsByCategoryFlow(characterId: Long, category: String): Flow<List<MemoryAtomEntity>> = flowOf(emptyList())
    override fun searchAtomsFlow(characterId: Long, query: String): Flow<List<MemoryAtomEntity>> = flowOf(emptyList())
    override fun getLastExtractionTime(characterId: Long): Flow<Long?> = flowOf(null)
    override suspend fun purgeExpired(now: Long) {}
    override fun getExtractedCount(characterId: Long): Flow<Int> = flowOf(0)
}
