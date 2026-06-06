package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.SummaryDao
import com.tavern.lite.data.db.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SummaryRepositoryTest {

    private lateinit var repository: SummaryRepository
    private lateinit var fakeDao: FakeSummaryDao

    @Before
    fun setup() {
        fakeDao = FakeSummaryDao()
        repository = SummaryRepository(fakeDao)
    }

    @Test
    fun `getSummariesForChat returns flow of summaries`() = runTest {
        val summary = SummaryEntity(
            id = 1, chatId = 10, content = "Summary content",
            messageRangeStart = 1, messageRangeEnd = 10
        )
        fakeDao.summaries[10L] = mutableListOf(summary)

        val flow = repository.getSummariesForChat(10)
        flow.collect { result ->
            assertEquals(1, result.size)
            assertEquals("Summary content", result[0].content)
        }
    }

    @Test
    fun `getLatestSummary returns most recent summary`() = runTest {
        val summary = SummaryEntity(
            id = 1, chatId = 10, content = "Latest summary",
            messageRangeStart = 1, messageRangeEnd = 10
        )
        fakeDao.summaries[10L] = mutableListOf(summary)

        val result = repository.getLatestSummary(10)
        assertNotNull(result)
        assertEquals("Latest summary", result!!.content)
    }

    @Test
    fun `getLatestSummary returns null when no summaries`() = runTest {
        val result = repository.getLatestSummary(999)
        assertNull(result)
    }

    @Test
    fun `getSummaryById returns summary`() = runTest {
        val summary = SummaryEntity(
            id = 5, chatId = 10, content = "Specific summary",
            messageRangeStart = 1, messageRangeEnd = 10
        )
        fakeDao.allSummaries.add(summary)

        val result = repository.getSummaryById(5)
        assertNotNull(result)
        assertEquals("Specific summary", result!!.content)
    }

    @Test
    fun `getSummaryById returns null when not found`() = runTest {
        val result = repository.getSummaryById(999)
        assertNull(result)
    }

    @Test
    fun `saveSummary inserts summary with correct fields`() = runTest {
        val id = repository.saveSummary(
            chatId = 10,
            content = "New summary",
            messageRangeStart = 11,
            messageRangeEnd = 20,
            tokenCount = 150
        )
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
        val summary = fakeDao.inserted[0]
        assertEquals(10L, summary.chatId)
        assertEquals("New summary", summary.content)
        assertEquals(11L, summary.messageRangeStart)
        assertEquals(20L, summary.messageRangeEnd)
        assertEquals(150, summary.tokenCount)
    }

    @Test
    fun `updateContent calls dao`() = runTest {
        repository.updateContent(5, "Updated content")
        assertEquals(5L, fakeDao.updatedId)
        assertEquals("Updated content", fakeDao.updatedContent)
    }

    @Test
    fun `deleteSummary calls dao deleteById`() = runTest {
        repository.deleteSummary(5)
        assertEquals(5L, fakeDao.deletedId)
    }

    @Test
    fun `deleteAllForChat calls dao`() = runTest {
        repository.deleteAllForChat(10)
        assertEquals(10L, fakeDao.deletedChatId)
    }
}

private class FakeSummaryDao : SummaryDao {
    val summaries = mutableMapOf<Long, MutableList<SummaryEntity>>()
    val allSummaries = mutableListOf<SummaryEntity>()
    val inserted = mutableListOf<SummaryEntity>()
    var updatedId: Long? = null
    var updatedContent: String? = null
    var deletedId: Long? = null
    var deletedChatId: Long? = null
    private var nextId = 1L

    override fun getSummariesForChat(chatId: Long): Flow<List<SummaryEntity>> =
        flowOf(summaries[chatId]?.toList() ?: emptyList())

    override suspend fun getLatestSummary(chatId: Long): SummaryEntity? =
        summaries[chatId]?.firstOrNull()

    override suspend fun getSummaryById(id: Long): SummaryEntity? =
        allSummaries.find { it.id == id } ?: inserted.find { it.id == id }

    override suspend fun getAllSummaries(): List<SummaryEntity> =
        summaries.values.flatten() + allSummaries + inserted

    override suspend fun insert(summary: SummaryEntity): Long {
        val id = nextId++
        val newSummary = summary.copy(id = id)
        inserted.add(newSummary)
        summaries.getOrPut(summary.chatId) { mutableListOf() }.add(0, newSummary)
        return id
    }

    override suspend fun updateContent(id: Long, content: String) {
        updatedId = id
        updatedContent = content
    }

    override suspend fun deleteById(id: Long) {
        deletedId = id
    }

    override suspend fun deleteAllForChat(chatId: Long) {
        deletedChatId = chatId
        summaries.remove(chatId)
    }

    override suspend fun getCountForChat(chatId: Long): Int =
        summaries[chatId]?.size ?: 0
}
