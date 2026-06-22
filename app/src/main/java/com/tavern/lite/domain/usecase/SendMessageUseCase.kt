package com.tavern.lite.domain.usecase

import android.util.Log
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.AuthorNoteRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.network.PromptBuilder
import com.tavern.lite.network.WebSearchResult
import com.tavern.lite.network.WebSearchService
import com.tavern.lite.util.ImageUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val worldBookRepository: WorldBookRepository,
    private val memoryRepository: MemoryRepository,
    private val authorNoteRepository: AuthorNoteRepository,
    private val scriptRepository: ScriptRepository,
    private val presetRepository: PresetRepository,
    private val helper: MessageExecutionHelper,
    private val summaryUseCase: SummaryUseCase,
    private val webSearchService: WebSearchService,
    private val settingsStore: SettingsStore,
) {
    // Singleton 作用域，用于 fire-and-forget 后台任务（自动摘要）
    private val summaryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * 发送单聊消息：构建 prompt → 流式 API → 保存 → 记忆提取
     */
    suspend fun sendSingleMessage(
        chatId: Long,
        character: CharacterEntity,
        userContent: String,
        config: ApiConfig,
        replyToId: Long? = null,
        imagePaths: List<String> = emptyList(),
    ): MessageExecutionHelper.ExecutionResult? {
        val processedContent = if (userContent.isNotBlank() || imagePaths.isNotEmpty()) {
            val processed = scriptRepository.applyScripts(character.id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user", replyToId = replyToId, imagePaths = imagePaths)
            processed
        } else ""

        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        val worldBookEntries = if (character.worldBookId != null) {
            worldBookRepository.matchEntriesRecursive(character.worldBookId, processedContent)
        } else emptyList()

        val memoryAtoms = memoryRepository.getRelevantAtoms(character.id, 10)
        memoryRepository.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(character.id, processedContent)
        } else emptyList()

        val authorNote = authorNoteRepository.getAuthorNoteSync(character.id)
        val persona = helper.personasafe(character.id)
        val preset = presetRepository.resolveEffectivePreset(chatId, character.id)
        val summary = summaryUseCase.getLatestSummaryText(chatId)
        val searchResults = performSearchIfNeeded(processedContent)

        val imageUrls = imagePaths.mapNotNull { ImageUtils.fileToDataUri(File(it)) }

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
            preset = preset,
            imageUrls = imageUrls,
            summary = summary,
            searchResults = searchResults
        )

        val result = helper.executeAndSave(chatId, character.id, character.name, promptMessages, config, processedContent)

        // 异步触发摘要生成（不影响主流程）
        if (result != null) {
            tryTriggerSummary(chatId, config, character.name)
        }

        return result
    }

    /**
     * 检测 /search 命令或自动搜索，返回搜索结果
     */
    private suspend fun performSearchIfNeeded(content: String): List<WebSearchResult> {
        return try {
            val searchConfig = settingsStore.webSearchConfigFlow.first()
            if (!searchConfig.enabled) return emptyList()

            val query = when {
                content.startsWith("/search ", ignoreCase = true) -> content.removePrefix("/search ").trim()
                content.startsWith("/搜索 ", ignoreCase = true) -> content.removePrefix("/搜索 ").trim()
                searchConfig.autoSearch && content.isNotBlank() -> content
                else -> return emptyList()
            }
            if (query.isBlank()) return emptyList()

            webSearchService.search(query, searchConfig)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("SendMessageUseCase", "搜索失败: ${e.message}")
            emptyList()
        }
    }

    private fun tryTriggerSummary(chatId: Long, config: ApiConfig, characterName: String) {
        summaryScope.launch {
            try {
                if (summaryUseCase.shouldGenerateSummary(chatId)) {
                    summaryUseCase.generateSummary(chatId, config, characterName)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("SendMessageUseCase", "自动摘要失败: ${e.message}")
            }
        }
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
        imagePaths: List<String> = emptyList(),
    ): List<Pair<Long, MessageExecutionHelper.ExecutionResult>> {
        val processedContent = if (userContent.isNotBlank() || imagePaths.isNotEmpty()) {
            val processed = scriptRepository.applyScripts(characters.first().id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user", imagePaths = imagePaths)
            processed
        } else ""

        val imageUrls = imagePaths.mapNotNull { ImageUtils.fileToDataUri(File(it)) }

        val results = mutableListOf<Pair<Long, MessageExecutionHelper.ExecutionResult>>()
        val characterMap = characters.associateBy { it.id }
        var chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        val preset = presetRepository.resolveEffectivePreset(chatId, characters.first().id)
        val summary = summaryUseCase.getLatestSummaryText(chatId)
        val searchResults = performSearchIfNeeded(processedContent)

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

            val memoryAtoms = memoryRepository.getRelevantAtoms(char.id, 10)
            memoryRepository.touchAtoms(memoryAtoms.map { it.id })
            val memories = if (memoryAtoms.isEmpty()) {
                memoryRepository.getRelevantMemories(char.id, processedContent)
            } else emptyList()

            val authorNote = authorNoteRepository.getAuthorNoteSync(char.id)

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
                preset = preset,
                imageUrls = imageUrls,
                summary = summary,
                searchResults = searchResults
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

        // 异步触发摘要生成
        if (results.isNotEmpty()) {
            tryTriggerSummary(chatId, config, characters.first().name)
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
        imagePaths: List<String> = emptyList(),
    ): MessageExecutionHelper.ExecutionResult? {
        val processedContent = if (userContent.isNotBlank() || imagePaths.isNotEmpty()) {
            val processed = scriptRepository.applyScripts(targetCharacter.id, userContent, 0)
            chatRepository.sendMessage(chatId, processed, "user", imagePaths = imagePaths)
            processed
        } else ""

        val imageUrls = imagePaths.mapNotNull { ImageUtils.fileToDataUri(File(it)) }

        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        val persona = helper.personasafe(targetCharacter.id)
        val characterMap = characters.associateBy { it.id }
        val preset = presetRepository.resolveEffectivePreset(chatId, targetCharacter.id)
        val summary = summaryUseCase.getLatestSummaryText(chatId)
        val searchResults = performSearchIfNeeded(processedContent)

        val worldBookEntries = if (targetCharacter.worldBookId != null) {
            worldBookRepository.matchEntriesRecursive(targetCharacter.worldBookId, userContent)
        } else emptyList()

        val memoryAtoms = memoryRepository.getRelevantAtoms(targetCharacter.id, 10)
        memoryRepository.touchAtoms(memoryAtoms.map { it.id })
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(targetCharacter.id, userContent)
        } else emptyList()

        val authorNote = authorNoteRepository.getAuthorNoteSync(targetCharacter.id)

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
            preset = preset,
            imageUrls = imageUrls,
            summary = summary,
            searchResults = searchResults
        )

        val result = helper.executeAndSave(chatId, targetCharacter.id, targetCharacter.name, promptMessages, config, userContent)

        if (result != null) {
            tryTriggerSummary(chatId, config, targetCharacter.name)
        }

        return result
    }
}
