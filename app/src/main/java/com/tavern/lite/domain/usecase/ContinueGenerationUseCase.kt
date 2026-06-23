package com.tavern.lite.domain.usecase

import android.util.Log
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.data.repository.AuthorNoteRepository
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.port.PromptBuilderPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinueGenerationUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val worldBookRepository: WorldBookRepository,
    private val memoryRepository: MemoryRepository,
    private val authorNoteRepository: AuthorNoteRepository,
    private val scriptRepository: ScriptRepository,
    private val presetRepository: PresetRepository,
    private val memoryExtractionUseCase: MemoryExtractionUseCase,
    private val helper: MessageExecutionHelper,
    private val promptBuilder: PromptBuilderPort,
) {
    /**
     * 继续生成：追加内容到最后一条 assistant 消息
     * @param previousReasoningContent 上一轮的思维链内容，传回给 API 以维持上下文
     */
    suspend fun continueGeneration(
        chatId: Long,
        characterId: Long,
        character: CharacterEntity,
        lastAssistantMsgId: Long,
        lastAssistantContent: String,
        config: ApiConfig,
        previousReasoningContent: String? = null,
    ): MessageExecutionHelper.ExecutionResult? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

        val worldBookEntries = if (character.worldBookId != null) {
            val lastUserMsg = chatRepository.getLastUserMessage(chatId)
            worldBookRepository.matchEntriesRecursive(character.worldBookId, lastUserMsg?.content ?: "")
        } else emptyList()

        val memoryAtoms = memoryRepository.getRelevantAtoms(characterId, 10)
        memoryRepository.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(characterId, "")
        } else emptyList()
        val authorNote = authorNoteRepository.getAuthorNoteSync(characterId)
        val persona = helper.personasafe(characterId)
        val preset = presetRepository.resolveEffectivePreset(chatId, characterId)

        val promptConfig = com.tavern.lite.domain.model.PromptConfig(
            character = character,
            userMessage = "",
            chatHistory = chatHistory.reversed(),
            worldBookEntries = worldBookEntries,
            userName = config.userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona,
            preset = preset
        )
        val promptMessages = promptBuilder.build(promptConfig)

        val responseBuffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        try {
            val messagesWithReasoning = helper.attachReasoningContent(promptMessages, previousReasoningContent)
            helper.chatApiService.streamChatWithMetadata(messagesWithReasoning, config).collect { chunk ->
                responseBuffer.append(chunk.content)
                chunk.reasoningContent?.let { reasoningBuffer.append(it) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("ContinueGeneration", "continueGeneration failed", e)
            val errorMsg = when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.net.SocketException -> "[网络连接失败，请检查网络设置]"
                is java.io.IOException -> "[网络连接异常: ${e.message?.take(50) ?: "未知错误"}]"
                else -> "[生成失败: ${e.message?.take(80) ?: "未知错误"}]"
            }
            chatRepository.appendToMessage(lastAssistantMsgId, errorMsg)
            return null
        }
        val reasoningContent = reasoningBuffer.takeIf { it.isNotEmpty() }?.toString()

        val newContent = responseBuffer.toString()
        if (newContent.isBlank()) return null

        chatRepository.appendToMessage(lastAssistantMsgId, newContent)

        val fullContent = lastAssistantContent + newContent
        val processedReply = scriptRepository.applyScripts(characterId, fullContent, 1)
        if (processedReply != fullContent) {
            chatRepository.updateMessageContent(lastAssistantMsgId, processedReply)
        }

        memoryExtractionUseCase.extractIfNeeded(chatId, characterId, character.name, "", config)

        return MessageExecutionHelper.ExecutionResult(
            assistantMsgId = lastAssistantMsgId,
            fullResponse = newContent,
            processedUserContent = "",
            reasoningContent = reasoningContent
        )
    }

    /**
     * 重新生成：生成新回复并添加为 swipe
     * @param previousReasoningContent 上一轮的思维链内容，传回给 API 以维持上下文
     */
    suspend fun regenerate(
        chatId: Long,
        characterId: Long,
        character: CharacterEntity,
        messageId: Long,
        userMessageContent: String,
        config: ApiConfig,
        previousReasoningContent: String? = null,
    ): MessageExecutionHelper.ExecutionResult? {
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

        val worldBookEntries = if (character.worldBookId != null) {
            worldBookRepository.matchEntriesRecursive(character.worldBookId, userMessageContent)
        } else emptyList()

        val memoryAtoms = memoryRepository.getRelevantAtoms(characterId, 10)
        memoryRepository.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(characterId, userMessageContent)
        } else emptyList()

        val authorNote = authorNoteRepository.getAuthorNoteSync(characterId)
        val persona = helper.personasafe(characterId)
        val preset = presetRepository.resolveEffectivePreset(chatId, characterId)

        val promptConfig = com.tavern.lite.domain.model.PromptConfig(
            character = character,
            userMessage = userMessageContent,
            chatHistory = chatHistory.reversed(),
            worldBookEntries = worldBookEntries,
            userName = config.userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona,
            preset = preset
        )
        val promptMessages = promptBuilder.build(promptConfig)

        val responseBuffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        try {
            val messagesWithReasoning = helper.attachReasoningContent(promptMessages, previousReasoningContent)
            helper.chatApiService.streamChatWithMetadata(messagesWithReasoning, config).collect { chunk ->
                responseBuffer.append(chunk.content)
                chunk.reasoningContent?.let { reasoningBuffer.append(it) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("ContinueGeneration", "regenerate failed", e)
            val errorMsg = when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.net.SocketException -> "[网络连接失败，请检查网络设置]"
                is java.io.IOException -> "[网络连接异常: ${e.message?.take(50) ?: "未知错误"}]"
                else -> "[生成失败: ${e.message?.take(80) ?: "未知错误"}]"
            }
            chatRepository.addSwipe(messageId, errorMsg)
            chatRepository.updateMessageContent(messageId, errorMsg)
            return null
        }
        val reasoningContent = reasoningBuffer.takeIf { it.isNotEmpty() }?.toString()

        val newContent = responseBuffer.toString()
        if (newContent.isBlank()) return null

        chatRepository.addSwipe(messageId, newContent)
        chatRepository.updateMessageContent(messageId, newContent)

        val processedReply = scriptRepository.applyScripts(characterId, newContent, 1)
        if (processedReply != newContent) {
            chatRepository.updateMessageContent(messageId, processedReply)
