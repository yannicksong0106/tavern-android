package com.tavern.lite.ui.screens.chat

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.tavern.lite.ui.components.BackgroundPickerSheet
import com.tavern.lite.ui.components.PresetBackground
import com.tavern.lite.ui.components.presetBackgrounds
import java.io.File
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tavern.lite.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.ui.components.CharacterAvatar
import com.tavern.lite.ui.components.LoadingDots
import com.tavern.lite.ui.components.MarkdownText
import io.noties.markwon.Markwon
import com.tavern.lite.ui.theme.AssistantBubbleDark
import com.tavern.lite.ui.theme.AssistantBubbleLight
import com.tavern.lite.ui.theme.UserBubbleDark
import com.tavern.lite.ui.theme.UserBubbleLight
import kotlinx.coroutines.launch

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
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val currentBranchIndex by viewModel.currentBranchIndex.collectAsStateWithLifecycle()
    val backgroundPath by viewModel.backgroundPath.collectAsStateWithLifecycle()
    val bubbleStyle by viewModel.bubbleStyle.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBackgroundPicker by remember { mutableStateOf(false) }

    // 背景选择器
    if (showBackgroundPicker) {
        BackgroundPickerSheet(
            currentBackgroundPath = backgroundPath,
            onSelectPreset = { preset ->
                viewModel.setChatBackground("preset:${preset.id}")
            },
            onSelectImage = { uri ->
                // 复制到内部存储
                val bgDir = java.io.File(context.filesDir, "backgrounds")
                bgDir.mkdirs()
                val bgFile = java.io.File(bgDir, "chat_bg_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    bgFile.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.setChatBackground(bgFile.absolutePath)
            },
            onClear = { viewModel.clearChatBackground() },
            onDismiss = { showBackgroundPicker = false }
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

    // 监听错误消息
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 自动滚动到底部
    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 编辑消息对话框
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
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

    // 删除确认对话框
    var deletingMessageId by remember { mutableStateOf<Long?>(null) }
    if (deletingMessageId != null) {
        AlertDialog(
            onDismissRequest = { deletingMessageId = null },
            title = { Text(stringResource(R.string.delete_message_title)) },
            text = { Text(stringResource(R.string.delete_message_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(deletingMessageId!!)
                    deletingMessageId = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessageId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharacterAvatar(
                            name = character?.name ?: "?",
                            avatarPath = character?.avatarPath,
                            size = 36.dp
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = character?.name ?: stringResource(R.string.loading),
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isGenerating) {
                                Text(
                                    text = stringResource(R.string.replying),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
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
            // 背景层
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
                    AsyncImage(
                        model = File(bgPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 半透明遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }

            // 内容层
            Column(modifier = Modifier.fillMaxSize()) {
                // 分支导航栏
            if (branches.size > 1) {
                BranchNavigationBar(
                    currentIndex = currentBranchIndex,
                    totalBranches = branches.size,
                    onPrevious = { viewModel.switchBranch(currentBranchIndex - 1) },
                    onNext = { viewModel.switchBranch(currentBranchIndex + 1) }
                )
            }

            // 消息列表
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        characterName = character?.name ?: "",
                        markwon = markwon,
                        bubbleStyle = bubbleStyle,
                        onRegenerate = { viewModel.regenerate(message.id) },
                        onEdit = { editingMessage = message },
                        onDelete = { deletingMessageId = message.id },
                        onBranch = { viewModel.createBranchFromMessage(message.id) },
                        onSwipeLeft = { viewModel.swipeLeft(message.id) },
                        onSwipeRight = { viewModel.swipeRight(message.id) }
                    )
                }

                // 流式输出中
                if (isGenerating && streamingText.isNotEmpty()) {
                    item {
                        MessageBubble(
                            message = MessageEntity(
                                id = -1,
                                chatId = chatId,
                                role = "assistant",
                                content = streamingText
                            ),
                            characterName = character?.name ?: "",
                            markwon = markwon,
                            bubbleStyle = bubbleStyle,
                            isStreaming = true,
                            onRegenerate = {},
                            onEdit = {},
                            onDelete = {}
                        )
                    }
                }

                // 正在生成但还没有内容
                if (isGenerating && streamingText.isEmpty()) {
                    item {
                        Row(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                            LoadingDots()
                        }
                    }
                }
            }

            // 输入栏
            InputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                onStop = { viewModel.stopGeneration() },
                onContinue = { viewModel.continueGeneration() },
                isGenerating = isGenerating,
                showContinue = messages.lastOrNull()?.role == "assistant" && !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    characterName: String,
    markwon: Markwon,
    bubbleStyle: BubbleStyleConfig = BubbleStyleConfig(),
    isStreaming: Boolean = false,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBranch: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
) {
    val isUser = message.role == "user"
    val isDark = isSystemInDarkTheme()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val screenWidth = LocalDensity.current.run {
        androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.widthPixels /
                density
    }
    val maxBubbleWidth = (screenWidth * 0.75f).dp

    // Parse swipe info
    val swipeList = remember(message.swipeContent) {
        if (message.swipeContent == "[]" || message.swipeContent.isBlank()) emptyList()
        else try {
            val arr = org.json.JSONArray(message.swipeContent)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }
    val swipeCount = if (swipeList.isEmpty()) 1 else swipeList.size
    val hasSwipes = swipeCount > 1

    // 气泡颜色：自定义 > 主题默认
    val bubbleColor = if (isUser) {
        if (bubbleStyle.userBubbleColor != 0L) {
            Color(bubbleStyle.userBubbleColor.toInt() or (0xFF000000.toInt()))
        } else {
            if (isDark) UserBubbleDark else UserBubbleLight
        }
    } else {
        if (bubbleStyle.assistantBubbleColor != 0L) {
            Color(bubbleStyle.assistantBubbleColor.toInt() or (0xFF000000.toInt()))
        } else {
            if (isDark) AssistantBubbleDark else AssistantBubbleLight
        }
    }
    val cornerRadius = bubbleStyle.cornerRadius.dp

    Row(
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(
                        RoundedCornerShape(
                            topStart = cornerRadius,
                            topEnd = cornerRadius,
                            bottomStart = if (isUser) cornerRadius else 4.dp,
                            bottomEnd = if (isUser) 4.dp else cornerRadius
                        )
                    )
                    .background(color = bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { if (!isStreaming) showMenu = true }
                    )
                    .animateContentSize()
                    .padding(12.dp)
            ) {
                if (!isUser && !isStreaming) {
                    Text(
                        text = characterName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }

                val content = message.content.ifEmpty { "..." }
                if (!isUser) {
                    MarkdownText(
                        markwon = markwon,
                        markdown = content,
                        textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                        textSize = bubbleStyle.fontSize.sp
                    )
                } else {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = bubbleStyle.fontSize.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isStreaming) {
                    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                    val cursorAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = InfiniteRepeatableSpec(
                            animation = androidx.compose.animation.core.tween(durationMillis = 600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "cursorAlpha"
                    )
                    Text(
                        text = stringResource(R.string.typing) + "\u2588",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = cursorAlpha),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Timestamp
                if (!isStreaming) {
                    Text(
                        text = formatTimestamp(context, message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Swipe navigation for assistant messages with multiple swipes
            if (!isUser && hasSwipes && !isStreaming) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = onSwipeLeft,
                        enabled = message.swipeIndex > 0,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.swipe_previous),
                            modifier = Modifier,
                            tint = if (message.swipeIndex > 0)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    Text(
                        text = "${message.swipeIndex + 1}/$swipeCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = onSwipeRight,
                        enabled = message.swipeIndex < swipeCount - 1,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.swipe_next),
                            tint = if (message.swipeIndex < swipeCount - 1)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // 长按菜单
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        onEdit()
                        showMenu = false
                    }
                )
                if (isUser) {
                    // 用户消息没有重新生成
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.regenerate)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = {
                            onRegenerate()
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.branch_from_here)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
                    onClick = {
                        onBranch()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onDelete()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EditMessageDialog(
    originalContent: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(originalContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_message_title)) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit = {},
    isGenerating: Boolean,
    showContinue: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.input_hint)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!isGenerating) onSend() }),
            maxLines = 5,
            enabled = !isGenerating,
            modifier = Modifier.weight(1f)
        )

        if (isGenerating) {
            IconButton(
                onClick = onStop,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = stringResource(R.string.stop),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            // Continue button (append to last AI message)
            if (showContinue) {
                IconButton(
                    onClick = onContinue,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Replay,
                        contentDescription = stringResource(R.string.continue_generation),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = if (value.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(context: android.content.Context, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> context.getString(R.string.time_just_now)
        diff < 3600_000 -> context.getString(R.string.time_minutes, (diff / 60_000).toInt())
        diff < 86400_000 -> context.getString(R.string.time_hours, (diff / 3600_000).toInt())
        diff < 604800_000 -> context.getString(R.string.time_days, (diff / 86400_000).toInt())
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@Composable
private fun BranchNavigationBar(
    currentIndex: Int,
    totalBranches: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = currentIndex > 0
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.branch_previous))
        }
        Text(
            text = stringResource(R.string.branch_info, currentIndex + 1, totalBranches),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(
            onClick = onNext,
            enabled = currentIndex < totalBranches - 1
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.branch_next))
        }
    }
}
