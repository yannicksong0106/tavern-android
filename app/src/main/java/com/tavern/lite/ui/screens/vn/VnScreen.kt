package com.tavern.lite.ui.screens.vn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.ui.screens.chat.ChatViewModel
import com.tavern.lite.ui.screens.chat.components.InputBar
import java.io.File

/**
 * Visual Novel 模式界面
 * 全屏立绘 + 底部对话框 + 背景层 + 输入框
 */
@Composable
fun VnScreen(
    characterId: Long,
    chatId: Long,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val currentEmotion by viewModel.vnModeManager.currentEmotion.collectAsState()
    val currentSpritePath by viewModel.vnModeManager.currentSpritePath.collectAsState()
    val isBgmPlaying by viewModel.vnModeManager.isBgmPlaying.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val character by viewModel.character.collectAsState()
    val isGenerating by viewModel.streamingManager.isGenerating.collectAsState()

    // 输入框状态
    var inputText by remember { mutableStateOf("") }

    // BGM 生命周期管理：进入时加载默认 BGM，离开时停止
    DisposableEffect(Unit) {
        viewModel.vnModeManager.loadDefaultBgm()
        onDispose {
            viewModel.vnModeManager.stopBgm()
        }
    }

    // 获取最新的 AI 回复消息
    val lastAssistantMessage = messages.lastOrNull { it.role == "assistant" }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层
        VnBackground(
            backgroundPath = character?.backgroundPath,
            modifier = Modifier.fillMaxSize()
        )

        // 立绘层
        VnSprite(
            spritePath = currentSpritePath,
            emotion = currentEmotion,
            characterName = character?.name ?: "",
            modifier = Modifier.fillMaxSize()
        )

        // 对话框层
        VnDialogueBox(
            characterName = character?.name ?: "",
            message = lastAssistantMessage?.content ?: "",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp)
        )

        // 输入框层
        InputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.streamingManager.sendMessage(inputText)
                    inputText = ""
                }
            },
            onStop = { viewModel.streamingManager.stopGeneration() },
            isGenerating = isGenerating,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )

        // 顶部工具栏
        VnToolbar(
            onBack = onBack,
            onSettings = onSettings,
            isBgmPlaying = isBgmPlaying,
            onToggleBgm = { viewModel.vnModeManager.toggleBgmPause() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )
    }
}

@Composable
private fun VnBackground(
    backgroundPath: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (backgroundPath != null) {
        val file = File(context.filesDir, backgroundPath)
        if (file.exists()) {
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        } else {
            // 默认渐变背景
            Box(
                modifier = modifier.background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1a1a2e),
                            Color(0xFF16213e),
                            Color(0xFF0f3460)
                        )
                    )
                )
            )
        }
    } else {
        // 默认渐变背景
        Box(
            modifier = modifier.background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f3460)
                    )
                )
            )
        )
    }
}

@Composable
private fun VnSprite(
    spritePath: String?,
    emotion: String,
    characterName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = spritePath != null,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(500))
    ) {
        if (spritePath != null) {
            val file = File(context.filesDir, spritePath)
            if (file.exists()) {
                Crossfade(
                    targetState = spritePath,
                    animationSpec = tween(500),
                    label = "sprite_crossfade"
                ) { path ->
                    Image(
                        painter = rememberAsyncImagePainter(File(context.filesDir, path)),
                        contentDescription = "$characterName - $emotion",
                        modifier = modifier,
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun VnDialogueBox(
    characterName: String,
    message: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(500)
        ) + fadeIn(animationSpec = tween(500)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp)
        ) {
            // 角色名称
            Text(
                text = characterName,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4fc3f7),
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 对话内容
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun VnToolbar(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    isBgmPlaying: Boolean,
    onToggleBgm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White
            )
        }

        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = Color.White
            )
        }

        IconButton(
            onClick = onToggleBgm,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = if (isBgmPlaying) Icons.Default.MusicNote else Icons.Default.MusicOff,
                contentDescription = stringResource(R.string.bgm_title),
                tint = if (isBgmPlaying) Color(0xFF4fc3f7) else Color.White
            )
        }
    }
}
