package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.network.PromptBuilder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val worldBookRepository: WorldBookRepository,
    private val memoryAtomDao: MemoryAtomDao,
    private val memoryRepository: MemoryRepository,
    private val authorNoteDao: AuthorNoteDao,
    private val scriptRepository: ScriptRepository,
    private val presetRepository: PresetRepository,
    private val helper: MessageExecutionHelper,
) {
    /**
     * 发送单聊消息：构建 prompt → 流式 API → 保存 → 记忆提取
     */
    suspend fun sendSingleMessage(
        chatId: Long,
        character: CharacterEntity,
        userContent: String,
        config: ApiConfig,
        replyToId: Long? = null,
    ): MessageExecutionHelper.ExecutionResult? {
        val processedContent = if (userContent.isNotBlank()) {
            val processed = scriptRepository.applyScripts(character.id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user", replyToId = replyToId)
            processed
        } else ""

        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        val worldBookEntries = if (character.worldBookId != null) {
            worldBookRepository.matchEntriesRecursive(character.worldBookId, processedContent)
        } else emptyList()

        val memoryAtoms = memoryAtomDao.getRelevantAtoms(character.id, 10)
        memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(character.id, processedContent)
        } else emptyList()

        val authorNote = authorNoteDao.getAuthorNoteSync(character.id)
        val persona = helper.personasafe(character.id)
        val preset = presetRepository.resolveEffectivePreset(chatId, character.id)

        val promptMessages = PromptBuilder.build(
            character = character,
            userMessage = processedContent,
            chatHistory = chatHistory.reversed(),
            worldBookEntries = worldBookEntries,
            userName = config.userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona,
            preset = preset
        )

        return helper.executeAndSave(chatId, character.id, character.name, promptMessages, config, processedContent)
    }

    /**
     * 发送群聊消息：让一组角色依次回复
     * 返回每个角色的 Result（characterId → Result）
     */
    suspend fun sendGroupMessage(
        chatId: Long,
        characters: List<CharacterEntity>,
        userContent: String,
        config: ApiConfig,
    ): List<Pair<Long, MessageExecutionHelper.ExecutionResult>> {
        val processedContent = if (userContent.isNotBlank()) {
            val processed = scriptRepository.applyScripts(characters.first().id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user")
            processed
        } else ""

        val results = mutableListOf<Pair<Long, MessageExecutionHelper.ExecutionResult>>()
        val characterMap = characters.associateBy { it.id }
        var chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        val preset = presetRepository.resolveEffectivePreset(chatId, characters.first().id)

        // Prepend the user message we just saved (it may not appear in getRecentMessages yet due to timing)
        if (processedContent.isNotBlank() && chatHistory.none { it.role == "user" && it.content == processedContent }) {
            val userMsg = MessageEntity(chatId = chatId, role = "user", content = processedContent)
            chatHistory = listOf(userMsg) + chatHistory
        }

        for (char in characters) {
            val persona = helper.personasafe(char.id)
            val worldBookEntries = if (char.worldBookId != null) {
                worldBookRepository.matchEntriesRecursive(char.worldBookId, processedContent)
            } else emptyList()

            val memoryAtoms = memoryAtomDao.getRelevantAtoms(char.id, 10)
            memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
            val memories = if (memoryAtoms.isEmpty()) {
                memoryRepository.getRelevantMemories(char.id, processedContent)
            } else emptyList()

            val authorNote = authorNoteDao.getAuthorNoteSync(char.id)

            val promptMessages = PromptBuilder.buildGroupChat(
                characters = characters,
                respondingCharacter = char,
                userMessage = processedContent,
                chatHistory = chatHistory.reversed(),
                characterMap = characterMap,
                worldBookEntries = worldBookEntries,
                userName = config.userName,
                memories = memories,
                memoryAtoms = memoryAtoms,
                persona = persona,
                authorNote = authorNote,
                preset = preset
            )

            val result = helper.executeAndSave(chatId, char.id, char.name, promptMessages, config, processedContent)
            if (result != null) {
                results.add(char.id to result)
            }

            // Append saved message to in-memory list instead of full DB reload (avoids N+1 queries)
            if (result?.assistantMsgId != null) {
                val savedMsg = chatRepository.getMessageById(result.assistantMsgId)
                if (savedMsg != null) {
                    chatHistory = listOf(savedMsg) + chatHistory
                }
            }
        }

        return results
    }

    /**
     * 发送定向消息：只让指定角色回复（@ 提及）
     */
    suspend fun sendDirectMessage(
        chatId: Long,
        characters: List<CharacterEntity>,
        targetCharacter: CharacterEntity,
        userContent: String,
        config: ApiConfig,
    ): MessageExecutionHelper.ExecutionResult? {
        val processedContent = if (userContent.isNotBlank()) {
            val processed = scriptRepository.applyScripts(targetCharacter.id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user")
            processed
        } else ""

        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        val persona = helper.personasafe(targetCharacter.id)
        val characterMap = characters.associateBy { it.id }
        val preset = presetRepository.resolveEffectivePreset(chatId, targetCharacter.id)

        val worldBookEntries = if (targetCharacter.worldBookId != null) {
            worldBookRepository.matchEntriesRecursive(targetCharacter.worldBookId, userContent)
        } else emptyList()

        val memoryAtoms = memoryAtomDao.getRelevantAtoms(targetCharacter.id, 10)
        memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(targetCharacter.id, userContent)
        } else emptyList()

        val authorNote = authorNoteDao.getAuthorNoteSync(targetCharacter.id)

        val promptMessages = PromptBuilder.buildGroupChat(
            characters = characters,
            respondingCharacter = targetCharacter,
            userMessage = userContent,
            chatHistory = chatHistory.reversed(),
            characterMap = characterMap,
            worldBookEntries = worldBookEntries,
            userName = config.userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            persona = persona,
            authorNote = authorNote,
            preset = preset
        )

        return helper.executeAndSave(chatId, targetCharacter.id, targetCharacter.name, promptMessages, config, userContent)
    }
}
