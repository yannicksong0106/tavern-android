package com.tavern.lite.data.repository

import com.tavern.lite.data.db.TransactionRunner
import com.tavern.lite.data.db.dao.QuickReplyDao
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import com.tavern.lite.domain.usecase.QuickReplyShareCodec
import com.tavern.lite.domain.usecase.QuickReplySharePackage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickReplyRepository @Inject constructor(
    private val quickReplyDao: QuickReplyDao,
    private val transactionRunner: TransactionRunner
) {
    private val shareCodec = QuickReplyShareCodec()

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

    /**
     * 把一个集合导出为可分享的脚本包 JSON。集合不存在时返回 null。
     * 导出不含权限标志与上下文绑定（见 [QuickReplyShareCodec]）。
     */
    suspend fun exportSetToShareJson(setId: Long): String? {
        val set = quickReplyDao.getSetById(setId) ?: return null
        val replies = quickReplyDao.getEnabledRepliesForSet(setId)
        return shareCodec.export(set, replies)
    }

    /**
     * 从分享 JSON 导入一个新集合。解析失败返回 [Result.failure]；成功返回新建 set id。
     *
     * 安全：新集合为全局 scope、启用，回复权限全部关闭（[QuickReplyShareCodec.toEntities]），
     * 导入者需在本地逐条重新授权后才能自动运行或发送消息。
     */
    suspend fun importSetFromShareJson(content: String): Result<Long> {
        val pack: QuickReplySharePackage = shareCodec.parse(content).getOrElse { return Result.failure(it) }
        val newSetId = transactionRunner.run {
            val setId = quickReplyDao.insertSet(QuickReplySetEntity(name = pack.name))
            quickReplyDao.insertReplies(shareCodec.toEntities(pack, setId))
            setId
        }
        return Result.success(newSetId)
    }
}
