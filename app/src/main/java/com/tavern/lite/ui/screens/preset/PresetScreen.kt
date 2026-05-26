package com.tavern.lite.ui.screens.preset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.PresetEntity

private val SCOPE_TABS = listOf(null, "global", "character", "chat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetScreen(
    onBack: () -> Unit,
    onSelectPreset: ((PresetEntity) -> Unit)? = null,
    viewModel: PresetViewModel = hiltViewModel()
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<PresetEntity?>(null) }
    var deletingPreset by remember { mutableStateOf<PresetEntity?>(null) }

    val filteredPresets = if (selectedTab == 0) {
        presets
    } else {
        val scope = SCOPE_TABS[selectedTab]
        presets.filter { it.scope == scope }
    }

    if (showAddDialog) {
        PresetEditDialog(
            preset = null,
            onConfirm = { name, desc, sysPrompt, postHistory, authorNote, scope ->
                viewModel.insertPreset(
                    PresetEntity(
                        name = name,
                        description = desc,
                        systemPrompt = sysPrompt,
                        postHistoryInstructions = postHistory,
                        authorNote = authorNote,
                        scope = scope
                    )
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingPreset?.let { preset ->
        PresetEditDialog(
            preset = preset,
            onConfirm = { name, desc, sysPrompt, postHistory, authorNote, scope ->
                viewModel.updatePreset(
                    preset.copy(
                        name = name,
                        description = desc,
                        systemPrompt = sysPrompt,
                        postHistoryInstructions = postHistory,
                        authorNote = authorNote,
                        scope = scope
                    )
                )
                editingPreset = null
            },
            onDismiss = { editingPreset = null }
        )
    }

    deletingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingPreset = null },
            title = { Text(stringResource(R.string.delete_preset_title)) },
            text = { Text(stringResource(R.string.delete_preset_text, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePreset(preset)
                    deletingPreset = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPreset = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preset_management)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_preset))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                SCOPE_TABS.forEachIndexed { index, scope ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                when (scope) {
                                    null -> stringResource(R.string.scope_all)
                                    "global" -> stringResource(R.string.scope_global)
                                    "character" -> stringResource(R.string.scope_character)
                                    "chat" -> stringResource(R.string.scope_chat)
                                    else -> scope
                                }
                            )
                        }
                    )
                }
            }

            if (filteredPresets.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_presets),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.no_presets_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(filteredPresets, key = { it.id }) { preset ->
                        PresetItem(
                            preset = preset,
                            onClick = {
                                if (onSelectPreset != null) {
                                    onSelectPreset(preset)
                                } else {
                                    editingPreset = preset
                                }
                            },
                            onSetDefault = { viewModel.setDefaultPreset(preset.id) },
                            onDelete = { deletingPreset = preset }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeLabel(scope: String) {
    val (text, color) = when (scope) {
        "global" -> stringResource(R.string.scope_global) to MaterialTheme.colorScheme.primary
        "character" -> stringResource(R.string.scope_character) to MaterialTheme.colorScheme.tertiary
        "chat" -> stringResource(R.string.scope_chat) to MaterialTheme.colorScheme.secondary
        else -> scope to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun PresetItem(
    preset: PresetEntity,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (preset.isDefault) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ScopeLabel(scope = preset.scope)
                }
                if (preset.description.isNotBlank()) {
                    Text(
                        text = preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onSetDefault) {
                Icon(
                    if (preset.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.set_default),
                    tint = if (preset.isDefault) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetEditDialog(
    preset: PresetEntity?,
    onConfirm: (name: String, desc: String, sysPrompt: String, postHistory: String, authorNote: String, scope: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(preset?.name ?: "") }
    var description by remember { mutableStateOf(preset?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(preset?.systemPrompt ?: "") }
    var postHistoryInstructions by remember { mutableStateOf(preset?.postHistoryInstructions ?: "") }
    var authorNote by remember { mutableStateOf(preset?.authorNote ?: "") }
    var scope by remember { mutableStateOf(preset?.scope ?: "global") }
    var scopeExpanded by remember { mutableStateOf(false) }

    val scopeOptions = listOf("global", "character", "chat")
    val scopeLabels = mapOf(
        "global" to "全局",
        "character" to "角色",
        "chat" to "对话"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (preset == null) R.string.add_preset else R.string.edit_preset)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preset_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = scopeExpanded,
                    onExpandedChange = { scopeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = scopeLabels[scope] ?: scope,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.preset_scope)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scopeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = scopeExpanded,
                        onDismissRequest = { scopeExpanded = false }
                    ) {
                        scopeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(scopeLabels[option] ?: option) },
                                onClick = {
                                    scope = option
                                    scopeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.system_prompt_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = postHistoryInstructions,
                    onValueChange = { postHistoryInstructions = it },
                    label = { Text(stringResource(R.string.post_history_instructions)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = authorNote,
                    onValueChange = { authorNote = it },
                    label = { Text(stringResource(R.string.author_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description, systemPrompt, postHistoryInstructions, authorNote, scope) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
