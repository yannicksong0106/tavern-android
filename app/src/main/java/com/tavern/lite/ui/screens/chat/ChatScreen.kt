package com.tavern.lite.ui.screens.chat

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.R
import com.tavern.lite.ui.components.BackgroundPickerSheet
import com.tavern.lite.ui.components.LoadingDots
import com.tavern.lite.ui.screens.chat.components.BookmarkSheet
import com.tavern.lite.ui.screens.chat.components.SummarySheet
import com.tavern.lite.ui.screens.chat.components.BranchNavigationBar
import com.tavern.lite.ui.screens.chat.components.ChattinessSheet
import com.tavern.lite.ui.screens.chat.components.ChatBackground
import com.tavern.lite.ui.screens.chat.components.ChatSearchBar
import com.tavern.lite.ui.screens.chat.components.ChatTopBar
import com.tavern.lite.ui.screens.chat.components.DeleteConfirmDialog
import com.tavern.lite.ui.screens.chat.components.EditMessageDialog
import com.tavern.lite.ui.screens.chat.components.InputBar
import com.tavern.lite.ui.screens.chat.components.MessageBubble
import kotlinx.coroutines.launch
import java.io.File

private const val PROACTIVE_TRIGGER_DELAY_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterId: Long,
    chatId: Long,
    onBack: () -> Unit,
    onVnMode: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val character by viewModel.character.collectAsStateWithLifecycle()
    val messages by viewModel.displayMessages.collectAsStateWithLifecycle()
    val allMessagesLoaded by viewModel.allMessagesLoaded.collectAsStateWithLifecycle()
    val isGenerating by viewModel.streamingManager.isGenerating.collectAsStateWithLifecycle()
    val branchEntities by viewModel.branchManager.branchEntities.collectAsStateWithLifecycle()
    val currentBranchId by viewModel.branchManager.currentBranchId.collectAsStateWithLifecycle()
    val showBookmarksOnly by viewModel.branchManager.showBookmarksOnly.collectAsStateWithLifecycle()
    val backgroundPath by viewModel.backgroundPath.collectAsStateWithLifecycle()
    val bubbleStyle by viewModel.bubbleStyle.collectAsStateWithLifecycle()
    val isGroupChat by viewModel.isGroupChat.collectAsStateWithLifecycle()
    val groupCharacters by viewModel.groupCharacters.collectAsStateWithLifecycle()
    val respondingCharacter by viewModel.respondingCharacter.collectAsStateWithLifecycle()
    val characterChattiness by viewModel.groupChatSettingsManager.characterChattiness.collectAsStateWithLifecycle()
    val groupChattiness by viewModel.groupChatSettingsManager.groupChattiness.collectAsStateWithLifecycle()
    val groupCharacterChattiness by viewModel.groupChatSettingsManager.groupCharacterChattiness.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchManager.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchManager.searchResults.collectAsStateWithLifecycle()
    val currentSearchIndex by viewModel.searchManager.currentSearchIndex.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.speechManager.isSpeaking.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.speechManager.speakingMessageId.collectAsStateWithLifecycle()
    val estimatedContextTokens by viewModel.estimatedContextTokens.collectAsStateWithLifecycle()
    val isListening by viewModel.speechManager.isListening.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val isGeneratingSummary by viewModel.isGeneratingSummary.collectAsStateWithLifecycle()
    val schedulingStrategy by viewModel.groupChatSettingsManager.schedulingStrategy.collectAsStateWithLifecycle()
    val messageIntervalMs by viewModel.groupChatSettingsManager.messageIntervalMs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showChattinessSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSummarySheet by remember { mutableStateOf(false) }

    if (showBackgroundPicker) {
        BackgroundPickerSheet(
            currentBackgroundPath = backgroundPath,
            onSelectPreset = { preset ->
                viewModel.setChatBackground("preset:${preset.id}")
            },
            onSelectImage = { uri ->
                val bgDir = File(context.filesDir, "backgrounds")
                bgDir.mkdirs()
                val bgFile = File(bgDir, "chat_bg_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    bgFile.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.setChatBackground(bgFile.absolutePath)
            },
            onClear = { viewModel.clearChatBackground() },
            onDismiss = { showBackgroundPicker = false }
        )
    }

    if (showChattinessSheet) {
        ChattinessSheet(
            isGroupChat = isGroupChat,
            characterName = character?.name ?: "",
            characterChattiness = characterChattiness,
            groupChattiness = groupChattiness,
            groupCharacters = groupCharacters,
            groupCharacterChattiness = groupCharacterChattiness,
            schedulingStrategy = schedulingStrategy,
            messageIntervalMs = messageIntervalMs,
            onCharacterChattinessChange = { viewModel.groupChatSettingsManager.updateCharacterChattiness(it) },
            onGroupChattinessChange = { viewModel.groupChatSettingsManager.updateGroupChattiness(it) },
            onGroupCharacterChattinessChange = { id, value -> viewModel.groupChatSettingsManager.updateGroupCharacterChattiness(id, value) },
            onSchedulingStrategyChange = { viewModel.groupChatSettingsManager.updateSchedulingStrategy(it) },
            onMessageIntervalChange = { viewModel.groupChatSettingsManager.updateMessageInterval(it) },
            onDismiss = { showChattinessSheet = false }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.branchManager.loadBranches()
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.speechManager.startVoiceInput { result ->
                inputText = if (inputText.isBlank()) result else "$inputText $result"
            }
        } else {
            Toast.makeText(context, context.getString(R.string.voice_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }
    var selectedImagePaths by remember { mutableStateOf(listOf<String>()) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val imageDir = java.io.File(context.filesDir, "chat_images")
            imageDir.mkdirs()
            val newPaths = uris.mapNotNull { uri ->
                try {
                    val file = java.io.File(imageDir, "img_${System.currentTimeMillis()}_${uris.indexOf(uri)}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file.absolutePath
                } catch (_: Exception) { null }
            }
            selectedImagePaths = selectedImagePaths + newPaths
        }
    }
    var selectedMessageId by remember { mutableStateOf<Long?>(null) }
    val pinnedMessages by viewModel.pinnedMessages.collectAsStateWithLifecycle()
    // O(1) 查找集合，避免在 LazyColumn items 中重复 O(n) 扫描
    val pinnedMessageIds by remember { derivedStateOf { pinnedMessages.map { it.id }.toSet() } }
    // 全量消息（用于引用跳转、搜索索引映射等需要完整列表的场景）
    val allMessages by viewModel.messages.collectAsStateWithLifecycle()
    // 预计算消息 ID → 索引映射（基于全量消息，用于引用跳转）
    val messageIdToIndex by remember(allMessages) {
        derivedStateOf { allMessages.mapIndexed { i, m -> m.id to i }.toMap() }
    }
    // displayMessages 的 ID → 索引映射（用于搜索高亮和分页跳转）
    val displayIdToIndex by remember(messages) {
        derivedStateOf { messages.mapIndexed { i, m -> m.id to i }.toMap() }
    }
    // 全量消息 ID 集合，用于快速判断搜索结果是否在当前显示范围内
    val displayMessageIds by remember(messages) {
        derivedStateOf { messages.map { it.id }.toSet() }
    }
    // 搜索结果映射到 displayMessages 索引
    val searchResultSet by remember(searchResults, displayIdToIndex, allMessages) {
        derivedStateOf {
            searchResults.mapNotNull { fullIdx ->
                if (fullIdx < allMessages.size) displayIdToIndex[allMessages[fullIdx].id] else null
            }.toSet()
        }
    }
    val currentSearchDisplayIndex by remember(searchResults, currentSearchIndex, displayIdToIndex, allMessages) {
        derivedStateOf {
            if (currentSearchIndex < 0 || currentSearchIndex >= searchResults.size) -1
            else {
                val fullIdx = searchResults[currentSearchIndex]
                if (fullIdx < allMessages.size) displayIdToIndex[allMessages[fullIdx].id] ?: -1 else -1
            }
        }
    }
    // 群聊角色 O(1) 查找
    val groupCharacterMap by remember(groupCharacters) {
        derivedStateOf { groupCharacters.associateBy { it.id } }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showBookmarksSheet) {
        BookmarkSheet(
            pinnedMessages = pinnedMessages,
            onMessageClick = { messageId ->
                showBookmarksSheet = false
                val index = displayIdToIndex[messageId]
                if (index != null) {
                    scope.launch { listState.animateScrollToItem(index) }
                } else {
                    viewModel.loadMoreMessages()
                }
            },
            onDismiss = { showBookmarksSheet = false }
        )
    }

    if (showSummarySheet) {
        SummarySheet(
            summaries = summaries,
            isGenerating = isGeneratingSummary,
            onGenerate = { viewModel.generateSummary() },
            onDelete = { viewModel.deleteSummary(it) },
            onDismiss = { showSummarySheet = false }
        )
    }

    // 引用消息点击跳转（基于 displayMessages 索引）
    val scrollToMessage: (Long) -> Unit = remember(displayIdToIndex) {
        { messageId: Long ->
            val index = displayIdToIndex[messageId]
            if (index != null) {
                scope.launch { listState.animateScrollToItem(index) }
            } else {
                // 消息不在当前显示范围，加载更多
                viewModel.loadMoreMessages()
            }
        }
    }
    val markwon = viewModel.markwon

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) true
            else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= info.totalItemsCount - 2
            }
        }
    }
    // 自动滚动到底部：使用 totalMessageCount 检测新消息（不受分页影响）
    val totalCount = remember { derivedStateOf { viewModel.totalMessageCount } }
    LaunchedEffect(totalCount.value, isAtBottom) {
        if (totalCount.value > 0 && isAtBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 滚动时关闭操作栏
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            selectedMessageId = null
        }
    }

    // 搜索结果滚动：映射全量索引到 displayMessages 索引
    LaunchedEffect(currentSearchIndex) {
        if (currentSearchIndex >= 0 && searchResults.isNotEmpty()) {
            val fullIndex = searchResults[currentSearchIndex]
            if (fullIndex < allMessages.size) {
                val messageId = allMessages[fullIndex].id
                val displayIndex = displayIdToIndex[messageId]
                if (displayIndex != null) {
                    listState.animateScrollToItem(displayIndex)
                } else {
                    // 消息不在当前显示范围，加载更多直到包含它
                    viewModel.loadMoreMessages()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(PROACTIVE_TRIGGER_DELAY_MS)
        viewModel.streamingManager.triggerProactiveIfNeeded()
    }

    var editingMessage by remember { mutableStateOf<com.tavern.lite.data.db.entity.MessageEntity?>(null) }
    editingMessage?.let { message ->
        EditMessageDialog(
            originalContent = message.content,
            onConfirm = { newContent ->
                viewModel.editMessage(message.id, newContent)
                editingMessage = null
            },
            onDismiss = { editingMessage = null }
        )
    }

    var deletingMessageId by remember { mutableStateOf<Long?>(null) }
    deletingMessageId?.let { messageId ->
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteMessage(messageId)
                deletingMessageId = null
            },
            onDismiss = { deletingMessageId = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                character = character,
                isGroupChat = isGroupChat,
                groupCharacters = groupCharacters,
                isGenerating = isGenerating,
                respondingCharacter = respondingCharacter,
                pinnedMessages = pinnedMessages,
                onBack = onBack,
                onVnMode = onVnMode,
                onToggleSearch = { showSearch = !showSearch },
                onShowBookmarksSheet = { showBookmarksSheet = true },
                onShowSummarySheet = { showSummarySheet = true },
                onShowChattinessSheet = { showChattinessSheet = true },
                onShowBackgroundPicker = { showBackgroundPicker = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ChatBackground(
                backgroundPath = backgroundPath,
                onBackgroundMissing = { viewModel.clearChatBackground() }
            )

            ChatSearchBar(
                visible = showSearch,
                searchQuery = searchQuery,
                searchResults = searchResults,
                currentSearchIndex = currentSearchIndex,
                onQueryChange = { viewModel.searchManager.searchMessages(it) },
                onPreviousResult = { viewModel.searchManager.previousSearchResult() },
                onNextResult = { viewModel.searchManager.nextSearchResult() },
                onClose = {
                    showSearch = false
                    viewModel.searchManager.clearSearch()
                }
            )

            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                val haptic = LocalHapticFeedback.current
                if (branchEntities.size > 1) {
                    BranchNavigationBar(
                        currentIndex = branchEntities.indexOfFirst { it.id == currentBranchId }.coerceAtLeast(0),
                        totalBranches = branchEntities.size,
                        onPrevious = {
                            val idx = branchEntities.indexOfFirst { it.id == currentBranchId }
                            if (idx > 0) viewModel.branchManager.switchBranch(branchEntities[idx - 1].id)
                        },
                        onNext = {
                            val idx = branchEntities.indexOfFirst { it.id == currentBranchId }
                            if (idx < branchEntities.size - 1) viewModel.branchManager.switchBranch(branchEntities[idx + 1].id)
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!allMessagesLoaded) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.load_more_messages),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                            .clickable { viewModel.loadMoreMessages() }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        items(
                            messages.size,
                            key = { messages[it].id },
                            contentType = { messages[it].role }
                        ) { index ->
                            val message = messages[index]
                            val prevMessage = if (index > 0) messages[index - 1] else null
                            val nextMessage = if (index < messages.size - 1) messages[index + 1] else null
                            val isSameRoleAsPrev = if (isGroupChat && message.role == "assistant") {
                                prevMessage?.role == message.role && prevMessage?.characterId == message.characterId
                            } else {
                                prevMessage?.role == message.role
                            }
                            val isSameRoleAsNext = if (isGroupChat && message.role == "assistant") {
                                nextMessage?.role == message.role && nextMessage?.characterId == message.characterId
                            } else {
                                nextMessage?.role == message.role
                            }
                            val topSpacing = if (isSameRoleAsPrev) 3.dp else 8.dp
                            val msgChar = if (isGroupChat && message.role == "assistant") {
                                message.characterId?.let { groupCharacterMap[it] }
                            } else null
                            val msgCharName = msgChar?.name ?: character?.name ?: ""
                            val msgAvatarPath = msgChar?.avatarPath ?: character?.avatarPath
                            // 引用消息查找 — O(1) 索引查找
                            val quotedMsg = remember(message.replyToId, messageIdToIndex) {
                                message.replyToId?.let { qid -> messageIdToIndex[qid]?.let { messages[it] } }
                            }
                            val quotedName = remember(quotedMsg) {
                                quotedMsg?.let { qm ->
                                    if (qm.role == "user") null
                                    else if (isGroupChat) qm.characterId?.let { groupCharacterMap[it]?.name }
                                    else character?.name
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .animateItem()
                                    .padding(top = topSpacing)
                            ) {
                                MessageBubble(
                                    message = message,
                                    characterName = msgCharName,
                                    markwon = markwon,
                                    bubbleStyle = bubbleStyle,
                                    showName = !isSameRoleAsPrev,
                                    showTimestamp = !isSameRoleAsNext,
                                    isGroupedTop = isSameRoleAsPrev,
                                    isGroupedBottom = isSameRoleAsNext,
                                    avatarPath = msgAvatarPath,
                                    showAvatar = isGroupChat && message.role == "assistant" && !isSameRoleAsPrev,
                                    isSearchResult = index in searchResultSet,
                                    isCurrentSearchResult = currentSearchDisplayIndex == index,
                                    isSpeaking = speakingMessageId == message.id,
                                    showActionBar = selectedMessageId == message.id,
                                    isPinned = message.id in pinnedMessageIds,
                                    onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedMessageId = if (selectedMessageId == message.id) null else message.id
                                    },
                                    onRegenerate = { viewModel.streamingManager.regenerate(message.id) },
                                    onEdit = { editingMessage = message },
                                    onDelete = { deletingMessageId = message.id },
                                    onSwipeLeft = { viewModel.swipeLeft(message.id) },
                                    onSwipeRight = { viewModel.swipeRight(message.id) },
                                    onReply = { viewModel.streamingManager.regenerate(message.id) },
                                    onSpeak = { viewModel.speechManager.speakMessage(message) },
                                    onStopSpeak = { viewModel.speechManager.stopSpeaking() },
                                    onPinToggle = { viewModel.togglePinMessage(message.id) },
                                    onCopy = {
                                        viewModel.copyMessage(context, message.id)
                                        Toast.makeText(context, context.getString(R.string.copy_message_toast), Toast.LENGTH_SHORT).show()
                                    },
                                    onResend = { viewModel.streamingManager.resendUserMessage(message.id) },
                                    onBranch = { viewModel.branchManager.createBranchFromMessage(message.id, "分支 ${message.id}") },
                                    quotedMessage = quotedMsg,
                                    quotedMessageName = quotedName,
                                    onQuoteClick = scrollToMessage
                                )
                            }
                        }

                        if (isGenerating) {
                            item {
                                val typingName = if (isGroupChat) {
                                    respondingCharacter?.name ?: character?.name ?: ""
                                } else {
                                    character?.name ?: ""
                                }
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LoadingDots()
                                    if (typingName.isNotBlank()) {
                                        Text(
                                            text = " $typingName ${stringResource(R.string.typing)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isAtBottom) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        // 先加载全部消息再滚动到底部
                                        if (!allMessagesLoaded) viewModel.loadMoreMessages()
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.scroll_to_bottom),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                InputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() || selectedImagePaths.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedMessageId = null
                            // Handle /imagine command for image generation
                            if (inputText.trimStart().startsWith("/imagine ")) {
                                val prompt = inputText.trimStart().removePrefix("/imagine ").trim()
                                if (prompt.isNotBlank()) {
                                    viewModel.streamingManager.generateImage(prompt)
                                }
                            } else {
                                viewModel.streamingManager.sendMessage(inputText, selectedImagePaths)
                            }
                            inputText = ""
                            selectedImagePaths = emptyList()
                        }
                    },
                    onStop = { viewModel.streamingManager.stopGeneration() },
                    onContinue = { viewModel.streamingManager.continueGeneration() },
                    isGenerating = isGenerating,
                    showContinue = messages.lastOrNull()?.role == "assistant" && !isGenerating,
                    isGroupChat = isGroupChat,
                    groupCharacters = groupCharacters,
                    contextTokens = estimatedContextTokens,
                    inputTokens = if (inputText.isNotBlank()) viewModel.estimateInputTokens(inputText) else 0,
                    isListening = isListening,
                    onVoiceInput = {
                        if (isListening) {
                            viewModel.speechManager.stopVoiceInput()
                        } else {
                            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    imagePaths = selectedImagePaths,
                    onAddImage = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemoveImage = { path ->
                        selectedImagePaths = selectedImagePaths.filter { it != path }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
