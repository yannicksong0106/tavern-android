package com.tavern.lite.ui.screens.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tavern.lite.R
import com.tavern.lite.ui.components.BackgroundPickerSheet
import com.tavern.lite.ui.components.CharacterAvatar
import com.tavern.lite.ui.components.LoadingDots
import com.tavern.lite.ui.components.presetBackgrounds
import com.tavern.lite.ui.screens.chat.components.BranchNavigationBar
import com.tavern.lite.ui.screens.chat.components.ChattinessSheet
import com.tavern.lite.ui.screens.chat.components.DeleteConfirmDialog
import com.tavern.lite.ui.screens.chat.components.EditMessageDialog
import com.tavern.lite.ui.screens.chat.components.InputBar
import com.tavern.lite.ui.screens.chat.components.MessageBubble
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterId: Long,
    chatId: Long,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val character by viewModel.character.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val currentBranchIndex by viewModel.currentBranchIndex.collectAsStateWithLifecycle()
    val backgroundPath by viewModel.backgroundPath.collectAsStateWithLifecycle()
    val bubbleStyle by viewModel.bubbleStyle.collectAsStateWithLifecycle()
    val isGroupChat by viewModel.isGroupChat.collectAsStateWithLifecycle()
    val groupCharacters by viewModel.groupCharacters.collectAsStateWithLifecycle()
    val respondingCharacter by viewModel.respondingCharacter.collectAsStateWithLifecycle()
    val characterChattiness by viewModel.characterChattiness.collectAsStateWithLifecycle()
    val groupChattiness by viewModel.groupChattiness.collectAsStateWithLifecycle()
    val groupCharacterChattiness by viewModel.groupCharacterChattiness.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showChattinessSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

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
            onCharacterChattinessChange = { viewModel.updateCharacterChattiness(it) },
            onGroupChattinessChange = { viewModel.updateGroupChattiness(it) },
            onGroupCharacterChattinessChange = { id, value -> viewModel.updateGroupCharacterChattiness(id, value) },
            onDismiss = { showChattinessSheet = false }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadBranches()
    }

    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val markwon = viewModel.markwon

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

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
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 搜索结果滚动
    LaunchedEffect(currentSearchIndex) {
        if (currentSearchIndex >= 0 && searchResults.isNotEmpty()) {
            val messageIndex = searchResults[currentSearchIndex]
            listState.animateScrollToItem(messageIndex)
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        viewModel.triggerProactiveIfNeeded()
    }

    var editingMessage by remember { mutableStateOf<com.tavern.lite.data.db.entity.MessageEntity?>(null) }
    if (editingMessage != null) {
        EditMessageDialog(
            originalContent = editingMessage!!.content,
            onConfirm = { newContent ->
                viewModel.editMessage(editingMessage!!.id, newContent)
                editingMessage = null
            },
            onDismiss = { editingMessage = null }
        )
    }

    var deletingMessageId by remember { mutableStateOf<Long?>(null) }
    if (deletingMessageId != null) {
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteMessage(deletingMessageId!!)
                deletingMessageId = null
            },
            onDismiss = { deletingMessageId = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isGroupChat) {
                            Box(modifier = Modifier.size(36.dp)) {
                                val chars = groupCharacters.take(2)
                                chars.forEachIndexed { index, char ->
                                    CharacterAvatar(
                                        name = char.name,
                                        avatarPath = char.avatarPath,
                                        size = 24.dp,
                                        modifier = Modifier
                                            .align(if (index == 0) Alignment.TopStart else Alignment.BottomEnd)
                                            .padding(if (index == 0) 0.dp else 4.dp)
                                    )
                                }
                            }
                        } else {
                            CharacterAvatar(
                                name = character?.name ?: "?",
                                avatarPath = character?.avatarPath,
                                size = 36.dp
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = if (isGroupChat) {
                                    groupCharacters.joinToString(", ") { it.name }
                                } else {
                                    character?.name ?: stringResource(R.string.loading)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            if (isGenerating) {
                                val infiniteTransition = rememberInfiniteTransition(label = "topBar")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.4f,
                                    animationSpec = InfiniteRepeatableSpec(
                                        animation = androidx.compose.animation.core.tween(800),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "typingAlpha"
                                )
                                val typingName = if (isGroupChat) {
                                    respondingCharacter?.name ?: stringResource(R.string.typing)
                                } else {
                                    stringResource(R.string.typing)
                                }
                                Text(
                                    text = typingName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = { showChattinessSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.chat_settings))
                    }
                    IconButton(onClick = { showBackgroundPicker = true }) {
                        Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.change_background))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val bgPath = backgroundPath
            if (bgPath != null) {
                if (bgPath.startsWith("preset:")) {
                    val presetId = bgPath.removePrefix("preset:")
                    val preset = presetBackgrounds.find { it.id == presetId }
                    if (preset != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(preset.colors))
                        )
                    }
                } else {
                    val file = File(bgPath)
                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LaunchedEffect(bgPath) {
                            viewModel.clearChatBackground()
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }

            // 搜索栏
            AnimatedVisibility(
                visible = showSearch,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchMessages(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_messages)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    if (searchResults.isNotEmpty()) {
                        Text(
                            text = "${currentSearchIndex + 1}/${searchResults.size}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { viewModel.previousSearchResult() }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.previous))
                        }
                        IconButton(onClick = { viewModel.nextSearchResult() }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.next))
                        }
                    }
                    IconButton(onClick = {
                        showSearch = false
                        viewModel.clearSearch()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                if (branches.size > 1) {
                    BranchNavigationBar(
                        currentIndex = currentBranchIndex,
                        totalBranches = branches.size,
                        onPrevious = { viewModel.switchBranch(currentBranchIndex - 1) },
                        onNext = { viewModel.switchBranch(currentBranchIndex + 1) }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages.size, key = { messages[it].id }) { index ->
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
                                message.characterId?.let { charId ->
                                    groupCharacters.find { it.id == charId }
                                }
                            } else null
                            val msgCharName = msgChar?.name ?: character?.name ?: ""
                            val msgAvatarPath = msgChar?.avatarPath ?: character?.avatarPath
                            // 引用消息查找
                            val quotedMsg = remember(message.replyToId, messages) {
                                message.replyToId?.let { qid -> messages.find { it.id == qid } }
                            }
                            val quotedName = remember(quotedMsg) {
                                quotedMsg?.let { qm ->
                                    if (qm.role == "user") null
                                    else if (isGroupChat) qm.characterId?.let { cid -> groupCharacters.find { it.id == cid }?.name }
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
                                    isSearchResult = index in searchResults,
                                    isCurrentSearchResult = currentSearchIndex >= 0 && searchResults.getOrNull(currentSearchIndex) == index,
                                    isSpeaking = speakingMessageId == message.id,
                                    onRegenerate = { viewModel.regenerate(message.id) },
                                    onEdit = { editingMessage = message },
                                    onDelete = { deletingMessageId = message.id },
                                    onBranch = { viewModel.createBranchFromMessage(message.id) },
                                    onSwipeLeft = { viewModel.swipeLeft(message.id) },
                                    onSwipeRight = { viewModel.swipeRight(message.id) },
                                    onReply = { viewModel.regenerate(message.id) },
                                    onSpeak = { viewModel.speakMessage(message) },
                                    onStopSpeak = { viewModel.stopSpeaking() },
                                    onQuoteReply = { viewModel.setReplyTo(message) },
                                    quotedMessage = quotedMsg,
                                    quotedMessageName = quotedName
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

                val haptic = LocalHapticFeedback.current
                val replyingToCharacterName = replyingTo?.let { msg ->
                    if (msg.role == "user") null
                    else if (isGroupChat) msg.characterId?.let { id -> groupCharacters.find { it.id == id }?.name }
                    else character?.name
                }
                InputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    onStop = { viewModel.stopGeneration() },
                    onContinue = { viewModel.continueGeneration() },
                    isGenerating = isGenerating,
                    showContinue = messages.lastOrNull()?.role == "assistant" && !isGenerating,
                    isGroupChat = isGroupChat,
                    groupCharacters = groupCharacters,
                    replyingTo = replyingTo,
                    replyingToName = replyingToCharacterName,
                    onCancelReply = { viewModel.clearReplyTo() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
