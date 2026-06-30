package com.tavern.lite.ui.screens.chat

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.GroupChatRepository
import com.tavern.lite.domain.port.LegacyConfigReaderPort
import com.tavern.lite.domain.usecase.SummaryUseCase

suspend fun buildChatPromptInspectorState(
    draftInput: String,
    chatId: Long,
    characterId: Long,
    character: CharacterEntity?,
    isGroupChat: Boolean,
    groupCharacters: List<CharacterEntity>,
    respondingCharacter: CharacterEntity?,
    messages: List<MessageEntity>,
    characterRepository: CharacterRepository,
    chatRepository: ChatRepository,
    groupChatRepository: GroupChatRepository,
    configReader: LegacyConfigReaderPort,
    summaryUseCase: SummaryUseCase,
    promptInspectorBuilder: PromptInspectorBuilder
): PromptInspectorState {
    val config = configReader.readConfig()
    val baseCharacter = character
        ?: characterRepository.getCharacterById(characterId)
        ?: return PromptInspectorState(error = "Character not loaded")
    val previewInput = draftInput.ifBlank {
        messages.lastOrNull { it.role == "user" }?.content ?: ""
    }
    val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength).reversed()
    val summary = summaryUseCase.getLatestSummaryText(chatId)

    return if (isGroupChat) {
        val characters = groupCharacters.ifEmpty {
            groupChatRepository.getCharactersForChatSync(chatId)
        }
        val replyCharacter = respondingCharacter ?: characters.firstOrNull() ?: baseCharacter
        promptInspectorBuilder.buildGroup(
            chatId = chatId,
            characters = characters.ifEmpty { listOf(replyCharacter) },
            respondingCharacter = replyCharacter,
            userMessage = previewInput,
            chatHistory = chatHistory,
            userName = config.userName,
            summary = summary
        )
    } else {
        promptInspectorBuilder.buildSingle(
            chatId = chatId,
            character = baseCharacter,
            userMessage = previewInput,
            chatHistory = chatHistory,
            userName = config.userName,
            summary = summary
        )
    }
}
