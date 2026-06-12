package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlin.random.Random

internal class AssistantReplyCommitter(
    private val chatId: Long,
    private val chatRepository: ChatRepository,
    private val random: Random = Random.Default
) {
    suspend fun commitAssistantReply(
        assistantMsgId: Long?,
        isCancelled: () -> Boolean,
        updateEmotion: Boolean = true,
        respectCancellation: Boolean = true,
        onEmotionUpdate: (String) -> Unit,
        onAssistantReplyCommitted: () -> Unit
    ): Boolean {
        if (assistantMsgId == null || (respectCancellation && isCancelled())) return false
        val assistantMsg = chatRepository.getMessageById(assistantMsgId)
        if (updateEmotion && assistantMsg != null) {
            onEmotionUpdate(assistantMsg.content)
        }
        splitIntoMultipleMessages(assistantMsgId, assistantMsg)
        onAssistantReplyCommitted()
        return true
    }

    private suspend fun splitIntoMultipleMessages(
        assistantMsgId: Long,
        initialMessage: MessageEntity? = null
    ) {
        val msg = initialMessage ?: chatRepository.getMessageById(assistantMsgId) ?: return
        val content = msg.content.trim()
        if (content.isBlank()) return

        val paragraphs = content.split(PARAGRAPH_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.size <= 1) return

        val msgCharacterId = msg.characterId
        chatRepository.updateMessageContent(assistantMsgId, paragraphs[0])

        for (i in 1 until paragraphs.size) {
            val len = paragraphs[i].length
            val baseDelay = (400L + len * 30L).coerceIn(500L, 2000L)
            val jitter = random.nextLong(-200, 200)
            delay(baseDelay + jitter)
            chatRepository.sendMessage(chatId, paragraphs[i], "assistant", msgCharacterId)
        }
    }

    private companion object {
        val PARAGRAPH_SPLIT_REGEX: Regex = Regex("\n{2,}")
    }
}
