package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.network.PromptBuilder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val presetRepository: PresetRepository,
    private val helper: MessageExecutionHelper,
) {
    /**
     * 构建主动对话 prompt 并发送（单聊）
     */
    suspend fun sendProactiveMessage(
        chatId: Long,
        character: CharacterEntity,
        config: ApiConfig,
    ): MessageExecutionHelper.ExecutionResult? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        if (chatHistory.isEmpty()) return null

        val persona = helper.personasafe(character.id)
        val preset = presetRepository.resolveEffectivePreset(chatId, character.id)

        val promptMessages = PromptBuilder.buildProactive(
            character = character,
            chatHistory = chatHistory.reversed(),
            userName = config.userName,
            persona = persona,
            preset = preset
        )

        return helper.executeAndSave(chatId, character.id, character.name, promptMessages, config, "")
    }

    /**
     * 构建群聊主动对话 prompt 并发送
     */
    suspend fun sendProactiveGroupMessage(
        chatId: Long,
        characters: List<CharacterEntity>,
        character: CharacterEntity,
        config: ApiConfig,
    ): MessageExecutionHelper.ExecutionResult? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        if (chatHistory.isEmpty()) return null

        val persona = helper.personasafe(character.id)
        val characterMap = characters.associateBy { it.id }
        val preset = presetRepository.resolveEffectivePreset(chatId, character.id)

        val promptMessages = PromptBuilder.buildGroupProactive(
            characters = characters,
            respondingCharacter = character,
            chatHistory = chatHistory.reversed(),
            characterMap = characterMap,
            userName = config.userName,
            persona = persona,
            preset = preset
        )

        return helper.executeAndSave(chatId, character.id, character.name, promptMessages, config, "")
    }
}
