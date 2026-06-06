package com.tavern.lite.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.SummaryEntity
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.GroupChatRepository
import com.tavern.lite.data.repository.BgmRepository
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
import com.tavern.lite.ui.screens.chat.manager.BranchManager
import com.tavern.lite.ui.screens.chat.manager.ChatStreamingManager
import com.tavern.lite.ui.screens.chat.manager.GroupChatSettingsManager
import com.tavern.lite.ui.screens.chat.manager.SearchManager
import com.tavern.lite.ui.screens.chat.manager.SpeechManager
import com.tavern.lite.ui.screens.chat.manager.VnModeManager
import com.tavern.lite.ui.screens.vn.BgmPlayer
import com.tavern.lite.util.ChatActiveTracker
import com.tavern.lite.util.SwipeUtils
import com.tavern.lite.util.TokenEstimator
import com.tavern.lite.util.TtsHelper
import com.tavern.lite.util.SttHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
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
    private val bgmRepository: BgmRepository,
    private val emotionDetector: EmotionDetector,
    private val bgmPlayer: BgmPlayer,
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

    // VN 模式管理器
    internal lateinit var vnModeManager: VnModeManager
        private set

    val bubbleStyle: StateFlow<BubbleStyleConfig> = settingsStore.bubbleStyleFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BubbleStyleConfig())

    val messages: StateFlow<List<MessageEntity>> = chatRepository.getMessagesForChat(chatId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 分页加载：只显示最近 N 条消息，滚动到顶部时加载更多
    private val _pageSize = MutableStateFlow(PAGE_SIZE)
    val displayMessages: StateFlow<List<MessageEntity>> = _pageSize.flatMapLatest { size ->
        flow {
            emit(chatRepository.getMessagesPage(chatId, size))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessagesLoaded: StateFlow<Boolean> = combine(
        messages, _pageSize
    ) { all, page -> all.size <= page }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadMoreMessages() {
        val currentAll = messages.value.size
        val currentPageSize = _pageSize.value
        if (currentPageSize < currentAll) {
            _pageSize.value = minOf(currentPageSize + PAGE_SIZE, currentAll)
        }
    }

    // O(1) message lookup by ID — updated when messages change
    private val _messageMap = MutableStateFlow<Map<Long, MessageEntity>>(emptyMap())

    // 流式生成标记：新消息到达时自动扩展分页窗口
    private var _isStreamingNewMessage = false

    /** 全部消息数量（用于滚动到底部） */
    val totalMessageCount: Int get() = messages.value.size

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // 流式对话管理器
    internal lateinit var streamingManager: ChatStreamingManager
        private set

    // 分支管理器
    internal lateinit var branchManager: BranchManager
        private set

    // 搜索管理器
    internal lateinit var searchManager: SearchManager
        private set

    // 语音管理器
    internal lateinit var speechManager: SpeechManager
        private set

    // 群聊设置管理器
    internal lateinit var groupChatSettingsManager: GroupChatSettingsManager
        private set

    init {
        ChatActiveTracker.setActive(chatId)

        // 群聊设置管理器（先初始化，其他管理器依赖其状态）
        groupChatSettingsManager = GroupChatSettingsManager(
            chatId = chatId,
            characterRepository = characterRepository,
            chatRepository = chatRepository,
            groupChatRepository = groupChatRepository,
            scope = viewModelScope
        ).apply {
            characterProvider = { _character.value }
        }

        streamingManager = ChatStreamingManager(
            chatId = chatId,
            characterId = characterId,
            chatRepository = chatRepository,
            apiConfigStore = apiConfigStore,
            sendMessageUseCase = sendMessageUseCase,
            continueGenerationUseCase = continueGenerationUseCase,
            proactiveMessageUseCase = proactiveMessageUseCase,
            proactiveDialogueUseCase = proactiveDialogueUseCase,
            imageGenerationService = imageGenerationService,
            scope = viewModelScope
        ).apply {
            characterProvider = { _character.value }
            groupCharactersProvider = { _groupCharacters.value }
            isGroupChatProvider = { _isGroupChat.value }
            messagesProvider = { messages.value }
            schedulingStrategyProvider = { groupChatSettingsManager.schedulingStrategy.value }
            groupCharacterChattinessProvider = { groupChatSettingsManager.groupCharacterChattiness.value }
            messageIntervalProvider = { groupChatSettingsManager.messageIntervalMs.value }
            onRespondingCharacterChanged = { _respondingCharacter.value = it }
            onEmotionUpdate = { vnModeManager.updateEmotionFromResponse(it) }
            onToast = { _toastMessage.emit(it) }
        }

        // VN 模式管理器
        vnModeManager = VnModeManager(
            spriteRepository = spriteRepository,
            emotionDetector = emotionDetector,
            bgmRepository = bgmRepository,
            bgmPlayer = bgmPlayer,
            scope = viewModelScope
        ).apply {
            characterIdProvider = { characterId }
            isGroupChatProvider = { _isGroupChat.value }
            respondingCharacterProvider = { _respondingCharacter.value }
            groupCharactersProvider = { _groupCharacters.value }
        }

        // 分支管理器
        branchManager = BranchManager(
            chatId = chatId,
            chatRepository = chatRepository,
            scope = viewModelScope
        )

        // 搜索管理器
        searchManager = SearchManager(
            scope = viewModelScope
        ).apply {
            messagesProvider = { messages.value }
        }

        // 语音管理器
        speechManager = SpeechManager(
            ttsHelper = ttsHelper,
            sttHelper = sttHelper,
            scope = viewModelScope
        )

        // 流式生成标记：自动扩展分页窗口
        viewModelScope.launch {
            streamingManager.isGenerating.collect { generating ->
                _isStreamingNewMessage = generating
            }
        }

        viewModelScope.launch {
            messages.collect { list ->
                // 增量更新 messageMap：复用已有条目，只更新变化的
                val oldMap = _messageMap.value
                val newMap = LinkedHashMap<Long, MessageEntity>(list.size)
                for (msg in list) {
                    newMap[msg.id] = oldMap[msg.id]?.takeIf { it.content == msg.content && it.swipeIndex == msg.swipeIndex } ?: msg
                }
                _messageMap.value = newMap
                searchManager.incrementCacheVersion()

                // 增量更新 token 估算
                val char = _character.value
                if (char != null) {
                    val oldTokens = _estimatedContextTokens.value
                    val oldSize = oldMap.size
                    val newSize = list.size
                    if (oldSize == 0 || newSize <= oldSize) {
                        // 首次加载或消息减少（删除）：全量重算
                        _estimatedContextTokens.value = 500 + list.sumOf { TokenEstimator.estimateText(it.content) + 4 }
                    } else {
                        // 消息增加（新消息/流式追加）：只计算新增部分
                        val addedTokens = list.subList(oldSize, newSize).sumOf { TokenEstimator.estimateText(it.content) + 4 }
                        _estimatedContextTokens.value = oldTokens + addedTokens
                    }
                } else {
                    _estimatedContextTokens.value = 0
                }

                // 流式生成时自动扩展分页窗口以显示新消息
                if (list.size > _pageSize.value && _isStreamingNewMessage) {
                    _pageSize.value = list.size
                }
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
            if (char != null) groupChatSettingsManager.loadCharacterChattiness(char.chattiness)

            // 加载可用表情
            vnModeManager.loadAvailableEmotions()

            // Group chat detection
            if (chat?.isGroup == true) {
                _isGroupChat.value = true
                val chars = groupChatRepository.getCharactersForChatSync(chatId)
                _groupCharacters.value = chars
                val chatChars = groupChatRepository.getChatCharacters(chatId)
                groupChatSettingsManager.loadGroupSettings(
                    groupChattiness = chat.groupChattiness,
                    schedulingStrategy = GroupSchedulingStrategy.fromKey(chat.schedulingStrategy),
                    messageIntervalMs = chat.messageIntervalMs,
                    chatCharacters = chatChars.map { it.characterId to it.chattiness }
                )
            }
        }
    }

    private fun findMessage(id: Long): MessageEntity? = _messageMap.value[id]

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

    // 对话摘要
    val summaries: StateFlow<List<SummaryEntity>> = summaryRepository.getSummariesForChat(chatId)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

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

    fun copyMessage(context: Context, messageId: Long) {
        val msg = findMessage(messageId) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", msg.content))
    }

    override fun onCleared() {
        super.onCleared()
        if (::streamingManager.isInitialized) streamingManager.cancel()
        if (::speechManager.isInitialized) speechManager.shutdown()
        ChatActiveTracker.clearActive(chatId)
    }

    companion object {
        private const val PAGE_SIZE = 50
    }

}
