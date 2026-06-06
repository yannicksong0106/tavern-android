package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.domain.usecase.ContinueGenerationUseCase
import com.tavern.lite.domain.usecase.MemoryExtractionUseCase
import com.tavern.lite.domain.usecase.ProactiveDialogueUseCase
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.domain.usecase.SendMessageUseCase
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.ImageGenerationService
import com.tavern.lite.util.SwipeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 封装所有流式对话逻辑：发送消息、继续生成、重新生成、主动对话等。
 *
 * 从 ChatViewModel 中提取，职责单一化。
 */
class ChatStreamingManager(
    private val chatId: Long,
    private val characterId: Long,
    private val chatRepository: ChatRepository,
    private val apiConfigStore: ApiConfigStore,
    private val sendMessageUseCase: SendMessageUseCase,
    private val continueGenerationUseCase: ContinueGenerationUseCase,
    private val proactiveMessageUseCase: ProactiveMessageUseCase,
    private val proactiveDialogueUseCase: ProactiveDialogueUseCase,
    private val imageGenerationService: ImageGenerationService,
    private val scope: CoroutineScope
) {
    // ==================== 状态 ====================

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var streamingJob: Job? = null
    private val streamingMutex = Mutex()
    @Volatile private var wasCancelled = false
    @Volatile private var isProactiveMessage = false
    @Volatile private var lastReasoningContent: String? = null

    // Round-robin 索引
    private var roundRobinIndex = 0

    // ==================== 外部只读状态（由 ViewModel 提供） ====================

    /** 获取当前角色 */
    var characterProvider: () -> CharacterEntity? = { null }

    /** 获取群聊角色列表 */
    var groupCharactersProvider: () -> List<CharacterEntity> = { emptyList() }

    /** 是否群聊 */
    var isGroupChatProvider: () -> Boolean = { false }

    /** 获取当前消息列表 */
    var messagesProvider: () -> List<MessageEntity> = { emptyList() }

    /** 获取调度策略 */
    var schedulingStrategyProvider: () -> GroupSchedulingStrategy = { GroupSchedulingStrategy.NATURAL }

    /** 获取群角色健谈度 */
    var groupCharacterChattinessProvider: () -> Map<Long, Int> = { emptyMap() }

    /** 获取发言间隔 */
    var messageIntervalProvider: () -> Long = { 1500L }

    // ==================== 回调 ====================

    /** 设置当前回复角色（群聊用） */
    var onRespondingCharacterChanged: (CharacterEntity?) -> Unit = {}

    /** AI 回复后触发表情更新 */
    var onEmotionUpdate: (String) -> Unit = {}

    /** 发送 toast 消息 */
    var onToast: suspend (String) -> Unit = {}

    // ==================== 公开方法 ====================

    fun sendMessage(content: String, imagePaths: List<String> = emptyList()) {
        if ((content.isBlank() && imagePaths.isEmpty()) || _isGenerating.value) return

        if (isGroupChatProvider()) {
            val atResult = proactiveDialogueUseCase.parseAtMention(content, groupCharactersProvider())
            if (atResult != null) {
                sendDirectMessage(atResult.second, atResult.first, imagePaths)
            } else {
                sendGroupChatMessage(content, imagePaths)
            }
        } else {
            sendSingleChatMessage(content, imagePaths)
        }
    }

    fun stopGeneration() {
        wasCancelled = true
        streamingJob?.cancel()
        streamingJob = null
        _isGenerating.value = false
    }

    fun continueGeneration() {
        val lastMsg = messagesProvider().lastOrNull { it.role == "assistant" }
        if (lastMsg == null || _isGenerating.value) return

        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val character = characterProvider() ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    val result = continueGenerationUseCase.continueGeneration(
                        chatId, characterId, character, lastMsg.id, lastMsg.content, config,
                        previousReasoningContent = lastReasoningContent
                    )
                    if (result != null) {
                        lastReasoningContent = result.reasoningContent
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    streamingJob = null
                }
            }
        }
    }

    fun regenerate(messageId: Long) {
        val allMessages = messagesProvider()
        val msg = allMessages.find { it.id == messageId }
        if (msg == null || msg.role != "assistant") return

        val msgIndex = allMessages.indexOfFirst { it.id == messageId }
        val userMsg = allMessages.take(msgIndex).lastOrNull { it.role == "user" }
        if (userMsg == null) return

        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val character = characterProvider() ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    val result = continueGenerationUseCase.regenerate(
                        chatId, characterId, character, messageId, userMsg.content, config,
                        previousReasoningContent = lastReasoningContent
                    )
                    if (result != null) {
                        lastReasoningContent = result.reasoningContent
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    streamingJob = null
                }
            }
        }
    }

    fun resendUserMessage(messageId: Long) {
        val allMessages = messagesProvider()
        val msg = allMessages.find { it.id == messageId }
        if (msg == null || msg.role != "user" || _isGenerating.value) return

        val msgIndex = allMessages.indexOfFirst { it.id == messageId }
        val content = msg.content

        scope.launch {
            for (i in msgIndex until allMessages.size) {
                chatRepository.deleteMessage(allMessages[i].id)
            }
            sendMessage(content)
        }
    }

    fun generateImage(prompt: String) {
        if (prompt.isBlank() || _isGenerating.value) return

        scope.launch {
            _isGenerating.value = true
            try {
                val config = apiConfigStore.configFlow.first()

                val imagePath = imageGenerationService.generateImage(prompt, config)
                if (imagePath != null) {
                    chatRepository.sendMessage(
                        chatId = chatId,
                        content = "/imagine $prompt",
                        role = "user",
                        imagePaths = listOf(imagePath)
                    )
                    sendSingleChatMessage("", listOf(imagePath))
                } else {
                    onToast("图片生成失败，请检查 OpenAI API 配置")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onToast(classifyError(e))
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun triggerProactiveIfNeeded() {
        val currentMessages = messagesProvider()
        if (_isGenerating.value || isProactiveMessage) return

        val lastMsg = currentMessages.lastOrNull() ?: return

        if (lastMsg.role == "user") {
            if (isGroupChatProvider()) {
                sendGroupChatMessage("")
            } else {
                sendSingleChatMessage("")
            }
        } else if (isGroupChatProvider() && lastMsg.role == "assistant") {
            val characters = groupCharactersProvider()
            val lastCharIndex = characters.indexOfFirst { it.id == lastMsg.characterId }
            if (lastCharIndex >= 0 && lastCharIndex < characters.size - 1) {
                val nextChar = characters[lastCharIndex + 1]
                sendDirectMessage("", nextChar)
            }
        }
    }

    fun cancel() {
        streamingJob?.cancel()
    }

    // ==================== 内部方法 ====================

    private fun sendSingleChatMessage(content: String, imagePaths: List<String> = emptyList()) {
        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val character = characterProvider() ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    val result = sendMessageUseCase.sendSingleMessage(chatId, character, content, config, null, imagePaths)
                    if (result != null) {
                        lastReasoningContent = result.reasoningContent
                    }
                    if (result?.assistantMsgId != null && !wasCancelled) {
                        val assistantMsg = chatRepository.getMessageById(result.assistantMsgId)
                        if (assistantMsg != null) {
                            onEmotionUpdate(assistantMsg.content)
                        }
                        splitIntoMultipleMessages(result.assistantMsgId)
                        scheduleProactiveDialogue()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    streamingJob = null
                }
            }
        }
    }

    private fun sendGroupChatMessage(content: String, imagePaths: List<String> = emptyList()) {
        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val characters = groupCharactersProvider()
                    if (characters.isEmpty()) return@withLock
                    val config = apiConfigStore.configFlow.first()

                    val respondingChars = selectRespondingCharacters(characters)

                    val intervalMs = messageIntervalProvider()
                    val results = sendMessageUseCase.sendGroupMessage(chatId, respondingChars, content, config, imagePaths)
                    for ((charId, result) in results) {
                        if (wasCancelled) break
                        onRespondingCharacterChanged(characters.find { it.id == charId })
                        if (result.assistantMsgId != null) {
                            val assistantMsg = chatRepository.getMessageById(result.assistantMsgId)
                            if (assistantMsg != null) {
                                onEmotionUpdate(assistantMsg.content)
                            }
                            splitIntoMultipleMessages(result.assistantMsgId)
                        }
                        if (charId != results.last().first && !wasCancelled) {
                            val jitter = random.nextLong((intervalMs * 0.3).toLong())
                            delay(intervalMs + jitter)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    onRespondingCharacterChanged(null)
                    streamingJob = null
                    if (!wasCancelled) {
                        scheduleGroupProactiveDialogue()
                    }
                }
            }
        }
    }

    private fun selectRespondingCharacters(characters: List<CharacterEntity>): List<CharacterEntity> {
        return when (schedulingStrategyProvider()) {
            GroupSchedulingStrategy.NATURAL -> {
                characters.filter { char ->
                    val chattiness = groupCharacterChattinessProvider()[char.id] ?: char.chattiness
                    val responseChance = 0.5 + (chattiness / 100.0) * 0.5
                    random.nextDouble() < responseChance
                }.ifEmpty { listOf(characters.random()) }
            }
            GroupSchedulingStrategy.LIST_ORDER -> characters
            GroupSchedulingStrategy.ROUND_ROBIN -> {
                val char = characters[roundRobinIndex % characters.size]
                roundRobinIndex = (roundRobinIndex + 1) % characters.size
                listOf(char)
            }
        }
    }

    private fun sendDirectMessage(content: String, targetCharacter: CharacterEntity, imagePaths: List<String> = emptyList()) {
        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                onRespondingCharacterChanged(targetCharacter)
                try {
                    val characters = groupCharactersProvider()
                    val config = apiConfigStore.configFlow.first()

                    val result = sendMessageUseCase.sendDirectMessage(chatId, characters, targetCharacter, content, config, imagePaths)
                    if (result?.assistantMsgId != null && !wasCancelled) {
                        splitIntoMultipleMessages(result.assistantMsgId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    onRespondingCharacterChanged(null)
                    streamingJob = null
                }
            }
        }
    }

    // ==================== 主动对话 ====================

    private fun scheduleProactiveDialogue() {
        if (isGroupChatProvider() || isProactiveMessage) return
        val character = characterProvider() ?: return

        val delayMs = proactiveDialogueUseCase.shouldScheduleProactive(character.chattiness) ?: return

        scope.launch {
            delay(delayMs)
            if (!_isGenerating.value) {
                sendProactiveSingleMessage()
            }
        }
    }

    private fun sendProactiveSingleMessage() {
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                isProactiveMessage = true
                try {
                    val character = characterProvider() ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    val result = proactiveMessageUseCase.sendProactiveMessage(chatId, character, config)
                    if (result?.assistantMsgId != null) {
                        splitIntoMultipleMessages(result.assistantMsgId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    isProactiveMessage = false
                    streamingJob = null
                }
            }
        }
    }

    private fun scheduleGroupProactiveDialogue() {
        if (!isGroupChatProvider() || isProactiveMessage) return
        val characters = groupCharactersProvider()
        if (characters.isEmpty()) return

        val delayMs = proactiveDialogueUseCase.shouldScheduleGroupProactive(characters) ?: return

        scope.launch {
            delay(delayMs)
            if (!_isGenerating.value) {
                val nextChar = proactiveDialogueUseCase.selectNextProactiveCharacter(characters)
                if (nextChar != null) {
                    sendProactiveGroupMessage(nextChar)
                }
            }
        }
    }

    private fun sendProactiveGroupMessage(character: CharacterEntity) {
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                onRespondingCharacterChanged(character)
                isProactiveMessage = true
                try {
                    val characters = groupCharactersProvider()
                    val config = apiConfigStore.configFlow.first()

                    val result = proactiveMessageUseCase.sendProactiveGroupMessage(chatId, characters, character, config)
                    if (result?.assistantMsgId != null) {
                        splitIntoMultipleMessages(result.assistantMsgId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    onRespondingCharacterChanged(null)
                    isProactiveMessage = false
                    streamingJob = null
                }
            }
        }
    }

    // ==================== 消息拆分 ====================

    private suspend fun splitIntoMultipleMessages(assistantMsgId: Long?) {
        if (assistantMsgId == null) return
        val msg = chatRepository.getMessageById(assistantMsgId) ?: return
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

    companion object {
        private val random = kotlin.random.Random.Default
        private val PARAGRAPH_SPLIT_REGEX: Regex = Regex("\n{2,}")

        private fun classifyError(e: Exception): String = when (e) {
            is UnknownHostException -> "网络连接失败，请检查网络设置"
            is SocketTimeoutException -> "请求超时，请稍后重试"
            is kotlinx.coroutines.CancellationException -> throw e
            else -> {
                val msg = e.message ?: ""
                when {
                    msg.contains("429") -> "请求过于频繁，请等待后重试"
                    msg.contains("500") || msg.contains("502") || msg.contains("503") -> "服务暂时不可用，请稍后重试"
                    else -> "错误: ${e.message}"
                }
            }
        }
    }
}
