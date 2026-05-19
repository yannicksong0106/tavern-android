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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tavern.lite.R
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
    val worldBooks by viewModel.worldBooks.collectAsStateWithLifecycle()
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showWorldBookPicker by remember { mutableStateOf(false) }

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

    // 世界书选择器
    if (showWorldBookPicker) {
        WorldBookPickerSheet(
            worldBooks = worldBooks,
            currentWorldBookId = state.worldBookId,
            onSelect = { book ->
                viewModel.setWorldBook(book.id, book.name)
                showWorldBookPicker = false
            },
            onClear = {
                viewModel.clearWorldBook()
                showWorldBookPicker = false
            },
            onDismiss = { showWorldBookPicker = false }
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
                title = { Text(if (state.isEditing) stringResource(R.string.edit_character) else stringResource(R.string.new_character_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onSaved) }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
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
                    text = stringResource(R.string.change_avatar),
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
                        text = stringResource(R.string.chat_background),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (state.backgroundPath != null) stringResource(R.string.background_set) else stringResource(R.string.default_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.change),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 世界书关联
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWorldBookPicker = true }
                    .padding(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.world_book),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.worldBookName ?: stringResource(R.string.no_world_book_assigned),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.worldBookName != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.change),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 健谈度设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.chattiness),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.chattiness_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = state.chattiness.toFloat(),
                    onValueChange = { viewModel.updateField("chattiness", it.toInt().toString()) },
                    valueRange = 0f..100f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = when {
                        state.chattiness <= 20 -> stringResource(R.string.chattiness_silent)
                        state.chattiness <= 40 -> stringResource(R.string.chattiness_quiet)
                        state.chattiness <= 60 -> stringResource(R.string.chattiness_normal)
                        state.chattiness <= 80 -> stringResource(R.string.chattiness_talkative)
                        else -> stringResource(R.string.chattiness_very_talkative)
                    },
                    style = MaterialTheme.typography.bodySmall,
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
                            text = stringResource(R.string.memory_management),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.memory_management_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.enter),
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
                            text = stringResource(R.string.regex_script),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.regex_script_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.enter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            EditField(
                label = stringResource(R.string.character_name_required),
                value = state.name,
                onValueChange = { viewModel.updateField("name", it) },
                singleLine = true
            )

            EditField(
                label = stringResource(R.string.character_desc),
                value = state.description,
                onValueChange = { viewModel.updateField("description", it) },
                placeholder = stringResource(R.string.character_desc_placeholder)
            )

            EditField(
                label = stringResource(R.string.personality),
                value = state.personality,
                onValueChange = { viewModel.updateField("personality", it) },
                placeholder = stringResource(R.string.personality_placeholder)
            )

            EditField(
                label = stringResource(R.string.first_message),
                value = state.firstMes,
                onValueChange = { viewModel.updateField("firstMes", it) },
                placeholder = stringResource(R.string.first_message_placeholder)
            )

            EditField(
                label = stringResource(R.string.example_dialogue),
                value = state.mesExample,
                onValueChange = { viewModel.updateField("mesExample", it) },
                placeholder = "<START>\n{{user}}: ...\n{{char}}: ..."
            )

            EditField(
                label = stringResource(R.string.system_prompt_optional),
                value = state.systemPrompt,
                onValueChange = { viewModel.updateField("systemPrompt", it) },
                placeholder = "Write {{char}}'s next reply..."
            )

            EditField(
                label = stringResource(R.string.post_history_instructions),
                value = state.postHistoryInstructions,
                onValueChange = { viewModel.updateField("postHistoryInstructions", it) },
                placeholder = stringResource(R.string.post_history_placeholder)
            )

            // Author's Note section
            Text(
                text = stringResource(R.string.author_note),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.author_note_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            EditField(
                label = stringResource(R.string.author_note_content),
                value = state.authorNoteContent,
                onValueChange = { viewModel.updateField("authorNoteContent", it) },
                placeholder = stringResource(R.string.author_note_content_placeholder)
            )
            EditField(
                label = stringResource(R.string.author_note_depth),
                value = state.authorNoteDepth.toString(),
                onValueChange = { viewModel.updateField("authorNoteDepth", it) },
                singleLine = true
            )
            Text(
                text = stringResource(
                    R.string.author_note_position,
                    if (state.authorNotePosition == "after_an")
                        stringResource(R.string.position_after_an)
                    else
                        stringResource(R.string.position_before_an)
                ),
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
                label = stringResource(R.string.creator),
                value = state.creator,
                onValueChange = { viewModel.updateField("creator", it) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldBookPickerSheet(
    worldBooks: List<com.tavern.lite.data.db.entity.WorldBookEntity>,
    currentWorldBookId: Long?,
    onSelect: (com.tavern.lite.data.db.entity.WorldBookEntity) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.select_world_book),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // "None" option
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClear() }
                    .padding(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = stringResource(R.string.no_world_book_assigned),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (currentWorldBookId == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            if (worldBooks.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_world_books),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                worldBooks.forEach { book ->
                    val isSelected = book.id == currentWorldBookId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(book) }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (book.description.isNotBlank()) {
                                Text(
                                    text = book.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
