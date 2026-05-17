package com.tavern.lite.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import com.tavern.lite.network.PromptBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val worldBookRepository: WorldBookRepository,
    private val chatApiService: ChatApiService,
    private val apiConfigStore: ApiConfigStore,
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val personaRepository: PersonaRepository,
    private val scriptRepository: ScriptRepository,
    private val authorNoteDao: AuthorNoteDao,
    val markwon: Markwon
) : ViewModel() {

    private val characterId: Long = savedStateHandle.get<Long>("characterId") ?: 0
    private val chatId: Long = savedStateHandle.get<Long>("chatId") ?: 0

    private val _character = MutableStateFlow<CharacterEntity?>(null)
    val character: StateFlow<CharacterEntity?> = _character.asStateFlow()

    // 背景路径：对话级 > 角色级
    private val _backgroundPath = MutableStateFlow<String?>(null)
    val backgroundPath: StateFlow<String?> = _backgroundPath.asStateFlow()

    val bubbleStyle: StateFlow<BubbleStyleConfig> = settingsStore.bubbleStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BubbleStyleConfig())

    val messages: StateFlow<List<MessageEntity>> = chatRepository.getMessagesForChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            val char = characterRepository.getCharacterById(characterId)
            _character.value = char
            // 加载背景：对话级覆盖角色级
            val chat = chatRepository.getChatById(chatId)
            _backgroundPath.value = chat?.backgroundPath ?: char?.backgroundPath
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _isGenerating.value) return

        streamingJob = viewModelScope.launch {
            // 对用户消息执行正则脚本（类型 0 = 用户消息）
            val processedContent = scriptRepository.applyScripts(characterId, content, 0)

            // 保存用户消息（使用处理后的文本）
            chatRepository.sendMessage(chatId, processedContent, "user")

            _isGenerating.value = true
            _streamingText.value = ""

            var assistantMsgId: Long? = null
            try {
                val character = _character.value ?: return@launch
                val config = apiConfigStore.configFlow.first()
                val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

                // 世界书匹配
                val worldBookEntries = if (character.worldBookId != null) {
                    worldBookRepository.matchEntries(character.worldBookId, processedContent)
                } else {
                    emptyList()
                }

                // 记忆检索
                val memories = memoryRepository.getRelevantMemories(characterId, processedContent)
                memoryRepository.touchMemories(memories.map { it.id })

                // 加载作者注释
                val authorNote = authorNoteDao.getAuthorNoteSync(characterId)

                // 加载用户角色（per-character override > default）
                val persona = personaRepository.getEffectivePersona(characterId)

                val promptMessages = PromptBuilder.build(
                    character = character,
                    userMessage = processedContent,
                    chatHistory = chatHistory.reversed(),
                    worldBookEntries = worldBookEntries,
                    userName = config.userName,
                    memories = memories,
                    authorNote = authorNote,
                    persona = persona
                )

                // 创建空的 assistant 消息
                assistantMsgId = chatRepository.sendMessage(chatId, "", "assistant")

                // 流式接收
                chatApiService.streamChat(promptMessages, config).collect { chunk ->
                    _streamingText.value += chunk
                    chatRepository.appendToMessage(assistantMsgId, chunk)
                }

                // 对 AI 回复执行正则脚本（类型 1 = AI 回复）
                if (assistantMsgId != null) {
                    val assistantMsg = chatRepository.getMessageById(assistantMsgId!!)
                    if (assistantMsg != null && assistantMsg.content.isNotBlank()) {
                        val processedReply = scriptRepository.applyScripts(characterId, assistantMsg.content, 1)
                        if (processedReply != assistantMsg.content) {
                            chatRepository.updateMessageContent(assistantMsgId!!, processedReply)
                        }
                    }
                }
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
                // 删除空的 assistant 消息
                if (assistantMsgId != null) {
                    val msg = chatRepository.getMessageById(assistantMsgId!!)
                    if (msg != null && msg.content.isBlank()) {
                        chatRepository.deleteMessage(msg.id)
                    }
                }
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
            }
        }
    }

    fun stopGeneration() {
        streamingJob?.cancel()
        streamingJob = null
        _isGenerating.value = false
        _streamingText.value = ""
    }

    fun continueGeneration() {
        val lastMsg = messages.value.lastOrNull { it.role == "assistant" }
        if (lastMsg == null || _isGenerating.value) return

        streamingJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = lastMsg.content // Show existing content while streaming

            try {
                val character = _character.value ?: return@launch
                val config = apiConfigStore.configFlow.first()
                val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

                val worldBookEntries = if (character.worldBookId != null) {
                    val lastUserMsg = messages.value.lastOrNull { it.role == "user" }
                    worldBookRepository.matchEntries(character.worldBookId, lastUserMsg?.content ?: "")
                } else {
                    emptyList()
                }

                val memories = memoryRepository.getRelevantMemories(characterId, "")
                val authorNote = authorNoteDao.getAuthorNoteSync(characterId)
                val persona = personaRepository.getEffectivePersona(characterId)

                val promptMessages = PromptBuilder.build(
                    character = character,
                    userMessage = "", // Empty user message for continue
                    chatHistory = chatHistory.reversed(),
                    worldBookEntries = worldBookEntries,
                    userName = config.userName,
                    memories = memories,
                    authorNote = authorNote,
                    persona = persona
                )

                var newContent = lastMsg.content
                chatApiService.streamChat(promptMessages, config).collect { chunk ->
                    newContent += chunk
                    _streamingText.value = newContent
                    chatRepository.appendToMessage(lastMsg.id, chunk)
                }

                // Apply regex scripts to the new content
                val processedReply = scriptRepository.applyScripts(characterId, newContent, 1)
                if (processedReply != newContent) {
                    chatRepository.updateMessageContent(lastMsg.id, processedReply)
                }
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
            }
        }
    }

    fun regenerate(messageId: Long) {
        viewModelScope.launch {
            val msg = messages.value.find { it.id == messageId }
            if (msg == null || msg.role != "assistant") return@launch

            // 找到上一条用户消息
            val allMessages = messages.value
            val msgIndex = allMessages.indexOfFirst { it.id == messageId }
            val userMsg = allMessages.take(msgIndex).lastOrNull { it.role == "user" }
            if (userMsg == null) return@launch

            // 保存当前回复作为旧 swipe，然后生成新的
            _isGenerating.value = true
            _streamingText.value = ""

            try {
                val character = _character.value ?: return@launch
                val config = apiConfigStore.configFlow.first()
                val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

                val worldBookEntries = if (character.worldBookId != null) {
                    worldBookRepository.matchEntries(character.worldBookId, userMsg.content)
                } else {
                    emptyList()
                }

                val memories = memoryRepository.getRelevantMemories(characterId, userMsg.content)
                memoryRepository.touchMemories(memories.map { it.id })

                val authorNote = authorNoteDao.getAuthorNoteSync(characterId)
                val persona = personaRepository.getEffectivePersona(characterId)

                val promptMessages = PromptBuilder.build(
                    character = character,
                    userMessage = userMsg.content,
                    chatHistory = chatHistory.reversed(),
                    worldBookEntries = worldBookEntries,
                    userName = config.userName,
                    memories = memories,
                    authorNote = authorNote,
                    persona = persona
                )

                // 流式接收新回复
                var newContent = ""
                chatApiService.streamChat(promptMessages, config).collect { chunk ->
                    newContent += chunk
                    _streamingText.value = newContent
                }

                // 将新回复添加为 swipe
                chatRepository.addSwipe(messageId, newContent)
                // 更新 content 为新回复
                chatRepository.updateMessageContent(messageId, newContent)

                // 对新回复执行正则脚本
                val processedReply = scriptRepository.applyScripts(characterId, newContent, 1)
                if (processedReply != newContent) {
                    chatRepository.updateMessageContent(messageId, processedReply)
                }
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
            }
        }
    }

    fun swipeLeft(messageId: Long) {
        viewModelScope.launch {
            val msg = messages.value.find { it.id == messageId } ?: return@launch
            val newIndex = msg.swipeIndex - 1
            if (newIndex >= 0) {
                chatRepository.switchSwipe(messageId, newIndex)
            }
        }
    }

    fun swipeRight(messageId: Long) {
        viewModelScope.launch {
            val msg = messages.value.find { it.id == messageId } ?: return@launch
            val swipes = parseSwipeContent(msg.swipeContent)
            val newIndex = msg.swipeIndex + 1
            if (newIndex < swipes.size) {
                chatRepository.switchSwipe(messageId, newIndex)
            }
        }
    }

    fun getSwipeInfo(messageId: Long): Pair<Int, Int> {
        val msg = messages.value.find { it.id == messageId } ?: return Pair(0, 0)
        val swipes = parseSwipeContent(msg.swipeContent)
        val count = if (swipes.isEmpty()) 1 else swipes.size
        return Pair(msg.swipeIndex + 1, count)
    }

    private fun parseSwipeContent(json: String): List<String> {
        if (json == "[]" || json.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
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

    // 分支操作
    private val _branches = MutableStateFlow<List<Long?>>(emptyList())
    val branches: StateFlow<List<Long?>> = _branches.asStateFlow()

    private val _currentBranchIndex = MutableStateFlow(0)
    val currentBranchIndex: StateFlow<Int> = _currentBranchIndex.asStateFlow()

    fun loadBranches() {
        viewModelScope.launch {
            val branchIds = chatRepository.getBranchIds(chatId)
            _branches.value = branchIds
            _currentBranchIndex.value = branchIds.size - 1 // 默认显示最新分支
        }
    }

    fun switchBranch(index: Int) {
        val branchIds = _branches.value
        if (index < 0 || index >= branchIds.size) return
        val branchId = branchIds[index] ?: return

        viewModelScope.launch {
            chatRepository.switchBranch(chatId, branchId)
            _currentBranchIndex.value = index
        }
    }

    fun createBranchFromMessage(messageId: Long) {
        viewModelScope.launch {
            val newBranchId = System.currentTimeMillis()
            chatRepository.createBranch(chatId, messageId, newBranchId)
            loadBranches()
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
}
