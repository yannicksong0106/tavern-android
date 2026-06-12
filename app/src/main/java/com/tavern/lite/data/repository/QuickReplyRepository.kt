package com.tavern.lite.data.repository

import com.tavern.lite.data.db.TransactionRunner
import com.tavern.lite.data.db.dao.QuickReplyDao
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickReplyRepository @Inject constructor(
    private val quickReplyDao: QuickReplyDao,
    private val transactionRunner: TransactionRunner
) {
    fun getAllSets(): Flow<List<QuickReplySetEntity>> = quickReplyDao.getAllSets()

    fun getEnabledSetsForContext(characterId: Long?, chatId: Long?): Flow<List<QuickReplySetEntity>> =
        quickReplyDao.getEnabledSetsForContext(characterId, chatId)

    fun getRepliesForSet(setId: Long): Flow<List<QuickReplyEntity>> =
        quickReplyDao.getRepliesForSet(setId)

    suspend fun getEnabledRepliesForSet(setId: Long): List<QuickReplyEntity> =
        quickReplyDao.getEnabledRepliesForSet(setId)

    fun getEnabledRepliesForContext(characterId: Long?, chatId: Long?): Flow<List<QuickReplyEntity>> =
        quickReplyDao.getEnabledRepliesForContext(characterId, chatId)

    suspend fun getRepliesByAutomationId(
        automationId: String,
        characterId: Long?,
        chatId: Long?
    ): List<QuickReplyEntity> {
        val normalizedAutomationId = automationId.trim()
        if (normalizedAutomationId.isBlank()) return emptyList()
        return quickReplyDao.getRepliesByAutomationId(normalizedAutomationId, characterId, chatId)
    }

    suspend fun saveSetWithReplies(
        set: QuickReplySetEntity,
        replies: List<QuickReplyEntity>
    ): Long = transactionRunner.run {
        val setId = quickReplyDao.insertSet(set)
        quickReplyDao.deleteRepliesForSet(setId)
        quickReplyDao.insertReplies(replies.map { it.copy(setId = setId) })
        setId
    }

    suspend fun insertSet(set: QuickReplySetEntity): Long = quickReplyDao.insertSet(set)

    suspend fun updateSet(set: QuickReplySetEntity) = quickReplyDao.updateSet(set)

    suspend fun deleteSet(set: QuickReplySetEntity) = quickReplyDao.deleteSet(set)

    suspend fun insertReply(reply: QuickReplyEntity): Long = quickReplyDao.insertReply(reply)

    suspend fun updateReply(reply: QuickReplyEntity) = quickReplyDao.updateReply(reply)

    suspend fun deleteReply(reply: QuickReplyEntity) = quickReplyDao.deleteReply(reply)
}
