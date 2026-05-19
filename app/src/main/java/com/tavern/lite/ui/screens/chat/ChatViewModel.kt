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
import com.tavern.lite.data.repository.GroupChatRepository
import com.tavern.lite.data.repository.MemoryConsolidator
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import com.tavern.lite.network.MemoryExtractorService
import com.tavern.lite.network.PromptBuilder
import com.tavern.lite.util.SwipeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val groupChatRepository: GroupChatRepository,
    private val worldBookRepository: WorldBookRepository,
    private val chatApiService: ChatApiService,
    private val apiConfigStore: ApiConfigStore,
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val memoryAtomDao: MemoryAtomDao,
    private val memoryExtractorService: MemoryExtractorService,
    private val memoryConsolidator: MemoryConsolidator,
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

    val bubbleStyle: StateFlow<BubbleStyleConfig> = settingsStore.bubbleStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BubbleStyleConfig())

    val messages: StateFlow<List<MessageEntity>> = chatRepository.getMessagesForChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var streamingJob: Job? = null
    @Volatile private var wasCancelled = false
    private var messageCount = 0

    // 主动对话冷却机制：characterId -> last proactive timestamp
    private val _lastProactiveTime = mutableMapOf<Long, Long>()
    // 防止主动对话链式触发
    @Volatile private var isProactiveMessage = false
    // 思维链内容（DeepSeek/Qwen thinking mode），下次请求时传回
    private var lastAssistantReasoningContent: String? = null

    init {
        viewModelScope.launch {
            val char = characterRepository.getCharacterById(characterId)
            _character.value = char
            // 加载背景：对话级覆盖角色级
            val chat = chatRepository.getChatById(chatId)
            _backgroundPath.value = chat?.backgroundPath ?: char?.backgroundPath
            // 初始化消息计数（用于记忆提取频率控制）
            messageCount = chatRepository.getMessageCount(chatId)

            // 加载健谈度
            if (char != null) _characterChattiness.value = char.chattiness

            // Group chat detection
            if (chat?.isGroup == true) {
                _isGroupChat.value = true
                val chars = groupChatRepository.getCharactersForChatSync(chatId)
                _groupCharacters.value = chars
                _groupChattiness.value = chat.groupChattiness
                // 加载群聊中每个角色的健谈度
                val chatChars = groupChatRepository.getChatCharacters(chatId)
                _groupCharacterChattiness.value = chatChars.associate { it.characterId to it.chattiness }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _isGenerating.value) return

        if (_isGroupChat.value) {
            // 先检查是否是 @ 消息
            if (!handleAtMention(content)) {
                sendGroupChatMessage(content)
            }
        } else {
            sendSingleChatMessage(content)
        }
    }

    private fun sendSingleChatMessage(content: String) {
        wasCancelled = false
        streamingJob = viewModelScope.launch {
            // 只有非空内容才发送用户消息（主动发言时不发送）
            val processedContent = if (content.isNotBlank()) {
                val processed = scriptRepository.applyScripts(characterId, content, 0)
                chatRepository.sendMessage(chatId, processed, "user")
                processed
            } else {
                ""
            }

            _isGenerating.value = true

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

                // 记忆检索（新版结构化记忆）
                val memoryAtoms = memoryAtomDao.getRelevantAtoms(characterId, 10)
                memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
                // Legacy fallback
                val memories = if (memoryAtoms.isEmpty()) {
                    memoryRepository.getRelevantMemories(characterId, processedContent)
                } else emptyList()

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
                    memoryAtoms = memoryAtoms,
                    authorNote = authorNote,
                    persona = persona
                )

                // 静默累积完整回复
                var responseBuffer = ""
                chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
                    responseBuffer += chunk
                }
                // 保存思维链内容，下次请求时传回
                lastAssistantReasoningContent = chatApiService.lastReasoningContent

                if (responseBuffer.isBlank()) return@launch

                // 写入完整消息
                assistantMsgId = chatRepository.sendMessage(chatId, responseBuffer, "assistant")

                // 对 AI 回复执行正则脚本（类型 1 = AI 回复）
                val processedReply = scriptRepository.applyScripts(characterId, responseBuffer, 1)
                if (processedReply != responseBuffer) {
                    chatRepository.updateMessageContent(assistantMsgId, processedReply)
                }

                // === 记忆提取 ===
                extractMemoryIfNeeded(characterId, character.name, processedContent, config)
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                // 用户主动停止时不拆分，避免对不完整内容操作
                if (!wasCancelled) {
                    splitIntoMultipleMessages(assistantMsgId)
                    // 触发主动对话
                    scheduleProactiveDialogue()
                }
                _isGenerating.value = false
            }
        }
    }

    private fun sendGroupChatMessage(content: String) {
        wasCancelled = false
        streamingJob = viewModelScope.launch {
            // 只有非空内容才发送用户消息（主动发言时不发送）
            val processedContent = if (content.isNotBlank()) {
                val processed = scriptRepository.applyScripts(characterId, content, 0)
                chatRepository.sendMessage(chatId, processed, "user")
                processed
            } else {
                ""
            }

            _isGenerating.value = true

            try {
                val characters = _groupCharacters.value
                if (characters.isEmpty()) return@launch

                val config = apiConfigStore.configFlow.first()
                val persona = personaRepository.getEffectivePersona(characterId)
                val characterMap = characters.associateBy { it.id }

                // Load history once, update after each character responds
                var chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

                for (char in characters) {
                    if (wasCancelled) break

                    _respondingCharacter.value = char

                    val worldBookEntries = if (char.worldBookId != null) {
                        worldBookRepository.matchEntries(char.worldBookId, processedContent)
                    } else emptyList()

                    val memoryAtoms = memoryAtomDao.getRelevantAtoms(char.id, 10)
                    memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
                    val memories = if (memoryAtoms.isEmpty()) {
                        memoryRepository.getRelevantMemories(char.id, processedContent)
                    } else emptyList()

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
                        persona = persona
                    )

                    // 静默累积完整回复
                    var fullResponse = ""
                    chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
                        fullResponse += chunk
                    }
                    lastAssistantReasoningContent = chatApiService.lastReasoningContent

                    // Strip [CharacterName]: prefix from final content
                    val cleanContent = cleanCharacterPrefix(fullResponse, char.name)

                    if (cleanContent.isNotBlank()) {
                        // 写入完整消息
                        val msgId = chatRepository.sendMessage(chatId, cleanContent, "assistant", char.id)

                        // Apply regex scripts
                        val processedReply = scriptRepository.applyScripts(char.id, cleanContent, 1)
                        if (processedReply != cleanContent) {
                            chatRepository.updateMessageContent(msgId, processedReply)
                        }

                        // Split into multiple messages
                        if (!wasCancelled) {
                            splitIntoMultipleMessages(msgId)
                        }
                    }

                    // Per-character memory extraction
                    extractMemoryIfNeeded(char.id, char.name, processedContent, config)

                    // Reload history so next character sees this one's response
                    chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

                    // Delay between characters
                    if (char != characters.last() && !wasCancelled) {
                        delay(500 + (Math.random() * 500).toLong())
                    }
                }
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                _isGenerating.value = false
                _respondingCharacter.value = null
                // 触发群聊主动对话
                if (!wasCancelled) {
                    scheduleGroupProactiveDialogue()
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

        // 按双换行拆分段落，过滤空段
        val paragraphs = content.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.size <= 1) return // 只有一段，不需要拆分

        val msgCharacterId = msg.characterId

        // 更新原消息为第一段
        chatRepository.updateMessageContent(assistantMsgId, paragraphs[0])

        // 后续段落逐条发送，带随机延迟模拟真人打字
        for (i in 1 until paragraphs.size) {
            val len = paragraphs[i].length
            val baseDelay = (400L + len * 30L).coerceIn(500L, 2000L)
            val jitter = (Math.random() * 400 - 200).toLong()
            delay(baseDelay + jitter)
            chatRepository.sendMessage(chatId, paragraphs[i], "assistant", msgCharacterId)
        }
    }

    // ==================== 主动对话逻辑 ====================

    /**
     * 单聊主动对话：延迟 2-4 秒后触发角色主动延伸话题
     */
    private fun scheduleProactiveDialogue() {
        // 防止链式触发：主动消息不再触发主动消息
        if (_isGroupChat.value || isProactiveMessage) return
        val character = _character.value ?: return
        val chattiness = character.chattiness
        if (chattiness <= 0) return  // 健谈度为 0 不触发

        // 根据健谈度计算概率 (chattiness/100)
        val probability = chattiness / 100.0
        if (Math.random() > probability) return

        // 延迟 2-4 秒后触发
        viewModelScope.launch {
            delay(2000 + (Math.random() * 2000).toLong())
            if (!_isGenerating.value) {
                sendProactiveSingleMessage()
            }
        }
    }

    /**
     * 单聊主动发言：构建主动对话 prompt 并发送
     */
    private fun sendProactiveSingleMessage() {
        streamingJob = viewModelScope.launch {
            _isGenerating.value = true
            isProactiveMessage = true  // 标记为主动消息，防止链式触发
            try {
                val character = _character.value ?: return@launch
                val config = apiConfigStore.configFlow.first()
                val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
                if (chatHistory.isEmpty()) return@launch

                val persona = personaRepository.getEffectivePersona(characterId)

                // 构建带主动发言指令的 prompt
                val promptMessages = PromptBuilder.buildProactive(
                    character = character,
                    chatHistory = chatHistory.reversed(),
                    userName = config.userName,
                    persona = persona
                )

                var responseBuffer = ""
                chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
                    responseBuffer += chunk
                }
                lastAssistantReasoningContent = chatApiService.lastReasoningContent

                if (responseBuffer.isBlank()) return@launch

                // 清理可能的 [CharName]: 前缀
                val cleanContent = cleanCharacterPrefix(responseBuffer, character.name)
                if (cleanContent.isNotBlank()) {
                    // 应用正则脚本
                    val processedReply = scriptRepository.applyScripts(characterId, cleanContent, 1)
                    val finalContent = if (processedReply != cleanContent) processedReply else cleanContent
                    chatRepository.sendMessage(chatId, finalContent, "assistant")

                    // 记忆提取
                    extractMemoryIfNeeded(characterId, character.name, "", config)
                }
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                _isGenerating.value = false
                isProactiveMessage = false
            }
        }
    }

    /**
     * 群聊主动对话：按概率触发角色主动发言
     */
    private fun scheduleGroupProactiveDialogue() {
        // 防止链式触发：主动消息不再触发主动消息
        if (!_isGroupChat.value || isProactiveMessage) return
        val characters = _groupCharacters.value
        if (characters.isEmpty()) return

        // 获取群聊健谈度（取所有角色的最大值，让最健谈的角色主导）
        val maxChattiness = characters.maxOf { it.chattiness }
        if (maxChattiness <= 0) return

        // 30-50% 概率触发（根据最高健谈度调整）
        val probability = 0.3 + (maxChattiness / 100.0) * 0.2  // 30%-50%
        if (Math.random() > probability) return

        // 延迟 1-3 秒
        viewModelScope.launch {
            delay(1000 + (Math.random() * 2000).toLong())
            if (!_isGenerating.value) {
                // 选择下一个发言角色（排除刚发言的，考虑冷却）
                val nextChar = selectNextProactiveCharacter(characters)
                if (nextChar != null) {
                    sendProactiveGroupMessage(nextChar)
                }
            }
        }
    }

    /**
     * 选择下一个主动发言的角色（按健谈度加权随机，带冷却机制）
     */
    private fun selectNextProactiveCharacter(characters: List<CharacterEntity>): CharacterEntity? {
        val now = System.currentTimeMillis()
        val cooldownMs = 30_000L  // 30 秒冷却

        // 过滤掉冷却中的角色
        val available = characters.filter { char ->
            val lastTime = _lastProactiveTime[char.id] ?: 0
            now - lastTime > cooldownMs
        }

        if (available.isEmpty()) return null

        // 按健谈度加权随机选择
        val totalWeight = available.sumOf { it.chattiness }
        if (totalWeight <= 0) return available.random()

        var random = Math.random() * totalWeight
        for (char in available) {
            random -= char.chattiness
            if (random <= 0) {
                _lastProactiveTime[char.id] = now
                return char
            }
        }

        val selected = available.last()
        _lastProactiveTime[selected.id] = now
        return selected
    }

    /**
     * 群聊主动发言：让指定角色主动插话
     */
    private fun sendProactiveGroupMessage(character: CharacterEntity) {
        streamingJob = viewModelScope.launch {
            _isGenerating.value = true
            _respondingCharacter.value = character
            isProactiveMessage = true  // 标记为主动消息，防止链式触发

            try {
                val characters = _groupCharacters.value
                val config = apiConfigStore.configFlow.first()
                val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
                if (chatHistory.isEmpty()) return@launch

                val persona = personaRepository.getEffectivePersona(characterId)
                val characterMap = characters.associateBy { it.id }

                val promptMessages = PromptBuilder.buildGroupProactive(
                    characters = characters,
                    respondingCharacter = character,
                    chatHistory = chatHistory.reversed(),
                    characterMap = characterMap,
                    userName = config.userName,
                    persona = persona
                )

                var fullResponse = ""
                chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
                    fullResponse += chunk
                }
                lastAssistantReasoningContent = chatApiService.lastReasoningContent

                val cleanContent = cleanCharacterPrefix(fullResponse, character.name)
                if (cleanContent.isNotBlank()) {
                    // 应用正则脚本
                    val processedReply = scriptRepository.applyScripts(character.id, cleanContent, 1)
                    val finalContent = if (processedReply != cleanContent) processedReply else cleanContent
                    chatRepository.sendMessage(chatId, finalContent, "assistant", character.id)

                    // 记忆提取
                    extractMemoryIfNeeded(character.id, character.name, "", config)
                }
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                _isGenerating.value = false
                _respondingCharacter.value = null
                isProactiveMessage = false
            }
        }
    }

    /**
     * 处理 @ 消息：检测 @ 角色名 并只让该角色回复
     */
    private fun handleAtMention(content: String): Boolean {
        if (!_isGroupChat.value) return false

        // 检测 @角色名 格式（角色名后跟空格或到达末尾）
        val match = AT_MENTION_PATTERN.find(content)
        if (match != null) {
            val mentionedName = match.groupValues[1]
            val mentionedChar = _groupCharacters.value.find {
                it.name.equals(mentionedName, ignoreCase = true)
            }

            if (mentionedChar != null) {
                // 移除 @前缀，发送给指定角色
                val cleanContent = content.replaceFirst(AT_MENTION_PATTERN, "").trim()
                sendDirectMessage(cleanContent, mentionedChar)
                return true
            }
        }

        return false
    }

    /**
     * 发送定向消息：只让指定角色回复
     */
    private fun sendDirectMessage(content: String, targetCharacter: CharacterEntity) {
        wasCancelled = false
        streamingJob = viewModelScope.launch {
            // 发送用户消息
            if (content.isNotBlank()) {
                val processedContent = scriptRepository.applyScripts(characterId, content, 0)
                chatRepository.sendMessage(chatId, processedContent, "user")
            }

            _isGenerating.value = true
            _respondingCharacter.value = targetCharacter

            try {
                val characters = _groupCharacters.value
                val config = apiConfigStore.configFlow.first()
                val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
                val persona = personaRepository.getEffectivePersona(characterId)
                val characterMap = characters.associateBy { it.id }

                // 世界书匹配
                val worldBookEntries = if (targetCharacter.worldBookId != null) {
                    worldBookRepository.matchEntries(targetCharacter.worldBookId, content)
                } else emptyList()

                // 记忆检索
                val memoryAtoms = memoryAtomDao.getRelevantAtoms(targetCharacter.id, 10)
                memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
                val memories = if (memoryAtoms.isEmpty()) {
                    memoryRepository.getRelevantMemories(targetCharacter.id, content)
                } else emptyList()

                val promptMessages = PromptBuilder.buildGroupChat(
                    characters = characters,
                    respondingCharacter = targetCharacter,
                    userMessage = content,
                    chatHistory = chatHistory.reversed(),
                    characterMap = characterMap,
                    worldBookEntries = worldBookEntries,
                    userName = config.userName,
                    memories = memories,
                    memoryAtoms = memoryAtoms,
                    persona = persona
                )

                var fullResponse = ""
                chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
                    fullResponse += chunk
                }
                lastAssistantReasoningContent = chatApiService.lastReasoningContent

                val cleanContent = cleanCharacterPrefix(fullResponse, targetCharacter.name)
                if (cleanContent.isNotBlank()) {
                    // 应用正则脚本
                    val processedReply = scriptRepository.applyScripts(targetCharacter.id, cleanContent, 1)
                    val finalContent = if (processedReply != cleanContent) processedReply else cleanContent
                    chatRepository.sendMessage(chatId, finalContent, "assistant", targetCharacter.id)

                    // 记忆提取
                    extractMemoryIfNeeded(targetCharacter.id, targetCharacter.name, content, config)
                }
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                _isGenerating.value = false
                _respondingCharacter.value = null
            }
        }
    }

    /**
     * 清理角色名前缀，如 "[Alice]: 你好" → "你好"
     */
    private fun cleanCharacterPrefix(response: String, charName: String): String {
        val trimmed = response.trim()
        val prefix = "[$charName]"
        if (!trimmed.startsWith(prefix)) return trimmed
        val afterPrefix = trimmed.substring(prefix.length)
        // 跳过冒号和空白
        var i = 0
        while (i < afterPrefix.length && (afterPrefix[i] == ':' || afterPrefix[i] == '：' || afterPrefix[i] == ' ' || afterPrefix[i] == '\t')) {
            i++
        }
        return afterPrefix.substring(i).trim()
    }

    fun continueGeneration() {
        val lastMsg = messages.value.lastOrNull { it.role == "assistant" }
        if (lastMsg == null || _isGenerating.value) return

        streamingJob = viewModelScope.launch {
            _isGenerating.value = true

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

                val memoryAtoms = memoryAtomDao.getRelevantAtoms(characterId, 10)
                memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
                val memories = if (memoryAtoms.isEmpty()) {
                    memoryRepository.getRelevantMemories(characterId, "")
                } else emptyList()
                val authorNote = authorNoteDao.getAuthorNoteSync(characterId)
                val persona = personaRepository.getEffectivePersona(characterId)

                val promptMessages = PromptBuilder.build(
                    character = character,
                    userMessage = "", // Empty user message for continue
                    chatHistory = chatHistory.reversed(),
                    worldBookEntries = worldBookEntries,
                    userName = config.userName,
                    memories = memories,
                    memoryAtoms = memoryAtoms,
                    authorNote = authorNote,
                    persona = persona
                )

                // 静默累积新内容
                var newContent = ""
                chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
                    newContent += chunk
                }
                lastAssistantReasoningContent = chatApiService.lastReasoningContent

                if (newContent.isBlank()) return@launch

                // 追加到现有消息
                chatRepository.appendToMessage(lastMsg.id, newContent)

                // Apply regex scripts to the full content
                val fullContent = lastMsg.content + newContent
                val processedReply = scriptRepository.applyScripts(characterId, fullContent, 1)
                if (processedReply != fullContent) {
                    chatRepository.updateMessageContent(lastMsg.id, processedReply)
                }

                // 记忆提取（continue 也需要触发）
                extractMemoryIfNeeded(characterId, character.name, "", config)
            } catch (e: Exception) {
                _toastMessage.emit("API 错误: ${e.message}")
            } finally {
                _isGenerating.value = false
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

            try {
                val character = _character.value ?: return@launch
                val config = apiConfigStore.configFlow.first()
                val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)

                val worldBookEntries = if (character.worldBookId != null) {
                    worldBookRepository.matchEntries(character.worldBookId, userMsg.content)
                } else {
                    emptyList()
                }

                val memoryAtoms = memoryAtomDao.getRelevantAtoms(characterId, 10)
                memoryAtomDao.touchAtoms(memoryAtoms.map { it.id })
                val memories = if (memoryAtoms.isEmpty()) {
                    memoryRepository.getRelevantMemories(characterId, userMsg.content)
                } else emptyList()

                val authorNote = authorNoteDao.getAuthorNoteSync(characterId)
                val persona = personaRepository.getEffectivePersona(characterId)

                val promptMessages = PromptBuilder.build(
                    character = character,
                    userMessage = userMsg.content,
                    chatHistory = chatHistory.reversed(),
                    worldBookEntries = worldBookEntries,
                    userName = config.userName,
                    memories = memories,
                    memoryAtoms = memoryAtoms,
                    authorNote = authorNote,
                    persona = persona
                )

                // 静默累积新回复
                var newContent = ""
                chatApiService.streamChat(attachReasoningContent(promptMessages), config).collect { chunk ->
                    newContent += chunk
                }
                lastAssistantReasoningContent = chatApiService.lastReasoningContent

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
            val swipes = SwipeUtils.parseSwipeContent(msg.swipeContent)
            val newIndex = msg.swipeIndex + 1
            if (newIndex < swipes.size) {
                chatRepository.switchSwipe(messageId, newIndex)
            }
        }
    }

    fun getSwipeInfo(messageId: Long): Pair<Int, Int> {
        val msg = messages.value.find { it.id == messageId } ?: return Pair(0, 0)
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

    /**
     * Get the character that sent a specific message (for group chat display).
     * Returns null for user messages or single-char chats.
     */
    fun getCharacterForMessage(message: MessageEntity): CharacterEntity? {
        if (!_isGroupChat.value) return _character.value
        val charId = message.characterId ?: return null
        return _groupCharacters.value.find { it.id == charId }
    }

    /**
     * 主动发言：检查是否有未回复的消息，自动触发回复。
     * 用于进入对话时自动回复用户未回复的消息，或群聊中角色之间的互动。
     */
    /**
     * 通用记忆提取：正则快速提取 + LLM 批量提取（每 N 轮一次）
     */
    private suspend fun extractMemoryIfNeeded(
        charId: Long,
        charName: String,
        userContent: String,
        config: com.tavern.lite.data.model.ApiConfig
    ) {
        // 正则快速提取
        val userMsgId = messages.value.lastOrNull { it.role == "user" }?.id
        val quickFacts = memoryExtractorService.extractQuickFacts(charId, userContent, chatId, userMsgId)
        if (quickFacts.isNotEmpty()) {
            memoryConsolidator.insertWithDedup(quickFacts)
        }

        // LLM 批量提取（每 10 轮一次）
        messageCount++
        if (memoryExtractorService.shouldExtract(messageCount)) {
            val allMessages = chatRepository.getRecentMessages(chatId, 30)
            val llmFacts = memoryExtractorService.extractWithLLM(
                charId, allMessages.reversed(), charName, config, chatId
            )
            if (llmFacts.isNotEmpty()) {
                memoryConsolidator.insertWithDedup(llmFacts)
                memoryConsolidator.maybeConsolidate(charId)
            }
        }
    }

    fun triggerProactiveIfNeeded() {
        val currentMessages = messages.value
        if (_isGenerating.value || isProactiveMessage) return

        val lastMsg = currentMessages.lastOrNull() ?: return

        // 如果最后一条是用户消息（未回复），触发回复
        if (lastMsg.role == "user") {
            if (_isGroupChat.value) {
                sendGroupChatMessage("")
            } else {
                sendSingleChatMessage("")
            }
        }
        // 群聊：如果最后一条是某个角色的消息，让下一个角色接话
        else if (_isGroupChat.value && lastMsg.role == "assistant") {
            val characters = _groupCharacters.value
            val lastCharIndex = characters.indexOfFirst { it.id == lastMsg.characterId }
            if (lastCharIndex >= 0 && lastCharIndex < characters.size - 1) {
                // 只让下一个角色发言，而非全部
                val nextChar = characters[lastCharIndex + 1]
                sendDirectMessage("", nextChar)
            }
        }
    }

    // === 健谈度更新 ===

    /** 单聊：更新角色健谈度 */
    fun updateCharacterChattiness(value: Int) {
        _characterChattiness.value = value
        viewModelScope.launch {
            val char = _character.value ?: return@launch
            characterRepository.updateCharacter(char.copy(chattiness = value))
        }
    }

    /** 群聊：更新群聊整体健谈度 */
    fun updateGroupChattiness(value: Int) {
        _groupChattiness.value = value
        viewModelScope.launch {
            chatRepository.updateGroupChattiness(chatId, value)
        }
    }

    /** 群聊：更新某角色的健谈度 */
    fun updateGroupCharacterChattiness(characterId: Long, value: Int) {
        _groupCharacterChattiness.value = _groupCharacterChattiness.value.toMutableMap().apply {
            put(characterId, value)
        }
        viewModelScope.launch {
            groupChatRepository.updateCharacterChattiness(chatId, characterId, value)
        }
    }

    /**
     * 为 promptMessages 中最后一条 assistant 消息附加 reasoning_content，
     * 满足思维链模型（DeepSeek/Qwen）的 API 要求。
     */
    private fun attachReasoningContent(messages: List<ChatMessage>): List<ChatMessage> {
        val reasoning = lastAssistantReasoningContent ?: return messages
        // 从后往前找最后一条 assistant 消息
        for (i in messages.indices.reversed()) {
            if (messages[i].role == "assistant") {
                return messages.toMutableList().also {
                    it[i] = it[i].copy(reasoningContent = reasoning)
                }
            }
        }
        return messages
    }

    companion object {
        // 预编译的 @ 提及正则，避免每次调用重新编译
        private val AT_MENTION_PATTERN = Regex("@(\\S+?)(?:\\s|$)")
    }
}
