package com.tavern.lite.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.SummaryEntity
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.GroupChatRepository
import com.tavern.lite.data.repository.SpriteRepository
import com.tavern.lite.data.repository.SummaryRepository
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.domain.usecase.ContinueGenerationUseCase
import com.tavern.lite.domain.usecase.MemoryExtractionUseCase
import com.tavern.lite.domain.usecase.SummaryUseCase
import com.tavern.lite.domain.usecase.ProactiveDialogueUseCase
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.domain.usecase.SendMessageUseCase
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.EmotionDetector
import com.tavern.lite.network.ImageGenerationService
import com.tavern.lite.util.ChatActiveTracker
import com.tavern.lite.util.SwipeUtils
import com.tavern.lite.util.TokenEstimator
import com.tavern.lite.util.TtsHelper
import com.tavern.lite.util.SttHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val groupChatRepository: GroupChatRepository,
    private val apiConfigStore: ApiConfigStore,
    private val settingsStore: SettingsStore,
    private val sendMessageUseCase: SendMessageUseCase,
    private val continueGenerationUseCase: ContinueGenerationUseCase,
    private val proactiveMessageUseCase: ProactiveMessageUseCase,
    private val proactiveDialogueUseCase: ProactiveDialogueUseCase,
    private val memoryExtractionUseCase: MemoryExtractionUseCase,
    private val summaryUseCase: SummaryUseCase,
    private val summaryRepository: SummaryRepository,
    private val spriteRepository: SpriteRepository,
    private val emotionDetector: EmotionDetector,
    private val imageGenerationService: ImageGenerationService,
    private val ttsHelper: TtsHelper,
    private val sttHelper: SttHelper,
    val markwon: Markwon
) : ViewModel() {

    private val characterId: Long = savedStateHandle.get<Long>("characterId") ?: 0
    private val chatId: Long = savedStateHandle.get<Long>("chatId") ?: 0

    private val _character = MutableStateFlow<CharacterEntity?>(null)
    val character: StateFlow<CharacterEntity?> = _character.asStateFlow()

    // 背景路径：对话级 > 角色级
    private val _backgroundPath = MutableStateFlow<String?>(null)
    val backgroundPath: StateFlow<String?> = _backgroundPath.asStateFlow()

    // Group chat state
    private val _isGroupChat = MutableStateFlow(false)
    val isGroupChat: StateFlow<Boolean> = _isGroupChat.asStateFlow()

    private val _groupCharacters = MutableStateFlow<List<CharacterEntity>>(emptyList())
    val groupCharacters: StateFlow<List<CharacterEntity>> = _groupCharacters.asStateFlow()

    private val _respondingCharacter = MutableStateFlow<CharacterEntity?>(null)
    val respondingCharacter: StateFlow<CharacterEntity?> = _respondingCharacter.asStateFlow()

    // 健谈度状态
    private val _characterChattiness = MutableStateFlow(50)
    val characterChattiness: StateFlow<Int> = _characterChattiness.asStateFlow()

    private val _groupChattiness = MutableStateFlow(50)
    val groupChattiness: StateFlow<Int> = _groupChattiness.asStateFlow()

    private val _groupCharacterChattiness = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val groupCharacterChattiness: StateFlow<Map<Long, Int>> = _groupCharacterChattiness.asStateFlow()

    // 调度策略
    private val _schedulingStrategy = MutableStateFlow(GroupSchedulingStrategy.NATURAL)
    val schedulingStrategy: StateFlow<GroupSchedulingStrategy> = _schedulingStrategy.asStateFlow()

    // 发言间隔
    private val _messageIntervalMs = MutableStateFlow(1500L)
    val messageIntervalMs: StateFlow<Long> = _messageIntervalMs.asStateFlow()

    // VN 模式 - 立绘状态
    private val _currentEmotion = MutableStateFlow("neutral")
    val currentEmotion: StateFlow<String> = _currentEmotion.asStateFlow()

    private val _currentSpritePath = MutableStateFlow<String?>(null)
    val currentSpritePath: StateFlow<String?> = _currentSpritePath.asStateFlow()

    private val _availableEmotions = MutableStateFlow<List<String>>(emptyList())
    val availableEmotions: StateFlow<List<String>> = _availableEmotions.asStateFlow()

    val bubbleStyle: StateFlow<BubbleStyleConfig> = settingsStore.bubbleStyleFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BubbleStyleConfig())

    val messages: StateFlow<List<MessageEntity>> = chatRepository.getMessagesForChat(chatId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // O(1) message lookup by ID — updated when messages change
    private val _messageMap = MutableStateFlow<Map<Long, MessageEntity>>(emptyMap())
    private var searchCacheVersion = 0
    private val _searchCache = mutableMapOf<Pair<String, Int>, List<Int>>()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var streamingJob: Job? = null
    private val streamingMutex = Mutex()
    @Volatile private var wasCancelled = false
    // 防止主动对话链式触发
    @Volatile private var isProactiveMessage = false

    init {
        ChatActiveTracker.setActive(chatId)

        viewModelScope.launch {
            messages.collect { list ->
                _messageMap.value = list.associateBy { it.id }
                searchCacheVersion++
                _searchCache.clear()
                // Update token estimate
                val char = _character.value
                val tokens = if (char != null) {
                    val systemOverhead = 500
                    val historyTokens = list.sumOf { TokenEstimator.estimateText(it.content) + 4 }
                    systemOverhead + historyTokens
                } else {
                    0
                }
                _estimatedContextTokens.value = tokens
            }
        }

        viewModelScope.launch {
            val char = characterRepository.getCharacterById(characterId)
            _character.value = char
            val chat = chatRepository.getChatById(chatId)
            _backgroundPath.value = chat?.backgroundPath ?: char?.backgroundPath

            // 初始化记忆提取器的消息计数
            val count = chatRepository.getMessageCount(chatId)
            memoryExtractionUseCase.setMessageCount(count)

            // 加载健谈度
            if (char != null) _characterChattiness.value = char.chattiness

            // 加载可用表情
            loadAvailableEmotions()

            // Group chat detection
            if (chat?.isGroup == true) {
                _isGroupChat.value = true
                val chars = groupChatRepository.getCharactersForChatSync(chatId)
                _groupCharacters.value = chars
                _groupChattiness.value = chat.groupChattiness
                _schedulingStrategy.value = GroupSchedulingStrategy.fromKey(chat.schedulingStrategy)
                _messageIntervalMs.value = chat.messageIntervalMs
                val chatChars = groupChatRepository.getChatCharacters(chatId)
                _groupCharacterChattiness.value = chatChars.associate { it.characterId to it.chattiness }
            }
        }
    }

    private fun findMessage(id: Long): MessageEntity? = _messageMap.value[id]

    fun sendMessage(content: String, imagePaths: List<String> = emptyList()) {
        if ((content.isBlank() && imagePaths.isEmpty()) || _isGenerating.value) return

        if (_isGroupChat.value) {
            val atResult = proactiveDialogueUseCase.parseAtMention(content, _groupCharacters.value)
            if (atResult != null) {
                sendDirectMessage(atResult.second, atResult.first, imagePaths)
            } else {
                sendGroupChatMessage(content, imagePaths)
            }
        } else {
            sendSingleChatMessage(content, imagePaths)
        }
    }

    private fun sendSingleChatMessage(content: String, imagePaths: List<String> = emptyList()) {
        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val character = _character.value ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    val result = sendMessageUseCase.sendSingleMessage(chatId, character, content, config, null, imagePaths)
                    if (result?.assistantMsgId != null && !wasCancelled) {
                        // 更新立绘表情
                        val assistantMsg = chatRepository.getMessageById(result.assistantMsgId)
                        if (assistantMsg != null) {
                            updateEmotionFromResponse(assistantMsg.content)
                        }
                        splitIntoMultipleMessages(result.assistantMsgId)
                        scheduleProactiveDialogue()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _toastMessage.emit(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    streamingJob = null
                }
            }
        }
    }

    // Round-robin 索引
    private var roundRobinIndex = 0

    private fun sendGroupChatMessage(content: String, imagePaths: List<String> = emptyList()) {
        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val characters = _groupCharacters.value
                    if (characters.isEmpty()) return@withLock
                    val config = apiConfigStore.configFlow.first()

                    // 根据调度策略选择发言角色
                    val respondingChars = selectRespondingCharacters(characters)

                    val intervalMs = _messageIntervalMs.value
                    val results = sendMessageUseCase.sendGroupMessage(chatId, respondingChars, content, config, imagePaths)
                    for ((charId, result) in results) {
                        if (wasCancelled) break
                        _respondingCharacter.value = characters.find { it.id == charId }
                        if (result.assistantMsgId != null) {
                            // 更新立绘表情
                            val assistantMsg = chatRepository.getMessageById(result.assistantMsgId)
                            if (assistantMsg != null) {
                                updateEmotionFromResponse(assistantMsg.content)
                            }
                            splitIntoMultipleMessages(result.assistantMsgId)
                        }
                        // 角色间延迟：基于配置间隔 + 自然抖动
                        if (charId != results.last().first && !wasCancelled) {
                            val jitter = random.nextLong((intervalMs * 0.3).toLong())
                            delay(intervalMs + jitter)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _toastMessage.emit(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    _respondingCharacter.value = null
                    streamingJob = null
                    if (!wasCancelled) {
                        scheduleGroupProactiveDialogue()
                    }
                }
            }
        }
    }

    /**
     * 根据调度策略选择本轮发言的角色列表
     */
    private fun selectRespondingCharacters(characters: List<CharacterEntity>): List<CharacterEntity> {
        return when (_schedulingStrategy.value) {
            GroupSchedulingStrategy.NATURAL -> {
                // 基于健谈度 + 随机因子
                characters.filter { char ->
                    val chattiness = _groupCharacterChattiness.value[char.id] ?: char.chattiness
                    val responseChance = 0.5 + (chattiness / 100.0) * 0.5 // 50%-100%
                    random.nextDouble() < responseChance
                }.ifEmpty { listOf(characters.random()) }
            }
            GroupSchedulingStrategy.LIST_ORDER -> {
                // 按列表顺序，所有角色依次发言
                characters
            }
            GroupSchedulingStrategy.ROUND_ROBIN -> {
                // 轮流发言：每次只选一个角色
                val char = characters[roundRobinIndex % characters.size]
                roundRobinIndex = (roundRobinIndex + 1) % characters.size
                listOf(char)
            }
        }
    }

    private fun sendDirectMessage(content: String, targetCharacter: CharacterEntity, imagePaths: List<String> = emptyList()) {
        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                _respondingCharacter.value = targetCharacter
                try {
                    val characters = _groupCharacters.value
                    val config = apiConfigStore.configFlow.first()

                    val result = sendMessageUseCase.sendDirectMessage(chatId, characters, targetCharacter, content, config, imagePaths)
                    if (result?.assistantMsgId != null && !wasCancelled) {
                        splitIntoMultipleMessages(result.assistantMsgId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _toastMessage.emit(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    _respondingCharacter.value = null
                    streamingJob = null
                }
            }
        }
    }

    fun stopGeneration() {
        wasCancelled = true
        streamingJob?.cancel()
        streamingJob = null
        _isGenerating.value = false
    }

    /**
     * 活人感：将 AI 回复按段落拆分成多条消息，逐条显示。
     */
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

    // ==================== 主动对话逻辑 ====================

    private fun scheduleProactiveDialogue() {
        if (_isGroupChat.value || isProactiveMessage) return
        val character = _character.value ?: return

        val delayMs = proactiveDialogueUseCase.shouldScheduleProactive(character.chattiness) ?: return

        viewModelScope.launch {
            delay(delayMs)
            if (!_isGenerating.value) {
                sendProactiveSingleMessage()
            }
        }
    }

    private fun sendProactiveSingleMessage() {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                isProactiveMessage = true
                try {
                    val character = _character.value ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    val result = proactiveMessageUseCase.sendProactiveMessage(chatId, character, config)
                    if (result?.assistantMsgId != null) {
                        splitIntoMultipleMessages(result.assistantMsgId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _toastMessage.emit(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    isProactiveMessage = false
                    streamingJob = null
                }
            }
        }
    }

    private fun scheduleGroupProactiveDialogue() {
        if (!_isGroupChat.value || isProactiveMessage) return
        val characters = _groupCharacters.value
        if (characters.isEmpty()) return

        val delayMs = proactiveDialogueUseCase.shouldScheduleGroupProactive(characters) ?: return

        viewModelScope.launch {
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
        streamingJob = viewModelScope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                _respondingCharacter.value = character
                isProactiveMessage = true
                try {
                    val characters = _groupCharacters.value
                    val config = apiConfigStore.configFlow.first()

                    val result = proactiveMessageUseCase.sendProactiveGroupMessage(chatId, characters, character, config)
                    if (result?.assistantMsgId != null) {
                        splitIntoMultipleMessages(result.assistantMsgId)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _toastMessage.emit(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    _respondingCharacter.value = null
                    isProactiveMessage = false
                    streamingJob = null
                }
            }
        }
    }

    fun continueGeneration() {
        val lastMsg = messages.value.lastOrNull { it.role == "assistant" }
        if (lastMsg == null || _isGenerating.value) return

        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val character = _character.value ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    continueGenerationUseCase.continueGeneration(
                        chatId, characterId, character, lastMsg.id, lastMsg.content, config
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _toastMessage.emit(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    streamingJob = null
                }
            }
        }
    }

    /**
     * Generate an image using DALL-E API and send it as a message attachment.
     * Triggered by "/imagine <prompt>" command.
     */
    fun generateImage(prompt: String) {
        if (prompt.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val config = apiConfigStore.configFlow.first()
                val character = _character.value ?: return@launch

                val imagePath = imageGenerationService.generateImage(prompt, config)
                if (imagePath != null) {
                    // Send the generated image as a user message with attachment
                    chatRepository.sendMessage(
                        chatId = chatId,
                        content = "/imagine $prompt",
                        role = "user",
                        imagePaths = listOf(imagePath)
                    )
                    // Let the AI respond to the image
                    sendSingleChatMessage("", listOf(imagePath))
                } else {
                    _toastMessage.emit("图片生成失败，请检查 OpenAI API 配置")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _toastMessage.emit(classifyError(e))
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun regenerate(messageId: Long) {
        val allMessages = messages.value
        val msg = allMessages.find { it.id == messageId }
        if (msg == null || msg.role != "assistant") return

        val msgIndex = allMessages.indexOfFirst { it.id == messageId }
        val userMsg = allMessages.take(msgIndex).lastOrNull { it.role == "user" }
        if (userMsg == null) return

        wasCancelled = false
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            streamingMutex.withLock {
                _isGenerating.value = true
                try {
                    val character = _character.value ?: return@withLock
                    val config = apiConfigStore.configFlow.first()

                    continueGenerationUseCase.regenerate(
                        chatId, characterId, character, messageId, userMsg.content, config
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _toastMessage.emit(classifyError(e))
                } finally {
                    _isGenerating.value = false
                    streamingJob = null
                }
            }
        }
    }

    /**
     * 重发用户消息：删除该消息及之后的所有消息，然后重新发送
     */
    fun resendUserMessage(messageId: Long) {
        val allMessages = messages.value
        val msg = allMessages.find { it.id == messageId }
        if (msg == null || msg.role != "user" || _isGenerating.value) return

        val msgIndex = allMessages.indexOfFirst { it.id == messageId }
        val content = msg.content

        viewModelScope.launch {
            // 删除该消息及之后的所有消息
            for (i in msgIndex until allMessages.size) {
                chatRepository.deleteMessage(allMessages[i].id)
            }
            // 重新发送
            sendMessage(content)
        }
    }

    fun swipeLeft(messageId: Long) {
        viewModelScope.launch {
            val msg = findMessage(messageId) ?: return@launch
            val newIndex = msg.swipeIndex - 1
            if (newIndex >= 0) {
                chatRepository.switchSwipe(messageId, newIndex)
            }
        }
    }

    fun swipeRight(messageId: Long) {
        viewModelScope.launch {
            val msg = findMessage(messageId) ?: return@launch
            val swipes = SwipeUtils.parseSwipeContent(msg.swipeContent)
            val newIndex = msg.swipeIndex + 1
            if (newIndex < swipes.size) {
                chatRepository.switchSwipe(messageId, newIndex)
            }
        }
    }

    fun getSwipeInfo(messageId: Long): Pair<Int, Int> {
        val msg = findMessage(messageId) ?: return Pair(0, 0)
        val swipes = SwipeUtils.parseSwipeContent(msg.swipeContent)
        val count = if (swipes.isEmpty()) 1 else swipes.size
        return Pair(msg.swipeIndex + 1, count)
    }

    fun editMessage(messageId: Long, newContent: String) {
        viewModelScope.launch {
            chatRepository.updateMessageContent(messageId, newContent)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }

    fun togglePinMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = findMessage(messageId) ?: return@launch
            chatRepository.togglePinMessage(messageId, !msg.isPinned)
        }
    }

    // Token estimation for context window usage
    private val _estimatedContextTokens = MutableStateFlow(0)
    val estimatedContextTokens: StateFlow<Int> = _estimatedContextTokens.asStateFlow()

    fun estimateInputTokens(text: String): Int = TokenEstimator.estimateText(text)

    val pinnedMessages: StateFlow<List<MessageEntity>> = chatRepository.getPinnedMessages(chatId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 分支操作
    private val _branchEntities = MutableStateFlow<List<BranchEntity>>(emptyList())
    val branchEntities: StateFlow<List<BranchEntity>> = _branchEntities.asStateFlow()

    private val _currentBranchId = MutableStateFlow<Long?>(null)
    val currentBranchId: StateFlow<Long?> = _currentBranchId.asStateFlow()

    // 书签筛选
    private val _showBookmarksOnly = MutableStateFlow(false)
    val showBookmarksOnly: StateFlow<Boolean> = _showBookmarksOnly.asStateFlow()

    // 对话摘要
    val summaries: StateFlow<List<SummaryEntity>> = summaryRepository.getSummariesForChat(chatId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

    fun loadBranches() {
        viewModelScope.launch {
            val branches = chatRepository.getBranchesForChatSync(chatId)
            _branchEntities.value = branches
            val defaultBranch = branches.find { it.isDefault } ?: branches.lastOrNull()
            _currentBranchId.value = defaultBranch?.id
        }
    }

    fun switchBranch(branchId: Long) {
        viewModelScope.launch {
            chatRepository.switchBranch(chatId, branchId)
            _currentBranchId.value = branchId
        }
    }

    fun createBranch(name: String) {
        viewModelScope.launch {
            chatRepository.createBranch(chatId, name)
            loadBranches()
        }
    }

    fun createBranchFromMessage(messageId: Long, name: String) {
        viewModelScope.launch {
            chatRepository.createBranchFromMessage(chatId, messageId, name)
            loadBranches()
        }
    }

    fun deleteBranch(branch: BranchEntity) {
        viewModelScope.launch {
            chatRepository.deleteBranch(branch)
            loadBranches()
        }
    }

    fun toggleBookmarkFilter() {
        _showBookmarksOnly.value = !_showBookmarksOnly.value
    }

    // === 对话摘要 ===

    fun generateSummary() {
        if (_isGeneratingSummary.value) return
        viewModelScope.launch {
            _isGeneratingSummary.value = true
            try {
                val character = _character.value ?: return@launch
                val config = apiConfigStore.configFlow.first()
                summaryUseCase.generateManualSummary(chatId, config, character.name)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _toastMessage.emit("摘要生成失败: ${e.message}")
            } finally {
                _isGeneratingSummary.value = false
            }
        }
    }

    fun deleteSummary(summary: SummaryEntity) {
        viewModelScope.launch {
            summaryRepository.deleteSummary(summary.id)
        }
    }

    fun setChatBackground(path: String?) {
        viewModelScope.launch {
            chatRepository.updateChatBackground(chatId, path)
            _backgroundPath.value = path
        }
    }

    fun clearChatBackground() {
        setChatBackground(null)
    }

    fun getCharacterForMessage(message: MessageEntity): CharacterEntity? {
        if (!_isGroupChat.value) return _character.value
        val charId = message.characterId ?: return null
        return _groupCharacters.value.find { it.id == charId }
    }

    fun triggerProactiveIfNeeded() {
        val currentMessages = messages.value
        if (_isGenerating.value || isProactiveMessage) return

        val lastMsg = currentMessages.lastOrNull() ?: return

        if (lastMsg.role == "user") {
            if (_isGroupChat.value) {
                sendGroupChatMessage("")
            } else {
                sendSingleChatMessage("")
            }
        } else if (_isGroupChat.value && lastMsg.role == "assistant") {
            val characters = _groupCharacters.value
            val lastCharIndex = characters.indexOfFirst { it.id == lastMsg.characterId }
            if (lastCharIndex >= 0 && lastCharIndex < characters.size - 1) {
                val nextChar = characters[lastCharIndex + 1]
                sendDirectMessage("", nextChar)
            }
        }
    }

    // === 健谈度更新 ===

    fun updateCharacterChattiness(value: Int) {
        _characterChattiness.value = value
        viewModelScope.launch {
            val char = _character.value ?: return@launch
            characterRepository.updateCharacter(char.copy(chattiness = value))
        }
    }

    fun updateGroupChattiness(value: Int) {
        _groupChattiness.value = value
        viewModelScope.launch {
            chatRepository.updateGroupChattiness(chatId, value)
        }
    }

    fun updateGroupCharacterChattiness(characterId: Long, value: Int) {
        _groupCharacterChattiness.value = _groupCharacterChattiness.value.toMutableMap().apply {
            put(characterId, value)
        }
        viewModelScope.launch {
            groupChatRepository.updateCharacterChattiness(chatId, characterId, value)
        }
    }

    fun updateSchedulingStrategy(strategy: GroupSchedulingStrategy) {
        _schedulingStrategy.value = strategy
        viewModelScope.launch {
            groupChatRepository.updateSchedulingStrategy(chatId, strategy.key)
        }
    }

    fun updateMessageInterval(intervalMs: Long) {
        _messageIntervalMs.value = intervalMs
        viewModelScope.launch {
            groupChatRepository.updateMessageInterval(chatId, intervalMs)
        }
    }

    // === 搜索功能 ===

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    fun searchMessages(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentSearchIndex.value = -1
            return
        }
        val lowerQuery = query.lowercase()
        val cacheKey = lowerQuery to searchCacheVersion
        val results = _searchCache.getOrPut(cacheKey) {
            messages.value.mapIndexedNotNull { index, msg ->
                if (msg.content.lowercase().contains(lowerQuery)) index else null
            }
        }
        _searchResults.value = results
        _currentSearchIndex.value = if (results.isNotEmpty()) 0 else -1
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        _currentSearchIndex.value = (_currentSearchIndex.value + 1) % results.size
    }

    fun previousSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        _currentSearchIndex.value = (_currentSearchIndex.value - 1 + results.size) % results.size
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentSearchIndex.value = -1
    }

    // === TTS 语音朗读 ===

    val isSpeaking: StateFlow<Boolean> = ttsHelper.isSpeaking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val speakingMessageId: StateFlow<Long?> = ttsHelper.speakingMessageId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun speakMessage(message: MessageEntity) {
        ttsHelper.speak(message.content, message.id)
    }

    fun stopSpeaking() {
        ttsHelper.stop()
    }

    // STT 语音输入
    val isListening: StateFlow<Boolean> = sttHelper.isListening
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val sttPartialText: StateFlow<String> = sttHelper.partialText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun startVoiceInput(onResult: (String) -> Unit) {
        sttHelper.startListening(onResult = onResult)
    }

    fun stopVoiceInput() {
        sttHelper.stopListening()
    }

    fun copyMessage(context: Context, messageId: Long) {
        val msg = findMessage(messageId) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", msg.content))
    }

    // ==================== VN 模式 - 立绘表情 ====================

    /**
     * 加载角色的可用表情列表。
     */
    fun loadAvailableEmotions() {
        viewModelScope.launch {
            val charId = if (_isGroupChat.value) {
                _respondingCharacter.value?.id ?: _groupCharacters.value.firstOrNull()?.id ?: return@launch
            } else {
                characterId
            }
            _availableEmotions.value = spriteRepository.getAvailableEmotions(charId)
        }
    }

    /**
     * 根据 AI 回复内容更新当前表情。
     */
    fun updateEmotionFromResponse(responseText: String) {
        val emotion = emotionDetector.detectEmotion(responseText)
        _currentEmotion.value = emotion

        viewModelScope.launch {
            val charId = if (_isGroupChat.value) {
                _respondingCharacter.value?.id ?: _groupCharacters.value.firstOrNull()?.id ?: return@launch
            } else {
                characterId
            }
            val sprite = spriteRepository.getSpriteByEmotion(charId, emotion)
            _currentSpritePath.value = sprite?.imagePath
        }
    }

    /**
     * 手动设置当前表情（用于用户选择）。
     */
    fun setEmotion(emotion: String) {
        _currentEmotion.value = emotion
        viewModelScope.launch {
            val charId = if (_isGroupChat.value) {
                _respondingCharacter.value?.id ?: _groupCharacters.value.firstOrNull()?.id ?: return@launch
            } else {
                characterId
            }
            val sprite = spriteRepository.getSpriteByEmotion(charId, emotion)
            _currentSpritePath.value = sprite?.imagePath
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
        ttsHelper.stop()
        sttHelper.shutdown()
        ChatActiveTracker.clearActive(chatId)
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
