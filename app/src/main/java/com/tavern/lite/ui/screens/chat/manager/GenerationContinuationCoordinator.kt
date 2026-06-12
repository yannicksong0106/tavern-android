package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.usecase.ContinueGenerationUseCase

internal class GenerationContinuationCoordinator(
    private val chatId: Long,
    private val characterId: Long,
    private val continueGenerationUseCase: ContinueGenerationUseCase
) {
    fun resolveContinueRequest(messages: List<MessageEntity>): ContinueRequest? {
        val lastAssistant = messages.lastOrNull { it.role == ROLE_ASSISTANT } ?: return null
        return ContinueRequest(
            assistantMessageId = lastAssistant.id,
            assistantContent = lastAssistant.content
        )
    }

    fun resolveRegenerateRequest(messages: List<MessageEntity>, messageId: Long): RegenerateRequest? {
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1 || messages[messageIndex].role != ROLE_ASSISTANT) return null

        val previousUserMessage = messages
            .take(messageIndex)
            .lastOrNull { it.role == ROLE_USER }
            ?: return null

        return RegenerateRequest(
            assistantMessageId = messageId,
            userContent = previousUserMessage.content
        )
    }

    suspend fun continueGeneration(
        request: ContinueRequest,
        character: CharacterEntity,
        config: ApiConfig,
        previousReasoningContent: String?
    ): MessageExecutionHelper.ExecutionResult? {
        return continueGenerationUseCase.continueGeneration(
            chatId = chatId,
            characterId = characterId,
            character = character,
            lastAssistantMsgId = request.assistantMessageId,
            lastAssistantContent = request.assistantContent,
            config = config,
            previousReasoningContent = previousReasoningContent
        )
    }

    suspend fun regenerate(
        request: RegenerateRequest,
        character: CharacterEntity,
        config: ApiConfig,
        previousReasoningContent: String?
    ): MessageExecutionHelper.ExecutionResult? {
        return continueGenerationUseCase.regenerate(
            chatId = chatId,
            characterId = characterId,
            character = character,
            messageId = request.assistantMessageId,
            userMessageContent = request.userContent,
            config = config,
            previousReasoningContent = previousReasoningContent
        )
    }

    data class ContinueRequest(
        val assistantMessageId: Long,
        val assistantContent: String
    )

    data class RegenerateRequest(
        val assistantMessageId: Long,
        val userContent: String
    )

    private companion object {
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"
    }
}
