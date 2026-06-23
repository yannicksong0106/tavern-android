package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.domain.worldbook.WorldBookMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorldBookMatchTest {

    private lateinit var repository: WorldBookRepository
    private lateinit var fakeDao: FakeWorldBookDao

    @Before
    fun setup() {
        fakeDao = FakeWorldBookDao()
        repository = WorldBookRepository(fakeDao, Json, WorldBookMatcher())
    }

    @Test
    fun `matchEntries returns constant entries regardless of text`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(
                id = 1,
                keys = "[\"dragon\"]",
                constant = true,
                content = "Always included"
            )
        )
        val results = repository.matchEntries(1, "hello world")
        assertEquals(1, results.size)
        assertEquals("Always included", results[0].content)
    }

    @Test
    fun `matchEntries matches primary key in text`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(id = 1, keys = "[\"dragon\"]", content = "Dragon info")
        )
        val results = repository.matchEntries(1, "I saw a dragon today")
        assertEquals(1, results.size)
    }

    @Test
    fun `matchEntries is case insensitive`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(id = 1, keys = "[\"Dragon\"]", content = "Dragon info")
        )
        val results = repository.matchEntries(1, "i saw a DRAGON today")
        assertEquals(1, results.size)
    }

    @Test
    fun `matchEntries does not match unrelated text`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(id = 1, keys = "[\"dragon\"]", content = "Dragon info")
        )
        val results = repository.matchEntries(1, "hello world")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matchEntries with selective AND matches when both keys present`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(
                id = 1,
                keys = "[\"dragon\"]",
                keysSecondary = "[\"fire\"]",
                selective = true,
                selectiveLogic = 0, // AND
                content = "Fire dragon"
            )
        )
        val results = repository.matchEntries(1, "the dragon breathes fire")
        assertEquals(1, results.size)
    }

    @Test
    fun `matchEntries with selective AND does not match when only primary key present`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(
                id = 1,
                keys = "[\"dragon\"]",
                keysSecondary = "[\"fire\"]",
                selective = true,
                selectiveLogic = 0, // AND
                content = "Fire dragon"
            )
        )
        val results = repository.matchEntries(1, "the dragon flies")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matchEntries with selective OR matches when only secondary key present`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(
                id = 1,
                keys = "[\"dragon\"]",
                keysSecondary = "[\"fire\"]",
                selective = true,
                selectiveLogic = 1, // OR
                content = "Dragon or fire"
            )
        )
        val results = repository.matchEntries(1, "the fire burns")
        assertEquals(1, results.size)
    }

    @Test
    fun `matchEntries with selective NOT excludes when secondary key present`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(
                id = 1,
                keys = "[\"dragon\"]",
                keysSecondary = "[\"fire\"]",
                selective = true,
                selectiveLogic = 2, // NOT
                content = "Ice dragon"
            )
        )
        // Should NOT match when fire is present
        val results1 = repository.matchEntries(1, "the dragon breathes fire")
        assertTrue(results1.isEmpty())

        // Should match when fire is NOT present
        val results2 = repository.matchEntries(1, "the dragon flies")
        assertEquals(1, results2.size)
    }

    @Test
    fun `matchEntries with empty keys returns nothing`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(id = 1, keys = "[]", keysSecondary = "[]", content = "No keys")
        )
        val results = repository.matchEntries(1, "anything")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matchEntries matches any of multiple primary keys`() = runTest {
        fakeDao.matchableEntries = listOf(
            makeEntry(id = 1, keys = "[\"dragon\", \"wyrm\", \"drake\"]", content = "Dragon lore")
        )
        assertTrue(repository.matchEntries(1, "the drake appeared").isNotEmpty())
        assertTrue(repository.matchEntries(1, "a wyrm sleeps").isNotEmpty())
        assertFalse(repository.matchEntries(1, "a goblin attacks").isNotEmpty())
    }
}

private fun makeEntry(
    id: Long = 1,
    worldBookId: Long = 1,
    keys: String = "[]",
    keysSecondary: String = "[]",
    content: String = "",
    constant: Boolean = false,
    selective: Boolean = false,
    selectiveLogic: Int = 0,
    disabled: Boolean = false
) = WorldBookEntryEntity(
    id = id,
    worldBookId = worldBookId,
    keys = keys,
    keysSecondary = keysSecondary,
    content = content,
    constant = constant,
    selective = selective,
    selectiveLogic = selectiveLogic,
    disabled = disabled
)

private class FakeWorldBookDao : WorldBookDao {
    var matchableEntries: List<WorldBookEntryEntity> = emptyList()

    override fun getAllWorldBooks(): Flow<List<WorldBookEntity>> = flowOf(emptyList())
    override suspend fun getWorldBookById(id: Long): WorldBookEntity? = null
    override suspend fun insertWorldBook(worldBook: WorldBookEntity): Long = 0
    override suspend fun updateWorldBook(worldBook: WorldBookEntity) {}
    override suspend fun deleteWorldBook(worldBook: WorldBookEntity) {}
    override fun getAllEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>> = flowOf(emptyList())
    override fun getActiveEntries(worldBookId: Long): Flow<List<WorldBookEntryEntity>> = flowOf(emptyList())
    override suspend fun getMatchableEntries(worldBookId: Long): List<WorldBookEntryEntity> = matchableEntries
    override suspend fun insertEntry(entry: WorldBookEntryEntity): Long = 0
    override suspend fun updateEntry(entry: WorldBookEntryEntity) {}
    override suspend fun deleteEntry(entry: WorldBookEntryEntity) {}
    override suspend fun getAllWorldBooksSync(): List<WorldBookEntity> = emptyList()
    override suspend fun getAllEntriesSync(): List<WorldBookEntryEntity> = emptyList()
}
