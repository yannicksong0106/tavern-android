package com.tavern.lite.ui.screens.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.ui.components.BackgroundPickerSheet
import com.tavern.lite.ui.components.CharacterAvatar
import com.tavern.lite.ui.components.presetBackgrounds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(
    characterId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onMemoryClick: (Long) -> Unit = {},
    onScriptClick: (Long) -> Unit = {},
    viewModel: CharacterEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showBackgroundPicker by remember { mutableStateOf(false) }

    // 背景选择器
    if (showBackgroundPicker) {
        BackgroundPickerSheet(
            currentBackgroundPath = state.backgroundPath,
            onSelectPreset = { preset ->
                viewModel.setPresetBackground(preset.id)
            },
            onSelectImage = { uri -> viewModel.updateBackground(uri) },
            onClear = { viewModel.clearBackground() },
            onDismiss = { showBackgroundPicker = false }
        )
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateAvatar(uri)
        }
    }

    LaunchedEffect(characterId) {
        if (characterId != null) {
            viewModel.loadCharacter(characterId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑角色" else "新建角色") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onSaved) }) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 头像选择
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clickable { avatarPicker.launch("image/*") }
            ) {
                CharacterAvatar(
                    name = state.name.ifBlank { "?" },
                    avatarPath = state.avatarPath,
                    size = 96.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击更换头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 背景设置
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBackgroundPicker = true }
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "聊天背景",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (state.backgroundPath != null) "已设置" else "默认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "更换",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 记忆管理
            if (state.isEditing && state.characterId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemoryClick(state.characterId!!) }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "记忆管理",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "查看、添加和编辑角色记忆",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "进入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 正则脚本
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onScriptClick(state.characterId!!) }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "正则脚本",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "消息替换和正则表达式处理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "进入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            EditField(
                label = "角色名称 *",
                value = state.name,
                onValueChange = { viewModel.updateField("name", it) },
                singleLine = true
            )

            EditField(
                label = "角色描述",
                value = state.description,
                onValueChange = { viewModel.updateField("description", it) },
                placeholder = "你是一个来自异世界的精灵，温柔而好奇..."
            )

            EditField(
                label = "性格特征",
                value = state.personality,
                onValueChange = { viewModel.updateField("personality", it) },
                placeholder = "温柔、好奇、有点迷糊"
            )

            EditField(
                label = "开场白",
                value = state.firstMes,
                onValueChange = { viewModel.updateField("firstMes", it) },
                placeholder = "*你推开古老的木门，看到一位精灵少女正在...*"
            )

            EditField(
                label = "示例对话",
                value = state.mesExample,
                onValueChange = { viewModel.updateField("mesExample", it) },
                placeholder = "<START>\n{{user}}: 你好\n{{char}}: *微微一笑* 你好呀，旅行者。"
            )

            EditField(
                label = "系统提示词（可选）",
                value = state.systemPrompt,
                onValueChange = { viewModel.updateField("systemPrompt", it) },
                placeholder = "Write {{char}}'s next reply..."
            )

            EditField(
                label = "历史后指令（可选）",
                value = state.postHistoryInstructions,
                onValueChange = { viewModel.updateField("postHistoryInstructions", it) },
                placeholder = "对话结束后要遵守的规则..."
            )

            // Author's Note section
            Text(
                text = "作者注释",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Text(
                text = "注入到对话历史中的额外提示词，用于微调角色行为",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            EditField(
                label = "注释内容",
                value = state.authorNoteContent,
                onValueChange = { viewModel.updateField("authorNoteContent", it) },
                placeholder = "在此输入作者注释..."
            )
            EditField(
                label = "注入深度（从末尾算起的消息数）",
                value = state.authorNoteDepth.toString(),
                onValueChange = { viewModel.updateField("authorNoteDepth", it) },
                singleLine = true
            )
            Text(
                text = "当前位置: ${if (state.authorNotePosition == "after_an") "作者注释之后" else "作者注释之前"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable {
                        viewModel.updateAuthorNotePosition(
                            if (state.authorNotePosition == "after_an") "before_an" else "after_an"
                        )
                    }
            )

            EditField(
                label = "创作者",
                value = state.creator,
                onValueChange = { viewModel.updateField("creator", it) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = false
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
