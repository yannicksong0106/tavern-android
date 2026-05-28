package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.SummaryDao
import com.tavern.lite.data.db.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    private val summaryDao: SummaryDao
) {

    fun getSummariesForChat(chatId: Long): Flow<List<SummaryEntity>> =
        summaryDao.getSummariesForChat(chatId)

    suspend fun getLatestSummary(chatId: Long): SummaryEntity? =
        summaryDao.getLatestSummary(chatId)

    suspend fun getSummaryById(id: Long): SummaryEntity? =
        summaryDao.getSummaryById(id)

    suspend fun saveSummary(
        chatId: Long,
        content: String,
        messageRangeStart: Long,
        messageRangeEnd: Long,
        tokenCount: Int = 0
    ): Long {
        return summaryDao.insert(
            SummaryEntity(
                chatId = chatId,
                content = content,
                messageRangeStart = messageRangeStart,
                messageRangeEnd = messageRangeEnd,
                tokenCount = tokenCount
            )
        )
    }

    suspend fun updateContent(id: Long, content: String) =
        summaryDao.updateContent(id, content)

    suspend fun deleteSummary(id: Long) =
        summaryDao.deleteById(id)

    suspend fun deleteAllForChat(chatId: Long) =
        summaryDao.deleteAllForChat(chatId)
}
