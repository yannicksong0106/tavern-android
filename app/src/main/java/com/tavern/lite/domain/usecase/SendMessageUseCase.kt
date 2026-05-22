package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import com.tavern.lite.network.PromptBuilder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val chatApiService: ChatApiService,
    private val worldBookRepository: WorldBookRepository,
    private val memoryAtomDao: MemoryAtomDao,
    private val memoryRepository: MemoryRepository,
    private val authorNoteDao: AuthorNoteDao,
    private val personaRepository: PersonaRepository,
    private val scriptRepository: ScriptRepository,
    private val memoryExtractionUseCase: MemoryExtractionUseCase,
) {
    // 思维链内容（DeepSeek/Qwen thinking mode），下次请求时传回
    private var lastAssistantReasoningContent: String? = null

    data class Result(
        val assistantMsgId: Long? = null,
        val fullResponse: String = "",
        val processedUserContent: String = "",
    )

    /**
     * 发送单聊消息：构建 prompt → 流式 API → 保存 → 记忆提取
     */
    suspend fun sendSingleMessage(
        chatId: Long,
        character: CharacterEntity,
        userContent: String,
        config: ApiConfig,
        replyToId: Long? = null,
    ): Result? {
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
        val persona = personaRepository.getEffectivePersona(character.id)

        val promptMessages = PromptBuilder.build(
            character = character,
            userMessage = processedContent,
            chatHistory = chatHistory.reversed(),
            worldBookEntries = worldBookEntries,
            userName = config.userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona
        )

        return executeAndSave(chatId, character.id, character.name, promptMessages, config, processedContent)
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
    ): List<Pair<Long, Result>> {
        val processedContent = if (userContent.isNotBlank()) {
            val processed = scriptRepository.applyScripts(characters.first().id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user")
            processed
        } else ""

        val results = mutableListOf<Pair<Long, Result>>()
        val characterMap = characters.associateBy { it.id }
        var chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

        for (char in characters) {
            val persona = personasafe(char.id)
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
                authorNote = authorNote
            )

            val result = executeAndSave(chatId, char.id, char.name, promptMessages, config, processedContent)
            if (result != null) {
                results.add(char.id to result)
            }

            // Reload history so next character sees this one's response
            chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
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
    ): Result? {
        val processedContent = if (userContent.isNotBlank()) {
            val processed = scriptRepository.applyScripts(targetCharacter.id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user")
            processed
        } else ""

        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        val persona = personasafe(targetCharacter.id)
        val characterMap = characters.associateBy { it.id }

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
            authorNote = authorNote
        )

        return executeAndSave(chatId, targetCharacter.id, targetCharacter.name, promptMessages, config, userContent)
    }

    /**
     * 继续生成：追加内容到最后一条 assistant 消息
     */
    suspend fun continueGeneration(
        chatId: Long,
        characterId: Long,
        character: CharacterEntity,
        lastAssistantMsgId: Long,
        lastAssistantContent: String,
        config: ApiConfig,
    ): Result? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

        val worldBookEntries = if (character.worldBookId != null) {
            val lastUserMsg = chatRepository.getRecentMessages(chatId, 100)
                .lastOrNull { it.role == "user" }
            worldBookRepository.matchEntriesRecursive(character.worldBookId, lastUserMsg?.content ?: "")
        } else emptyList()

        val memoryAtoms = memoryAtomDao.getRelevantAtoms(characterId, 10)
        memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(characterId, "")
        } else emptyList()
        val authorNote = authorNoteDao.getAuthorNoteSync(characterId)
        val persona = personasafe(characterId)

        val promptMessages = PromptBuilder.build(
            character = character,
            userMessage = "",
            chatHistory = chatHistory.reversed(),
            worldBookEntries = worldBookEntries,
            userName = config.userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona
        )

        val responseBuffer = StringBuilder()
        chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
            responseBuffer.append(chunk)
        }
        lastAssistantReasoningContent = chatApiService.lastReasoningContent

        val newContent = responseBuffer.toString()
        if (newContent.isBlank()) return null

        chatRepository.appendToMessage(lastAssistantMsgId, newContent)

        val fullContent = lastAssistantContent + newContent
        val processedReply = scriptRepository.applyScripts(characterId, fullContent, 1)
        if (processedReply != fullContent) {
            chatRepository.updateMessageContent(lastAssistantMsgId, processedReply)
        }

        memoryExtractionUseCase.extractIfNeeded(chatId, characterId, character.name, "", config)

        return Result(assistantMsgId = lastAssistantMsgId, fullResponse = newContent, processedUserContent = "")
    }

    /**
     * 重新生成：生成新回复并添加为 swipe
     */
    suspend fun regenerate(
        chatId: Long,
        characterId: Long,
        character: CharacterEntity,
        messageId: Long,
        userMessageContent: String,
        config: ApiConfig,
    ): Result? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

        val worldBookEntries = if (character.worldBookId != null) {
            worldBookRepository.matchEntriesRecursive(character.worldBookId, userMessageContent)
        } else emptyList()

        val memoryAtoms = memoryAtomDao.getRelevantAtoms(characterId, 10)
        memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(characterId, userMessageContent)
        } else emptyList()

        val authorNote = authorNoteDao.getAuthorNoteSync(characterId)
        val persona = personasafe(characterId)

        val promptMessages = PromptBuilder.build(
            character = character,
            userMessage = userMessageContent,
            chatHistory = chatHistory.reversed(),
            worldBookEntries = worldBookEntries,
            userName = config.userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona
        )

        val responseBuffer = StringBuilder()
        chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
            responseBuffer.append(chunk)
        }
        lastAssistantReasoningContent = chatApiService.lastReasoningContent

        val newContent = responseBuffer.toString()
        if (newContent.isBlank()) return null

        chatRepository.addSwipe(messageId, newContent)
        chatRepository.updateMessageContent(messageId, newContent)

        val processedReply = scriptRepository.applyScripts(characterId, newContent, 1)
        if (processedReply != newContent) {
            chatRepository.updateMessageContent(messageId, processedReply)
        }

        return Result(assistantMsgId = messageId, fullResponse = newContent, processedUserContent = userMessageContent)
    }

    /**
     * 构建主动对话 prompt 并发送（单聊）
     */
    suspend fun sendProactiveMessage(
        chatId: Long,
        character: CharacterEntity,
        config: ApiConfig,
    ): Result? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        if (chatHistory.isEmpty()) return null

        val persona = personasafe(character.id)

        val promptMessages = PromptBuilder.buildProactive(
            character = character,
            chatHistory = chatHistory.reversed(),
            userName = config.userName,
            persona = persona
        )

        return executeAndSave(chatId, character.id, character.name, promptMessages, config, "")
    }

    /**
     * 构建群聊主动对话 prompt 并发送
     */
    suspend fun sendProactiveGroupMessage(
        chatId: Long,
        characters: List<CharacterEntity>,
        character: CharacterEntity,
        config: ApiConfig,
    ): Result? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        if (chatHistory.isEmpty()) return null

        val persona = personasafe(character.id)
        val characterMap = characters.associateBy { it.id }

        val promptMessages = PromptBuilder.buildGroupProactive(
            characters = characters,
            respondingCharacter = character,
            chatHistory = chatHistory.reversed(),
            characterMap = characterMap,
            userName = config.userName,
            persona = persona
        )

        return executeAndSave(chatId, character.id, character.name, promptMessages, config, "")
    }

    // ==================== 内部方法 ====================

    /**
     * 核心执行流程：流式 API → 清理前缀 → 保存 → 正则脚本 → 记忆提取
     */
    private suspend fun executeAndSave(
        chatId: Long,
        characterId: Long,
        characterName: String,
        promptMessages: List<ChatMessage>,
        config: ApiConfig,
        processedUserContent: String,
    ): Result? {
        val responseBuffer = StringBuilder()
        chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
            responseBuffer.append(chunk)
        }
        lastAssistantReasoningContent = chatApiService.lastReasoningContent

        var fullResponse = responseBuffer.toString()
        if (fullResponse.isBlank()) return null

        // 清理角色名前缀（群聊常见）
        val cleanContent = cleanCharacterPrefix(fullResponse, characterName)

        if (cleanContent.isBlank()) return null

        val assistantMsgId = chatRepository.sendMessage(chatId, cleanContent, "assistant", characterId)

        val processedReply = scriptRepository.applyScripts(characterId, cleanContent, 1)
        if (processedReply != cleanContent) {
            chatRepository.updateMessageContent(assistantMsgId, processedReply)
        }

        memoryExtractionUseCase.extractIfNeeded(chatId, characterId, characterName, processedUserContent, config)

        return Result(
            assistantMsgId = assistantMsgId,
            fullResponse = cleanContent,
            processedUserContent = processedUserContent
        )
    }

    /**
     * 清理角色名前缀，如 "[Alice]: 你好" → "你好"
     */
    internal fun cleanCharacterPrefix(response: String, charName: String): String {
        val trimmed = response.trim()
        val prefix = "[$charName]"
        if (!trimmed.startsWith(prefix)) return trimmed
        val afterPrefix = trimmed.substring(prefix.length)
        var i = 0
        while (i < afterPrefix.length && (afterPrefix[i] == ':' || afterPrefix[i] == '：' || afterPrefix[i] == ' ' || afterPrefix[i] == '\t')) {
            i++
        }
        return afterPrefix.substring(i).trim()
    }

    /**
     * 为 promptMessages 中最后一条 assistant 消息附加 reasoning_content，
     * 满足思维链模型（DeepSeek/Qwen）的 API 要求。
     */
    internal fun attachReasoningContent(messages: List<ChatMessage>): List<ChatMessage> {
        val reasoning = lastAssistantReasoningContent ?: return messages
        for (i in messages.indices.reversed()) {
            if (messages[i].role == "assistant") {
                return messages.toMutableList().also {
                    it[i] = it[i].copy(reasoningContent = reasoning)
                }
            }
        }
        return messages
    }

    private suspend fun personasafe(characterId: Long) =
        try { personaRepository.getEffectivePersona(characterId) } catch (_: Exception) { null }
}
