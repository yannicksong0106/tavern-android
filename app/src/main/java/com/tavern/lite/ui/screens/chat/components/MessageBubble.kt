package com.tavern.lite.ui.screens.chat.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    showActionBar: Boolean = false,
    isPinned: Boolean = false,
    onTap: () -> Unit = {},
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onReply: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onStopSpeak: () -> Unit = {},
    onPinToggle: () -> Unit = {},
    onCopy: () -> Unit = {},
    onResend: () -> Unit = {},
    onBranch: () -> Unit = {},
    quotedMessage: MessageEntity? = null,
    quotedMessageName: String? = null,
    onQuoteClick: ((Long) -> Unit)? = null
) {
    val isUser = message.role == "user"
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
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
        } catch (e: Exception) { Log.w("MessageBubble", "Failed to parse swipeContent: ${e.message}", e); emptyList() }
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
    Row(
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
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

            // 圆角形状只计算一次，.border() 和 .clip() 共用
            val bubbleShape = remember(isGroupedTop, isGroupedBottom, isUser, cornerRadius) {
                RoundedCornerShape(
                    topStart = if (isGroupedTop) 4.dp else cornerRadius,
                    topEnd = if (isGroupedTop) 4.dp else cornerRadius,
                    bottomStart = if (isUser) (if (isGroupedBottom) 4.dp else cornerRadius) else 4.dp,
                    bottomEnd = if (isUser) 4.dp else (if (isGroupedBottom) 4.dp else cornerRadius)
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .then(
                        if (isCurrentSearchResult || isSpeaking) {
                            Modifier.border(
                                width = borderWidth,
                                color = borderColor,
                                shape = bubbleShape
                            )
                        } else Modifier
                    )
                    .clip(bubbleShape)
                    .background(color = bubbleColor)
                    .animateContentSize()
                    .clickable { onTap() }
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

                // 引用消息卡片
                if (quotedMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .then(
                                if (onQuoteClick != null) Modifier.clickable { onQuoteClick(quotedMessage.id) }
                                else Modifier
                            )
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quotedMessageName ?: if (quotedMessage.role == "user") stringResource(R.string.user_persona) else stringResource(R.string.app_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                            Text(
                                text = quotedMessage.content.take(120),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 显示图片附件
                val imagePathsList = remember(message.imagePaths) {
                    try {
                        if (message.imagePaths != "[]" && message.imagePaths.isNotBlank()) {
                            Json.parseToJsonElement(message.imagePaths).jsonArray
                                .map { it.jsonPrimitive.content }
                        } else emptyList()
                    } catch (_: Exception) { emptyList() }
                }
                if (imagePathsList.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (message.content.isNotBlank()) 6.dp else 0.dp)
                    ) {
                        items(imagePathsList) { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
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

        }
    }
    }

    // 消息操作栏
    AnimatedVisibility(
        visible = showActionBar,
        enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f),
        exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f)
    ) {
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 复制
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            ActionBarDivider()

            // 编辑 / 重新生成
            if (isUser) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                ActionBarDivider()
                // 重发（仅用户消息）
                IconButton(onClick = onResend, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.resend),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.regenerate),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            ActionBarDivider()

            // 朗读 / 停止朗读
            IconButton(
                onClick = if (isSpeaking) onStopSpeak else onSpeak,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(if (isSpeaking) R.string.tts_stop else R.string.tts_speak),
                    tint = if (isSpeaking) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 置顶（仅 AI 消息）
            if (!isUser) {
                ActionBarDivider()
                IconButton(onClick = onPinToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(if (isPinned) R.string.unpin_message else R.string.pin_message),
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 创建分支
            ActionBarDivider()
            IconButton(onClick = onBranch, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ForkRight,
                    contentDescription = stringResource(R.string.create_branch),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            ActionBarDivider()

            // 删除
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    }
}

@Composable
private fun ActionBarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
    )
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
