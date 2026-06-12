package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.delay
import kotlin.random.Random

internal class GenerationSendCoordinator(
    private val chatId: Long,
    private val sendMessageUseCase: SendMessageUseCase,
    private val groupRespondingCharacterSelector: GroupRespondingCharacterSelector = GroupRespondingCharacterSelector(),
    private val random: Random = Random.Default
) {
    suspend fun sendSingle(
        character: CharacterEntity,
        content: String,
        config: ApiConfig,
        imagePaths: List<String>
    ): MessageExecutionHelper.ExecutionResult? {
        return sendMessageUseCase.sendSingleMessage(chatId, character, content, config, null, imagePaths)
    }

    suspend fun sendDirect(
        characters: List<CharacterEntity>,
        targetCharacter: CharacterEntity,
        content: String,
        config: ApiConfig,
        imagePaths: List<String>
    ): MessageExecutionHelper.ExecutionResult? {
        return sendMessageUseCase.sendDirectMessage(
            chatId = chatId,
            characters = characters,
            targetCharacter = targetCharacter,
            userContent = content,
            config = config,
            imagePaths = imagePaths
        )
    }

    suspend fun sendGroup(
        characters: List<CharacterEntity>,
        content: String,
        config: ApiConfig,
        imagePaths: List<String>,
        schedulingStrategy: GroupSchedulingStrategy,
        chattinessByCharacterId: Map<Long, Int>,
        intervalMs: Long,
        isCancelled: () -> Boolean,
        onRespondingCharacterChanged: (CharacterEntity?) -> Unit,
        onAssistantReplyCommit: suspend (Long?) -> Boolean
    ): GroupSendResult {
        if (characters.isEmpty()) return GroupSendResult(emptyList())

        val respondingCharacters = groupRespondingCharacterSelector.select(
            characters = characters,
            schedulingStrategy = schedulingStrategy,
            chattinessByCharacterId = chattinessByCharacterId
        )

        val results = sendMessageUseCase.sendGroupMessage(
            chatId = chatId,
            characters = respondingCharacters,
            userContent = content,
            config = config,
            imagePaths = imagePaths
        )

        for ((index, entry) in results.withIndex()) {
            if (isCancelled()) break
            val (characterId, result) = entry
            onRespondingCharacterChanged(characters.find { it.id == characterId })
            onAssistantReplyCommit(result.assistantMsgId)
            if (index != results.lastIndex && !isCancelled()) {
                val jitterBound = (intervalMs * JITTER_RATIO).toLong().coerceAtLeast(MIN_JITTER_BOUND)
                delay(intervalMs + random.nextLong(jitterBound))
            }
        }

        return GroupSendResult(results)
    }

    data class GroupSendResult(
        val results: List<Pair<Long, MessageExecutionHelper.ExecutionResult>>
    )

    private companion object {
        const val JITTER_RATIO = 0.3
        const val MIN_JITTER_BOUND = 1L
    }
}
