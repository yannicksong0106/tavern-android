package com.tavern.lite.data.repository

import com.tavern.lite.data.db.TransactionRunner
import com.tavern.lite.data.db.dao.QuickReplyDao
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickReplyRepositoryTest {

    private val dao = FakeQuickReplyDao()
    private val repository = QuickReplyRepository(dao, ImmediateTransactionRunner)

    @Test
    fun `saveSetWithReplies replaces replies and assigns generated set id`() = runTest {
        val setId = repository.saveSetWithReplies(
            QuickReplySetEntity(name = "Core"),
            listOf(
                QuickReplyEntity(setId = 0, label = "Send", script = "/send hi"),
                QuickReplyEntity(setId = 99, label = "Echo", script = "/echo ok")
            )
        )

        assertEquals(1L, setId)
        assertEquals("Core", dao.sets.single().name)
        assertEquals(listOf(1L, 1L), dao.replies.map { it.setId })
        assertEquals(1L, dao.deletedRepliesForSet.single())
    }

    @Test
    fun `getRepliesByAutomationId delegates context filters`() = runTest {
        dao.replies = mutableListOf(
            QuickReplyEntity(setId = 1, label = "Auto", script = "/trigger", automationId = "start")
        )

        val replies = repository.getRepliesByAutomationId(" start ", characterId = 2, chatId = 3)

        assertEquals("start", dao.lastAutomationQuery?.automationId)
        assertEquals(2L, dao.lastAutomationQuery?.characterId)
        assertEquals(3L, dao.lastAutomationQuery?.chatId)
        assertEquals(1, replies.size)
    }

    @Test
    fun `getRepliesByAutomationId skips blank id without querying dao`() = runTest {
        val replies = repository.getRepliesByAutomationId("   ", characterId = 2, chatId = 3)

        assertEquals(emptyList<QuickReplyEntity>(), replies)
        assertEquals(null, dao.lastAutomationQuery)
    }

    @Test
    fun `getEnabledRepliesForContext delegates context filters`() = runTest {
        dao.replies = mutableListOf(
            QuickReplyEntity(setId = 1, label = "Enabled", script = "/echo ok"),
            QuickReplyEntity(setId = 1, label = "Disabled", script = "/echo no", enabled = false)
        )

        val replies = repository.getEnabledRepliesForContext(characterId = 2, chatId = 3)

        assertEquals(listOf("Enabled"), replies.first().map { it.label })
        assertEquals(2L, dao.lastContextQuery?.characterId)
        assertEquals(3L, dao.lastContextQuery?.chatId)
    }
}

private object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

private class FakeQuickReplyDao : QuickReplyDao {
    data class AutomationQuery(val automationId: String, val characterId: Long?, val chatId: Long?)
    data class ContextQuery(val characterId: Long?, val chatId: Long?)

    val sets = mutableListOf<QuickReplySetEntity>()
    var replies = mutableListOf<QuickReplyEntity>()
    val deletedRepliesForSet = mutableListOf<Long>()
    var lastAutomationQuery: AutomationQuery? = null
    var lastContextQuery: ContextQuery? = null
    private var nextSetId = 1L
    private var nextReplyId = 1L

    override fun getAllSets(): Flow<List<QuickReplySetEntity>> = flowOf(sets)

    override suspend fun getAllSetsSync(): List<QuickReplySetEntity> = sets

    override fun getEnabledSetsForContext(characterId: Long?, chatId: Long?): Flow<List<QuickReplySetEntity>> =
        flowOf(sets.filter { it.enabled })

    override suspend fun getSetById(id: Long): QuickReplySetEntity? = sets.find { it.id == id }

    override suspend fun insertSet(set: QuickReplySetEntity): Long {
        val id = set.id.takeIf { it != 0L } ?: nextSetId++
        sets.removeIf { it.id == id }
        sets.add(set.copy(id = id))
        return id
    }

    override suspend fun updateSet(set: QuickReplySetEntity) {
        sets.removeIf { it.id == set.id }
        sets.add(set)
    }

    override suspend fun deleteSet(set: QuickReplySetEntity) {
        sets.removeIf { it.id == set.id }
    }

    override fun getRepliesForSet(setId: Long): Flow<List<QuickReplyEntity>> =
        flowOf(replies.filter { it.setId == setId })

    override suspend fun getAllRepliesSync(): List<QuickReplyEntity> = replies

    override suspend fun getEnabledRepliesForSet(setId: Long): List<QuickReplyEntity> =
        replies.filter { it.setId == setId && it.enabled }

    override fun getEnabledRepliesForContext(characterId: Long?, chatId: Long?): Flow<List<QuickReplyEntity>> {
        lastContextQuery = ContextQuery(characterId, chatId)
        return flowOf(replies.filter { it.enabled })
    }

    override suspend fun getRepliesByAutomationId(
        automationId: String,
        characterId: Long?,
        chatId: Long?
    ): List<QuickReplyEntity> {
        lastAutomationQuery = AutomationQuery(automationId, characterId, chatId)
        return replies.filter { it.automationId == automationId && it.enabled }
    }

    override suspend fun insertReply(reply: QuickReplyEntity): Long {
        val id = reply.id.takeIf { it != 0L } ?: nextReplyId++
        replies.removeIf { it.id == id }
        replies.add(reply.copy(id = id))
        return id
    }

    override suspend fun insertReplies(replies: List<QuickReplyEntity>) {
        replies.forEach { insertReply(it) }
    }

    override suspend fun updateReply(reply: QuickReplyEntity) {
        replies.removeIf { it.id == reply.id }
        replies.add(reply)
    }

    override suspend fun deleteReply(reply: QuickReplyEntity) {
        replies.removeIf { it.id == reply.id }
    }

    override suspend fun deleteRepliesForSet(setId: Long) {
        deletedRepliesForSet.add(setId)
        replies.removeIf { it.setId == setId }
    }
}
