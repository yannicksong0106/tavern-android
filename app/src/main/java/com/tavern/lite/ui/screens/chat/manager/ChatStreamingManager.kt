package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.domain.port.ImageGenerationPort
import com.tavern.lite.domain.port.LegacyConfigReaderPort
import com.tavern.lite.domain.usecase.ContinueGenerationUseCase
import com.tavern.lite.domain.usecase.ProactiveDialogueUseCase
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val configReader: LegacyConfigReaderPort,
    private val sendMessageUseCase: SendMessageUseCase,
    private val continueGenerationUseCase: ContinueGenerationUseCase,
    private val proactiveMessageUseCase: ProactiveMessageUseCase,
    private val proactiveDialogueUseCase: ProactiveDialogueUseCase,
    private val imageGenerationService: ImageGenerationPort,
    private val scope: CoroutineScope
) {
    // ==================== 状态 ====================

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var streamingJob: Job? = null
    private val streamingMutex = Mutex()
    private val assistantReplyCommitter = AssistantReplyCommitter(chatId, chatRepository)
    private val imageGenerationCoordinator = ImageGenerationCoordinator(
        chatId = chatId,
        imageGenerationService = imageGenerationService,
        sendMessageUseCase = sendMessageUseCase
    )
    private val generationContinuationCoordinator = GenerationContinuationCoordinator(
        chatId = chatId,
        characterId = characterId,
        continueGenerationUseCase = continueGenerationUseCase
    )
    private val generationSendCoordinator = GenerationSendCoordinator(
        chatId = chatId,
        sendMessageUseCase = sendMessageUseCase,
        random = random
    )
    private val generationReasoningContext = GenerationReasoningContext()
    private val proactiveDialogueCoordinator = ProactiveDialogueCoordinator(
        chatId = chatId,
        configReader = configReader,
        proactiveMessageUseCase = proactiveMessageUseCase,
        proactiveDialogueUseCase = proactiveDialogueUseCase,
        scope = scope,
        streamingMutex = streamingMutex
    )
    @Volatile private var wasCancelled = false

    init {
        proactiveDialogueCoordinator.characterProvider = { characterProvider() }
        proactiveDialogueCoordinator.groupCharactersProvider = { groupCharactersProvider() }
        proactiveDialogueCoordinator.isGroupChatProvider = { isGroupChatProvider() }
        proactiveDialogueCoordinator.isGeneratingProvider = { _isGenerating.value }
        proactiveDialogueCoordinator.onGeneratingChanged = { _isGenerating.value = it }
        proactiveDialogueCoordinator.onRespondingCharacterChanged = { onRespondingCharacterChanged(it) }
        proactiveDialogueCoordinator.onToast = { onToast(classifyError(it)) }
        proactiveDialogueCoordinator.onAssistantReplyCommit = { assistantMsgId, updateEmotion, respectCancellation ->
            commitAssistantReply(
                assistantMsgId = assistantMsgId,
                updateEmotion = updateEmotion,
                respectCancellation = respectCancellation
            )
        }
        proactiveDialogueCoordinator.onStreamingJobChanged = { streamingJob = it }
    }

    // Round-robin 索引
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

    /** 助理消息真正落库后触发 */
    var onAssistantReplyCommitted: () -> Unit = {}

    // ==================== 公开方法 ====================

    private fun launchGenerationJob(
        clearRespondingOnExit: Boolean = false,
        onFinally: (suspend () -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    block()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onToast(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    if (clearRespondingOnExit) onRespondingCharacterChanged(null)
                    streamingJob = null
                    onFinally?.invoke()
                }
            }
        }
    }

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

    fun triggerGeneration(userInput: String? = null) {
        if (_isGenerating.value) return
        val content = userInput.orEmpty()
        if (isGroupChatProvider()) {
            sendGroupChatMessage(content)
        } else {
            sendSingleChatMessage(content)
        }
    }

    fun stopGeneration() {
        wasCancelled = true
        streamingJob?.cancel()
        streamingJob = null
        _isGenerating.value = false
    }

    fun continueGeneration() {
        val request = generationContinuationCoordinator.resolveContinueRequest(messagesProvider())
        if (request == null || _isGenerating.value) return

        launchGenerationJob {
            val character = characterProvider() ?: return@launchGenerationJob
            val config = configReader.readConfig()
            val result = generationContinuationCoordinator.continueGeneration(
                request = request,
                character = character,
                config = config,
                previousReasoningContent = generationReasoningContext.previousFor(request.assistantMessageId)
            )
            if (result != null) {
                generationReasoningContext.record(result)
                onAssistantReplyCommitted()
            }
        }
    }

    fun regenerate(messageId: Long) {
        val request = generationContinuationCoordinator.resolveRegenerateRequest(messagesProvider(), messageId)
        if (request == null) return

        launchGenerationJob {
            val character = characterProvider() ?: return@launchGenerationJob
            val config = configReader.readConfig()
            val result = generationContinuationCoordinator.regenerate(
                request = request,
                character = character,
                config = config,
                previousReasoningContent = generationReasoningContext.previousFor(request.assistantMessageId)
            )
            if (result != null) {
                generationReasoningContext.record(result)
                onAssistantReplyCommitted()
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

        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val character = characterProvider() ?: return@withLock
                    val config = configReader.readConfig()

                    val generationResult = imageGenerationCoordinator.generateImageReply(
                        prompt = prompt,
                        character = character,
                        config = config,
                        isCancelled = { wasCancelled }
                    )
                    if (generationResult is ImageGenerationCoordinator.ImageGenerationResult.Success) {
                        val result = generationResult.executionResult
                        generationReasoningContext.record(result)
                        if (commitAssistantReply(result?.assistantMsgId)) {
                            scheduleProactiveDialogue()
                        }
                    } else if (generationResult is ImageGenerationCoordinator.ImageGenerationResult.ImageGenerationFailed) {
                        onToast("图片生成失败，请检查 OpenAI API 配置")
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

    fun triggerProactiveIfNeeded() {
        proactiveDialogueCoordinator.triggerIfNeeded(
            currentMessages = messagesProvider(),
            sendSingleChatMessage = { sendSingleChatMessage(it) },
            sendGroupChatMessage = { sendGroupChatMessage(it) },
            sendDirectMessage = { content, character -> sendDirectMessage(content, character) }
        )
    }

    fun cancel() {
        streamingJob?.cancel()
    }

    // ==================== 内部方法 ====================

    private fun sendSingleChatMessage(content: String, imagePaths: List<String> = emptyList()) {
        launchGenerationJob {
            val character = characterProvider() ?: return@launchGenerationJob
            val config = configReader.readConfig()
            val result = generationSendCoordinator.sendSingle(character, content, config, imagePaths)
            generationReasoningContext.record(result)
            if (commitAssistantReply(result?.assistantMsgId)) {
                scheduleProactiveDialogue()
            }
        }
    }

    private fun sendGroupChatMessage(content: String, imagePaths: List<String> = emptyList()) {
        launchGenerationJob(
            clearRespondingOnExit = true,
            onFinally = { if (!wasCancelled) scheduleGroupProactiveDialogue() }
        ) {
            val characters = groupCharactersProvider()
            if (characters.isEmpty()) return@launchGenerationJob
            val config = configReader.readConfig()
            val groupResult = generationSendCoordinator.sendGroup(
                characters = characters,
                content = content,
                config = config,
                imagePaths = imagePaths,
                schedulingStrategy = schedulingStrategyProvider(),
                chattinessByCharacterId = groupCharacterChattinessProvider(),
                intervalMs = messageIntervalProvider(),
                isCancelled = { wasCancelled },
                onRespondingCharacterChanged = onRespondingCharacterChanged,
                onAssistantReplyCommit = { assistantMsgId -> commitAssistantReply(assistantMsgId) }
            )
            generationReasoningContext.recordAll(groupResult.results.map { it.second })
        }
    }

    private fun sendDirectMessage(content: String, targetCharacter: CharacterEntity, imagePaths: List<String> = emptyList()) {
        launchGenerationJob(clearRespondingOnExit = true) {
            onRespondingCharacterChanged(targetCharacter)
            val characters = groupCharactersProvider()
            val config = configReader.readConfig()
            val result = generationSendCoordinator.sendDirect(characters, targetCharacter, content, config, imagePaths)
            generationReasoningContext.record(result)
            commitAssistantReply(result?.assistantMsgId)
        }
    }

    // ==================== 主动对话 ====================

    private fun scheduleProactiveDialogue() {
        proactiveDialogueCoordinator.scheduleSingle()
    }

    private fun scheduleGroupProactiveDialogue() {
        proactiveDialogueCoordinator.scheduleGroup()
    }

    // ==================== 消息拆分 ====================

    private suspend fun commitAssistantReply(
        assistantMsgId: Long?,
        updateEmotion: Boolean = true,
        respectCancellation: Boolean = true
    ): Boolean {
        return assistantReplyCommitter.commitAssistantReply(
            assistantMsgId = assistantMsgId,
            isCancelled = { wasCancelled },
            updateEmotion = updateEmotion,
            respectCancellation = respectCancellation,
            onEmotionUpdate = onEmotionUpdate,
            onAssistantReplyCommitted = onAssistantReplyCommitted
        )
    }

    companion object {
        private val random = kotlin.random.Random.Default

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
