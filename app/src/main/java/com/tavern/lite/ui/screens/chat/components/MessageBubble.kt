package com.tavern.lite.ui.screens.chat.components

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.ui.components.MarkdownText
import com.tavern.lite.ui.theme.AssistantBubbleDark
import com.tavern.lite.ui.theme.AssistantBubbleLight
import com.tavern.lite.ui.theme.UserBubbleDark
import com.tavern.lite.ui.theme.UserBubbleLight
import com.tavern.lite.ui.components.CharacterAvatar
import io.noties.markwon.Markwon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    characterName: String,
    markwon: Markwon,
    bubbleStyle: BubbleStyleConfig = BubbleStyleConfig(),
    showName: Boolean = true,
    showTimestamp: Boolean = true,
    isGroupedTop: Boolean = false,
    isGroupedBottom: Boolean = false,
    avatarPath: String? = null,
    showAvatar: Boolean = false,
    isSearchResult: Boolean = false,
    isCurrentSearchResult: Boolean = false,
    isSpeaking: Boolean = false,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBranch: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onReply: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onStopSpeak: () -> Unit = {},
    onQuoteReply: () -> Unit = {}
) {
    val isUser = message.role == "user"
    val isDark = isSystemInDarkTheme()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val maxBubbleWidth = (screenWidth * 0.75f).dp

    // 滑动手势状态
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val maxSwipe = with(LocalDensity.current) { 120.dp.toPx() }

    val swipeList = remember(message.swipeContent) {
        if (message.swipeContent == "[]" || message.swipeContent.isBlank()) emptyList()
        else try {
            val arr = org.json.JSONArray(message.swipeContent)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }
    val swipeCount = if (swipeList.isEmpty()) 1 else swipeList.size
    val hasSwipes = swipeCount > 1

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

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 滑动时露出的操作图标
        val revealAlpha = (offsetX.value / swipeThreshold).coerceIn(-1f, 1f)
        if (revealAlpha < -0.3f) {
            // 左滑露出：回复/重新生成
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isUser) Icons.Default.Edit else Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (revealAlpha > 0.3f) {
            // 右滑露出：删除
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

    Row(
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offsetX.value < -swipeThreshold -> {
                                    // 左滑超过阈值 → 回复/重新生成
                                    if (isUser) onEdit() else onReply()
                                    offsetX.animateTo(0f, tween(200))
                                }
                                offsetX.value > swipeThreshold -> {
                                    // 右滑超过阈值 → 删除
                                    onDelete()
                                    offsetX.animateTo(0f, tween(200))
                                }
                                else -> {
                                    offsetX.animateTo(0f, tween(200))
                                }
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            val newValue = (offsetX.value + dragAmount)
                                .coerceIn(-maxSwipe, maxSwipe)
                            offsetX.snapTo(newValue)
                        }
                    }
                )
            }
    ) {
        // 群聊头像：只在非用户消息且 showAvatar=true 时显示
        if (showAvatar && !isUser) {
            CharacterAvatar(
                name = characterName,
                avatarPath = avatarPath,
                size = 28.dp,
                modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
            )
        }

        Box {
            val borderWidth = when {
                isSpeaking -> 2.dp
                isCurrentSearchResult -> 2.dp
                else -> 0.dp
            }
            val borderColor = when {
                isSpeaking -> MaterialTheme.colorScheme.tertiary
                isCurrentSearchResult -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }

            Column(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .then(
                        if (isCurrentSearchResult || isSpeaking) {
                            Modifier.border(
                                width = borderWidth,
                                color = borderColor,
                                shape = RoundedCornerShape(
                                    topStart = if (isGroupedTop) 4.dp else cornerRadius,
                                    topEnd = if (isGroupedTop) 4.dp else cornerRadius,
                                    bottomStart = if (isUser) (if (isGroupedBottom) 4.dp else cornerRadius) else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else (if (isGroupedBottom) 4.dp else cornerRadius)
                                )
                            )
                        } else Modifier
                    )
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isGroupedTop) 4.dp else cornerRadius,
                            topEnd = if (isGroupedTop) 4.dp else cornerRadius,
                            bottomStart = if (isUser) (if (isGroupedBottom) 4.dp else cornerRadius) else 4.dp,
                            bottomEnd = if (isUser) 4.dp else (if (isGroupedBottom) 4.dp else cornerRadius)
                        )
                    )
                    .background(color = bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    )
                    .animateContentSize()
                    .padding(12.dp)
            ) {
                if (!isUser && showName) {
                    Text(
                        text = characterName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }

                val content = message.content.ifEmpty { stringResource(R.string.empty_message) }
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

                if (showTimestamp) {
                    Text(
                        text = formatTimestamp(context, message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (!isUser && hasSwipes) {
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
                if (!isUser) {
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
                    text = { Text(stringResource(R.string.quote_reply)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    onClick = {
                        onQuoteReply()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.branch_from_here)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
                    onClick = {
                        onBranch()
                        showMenu = false
                    }
                )
                if (isSpeaking) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tts_stop)) },
                        leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) },
                        onClick = {
                            onStopSpeak()
                            showMenu = false
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tts_speak)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                        onClick = {
                            onSpeak()
                            showMenu = false
                        }
                    )
                }
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
