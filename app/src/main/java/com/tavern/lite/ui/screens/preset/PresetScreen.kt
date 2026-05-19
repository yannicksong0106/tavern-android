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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetScreen(
    onBack: () -> Unit,
    onSelectPreset: ((PresetEntity) -> Unit)? = null,
    viewModel: PresetViewModel = hiltViewModel()
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<PresetEntity?>(null) }
    var deletingPreset by remember { mutableStateOf<PresetEntity?>(null) }

    if (showAddDialog) {
        PresetEditDialog(
            preset = null,
            onConfirm = { name, desc, sysPrompt, postHistory, authorNote ->
                viewModel.insertPreset(
                    PresetEntity(
                        name = name,
                        description = desc,
                        systemPrompt = sysPrompt,
                        postHistoryInstructions = postHistory,
                        authorNote = authorNote
                    )
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingPreset != null) {
        PresetEditDialog(
            preset = editingPreset,
            onConfirm = { name, desc, sysPrompt, postHistory, authorNote ->
                viewModel.updatePreset(
                    editingPreset!!.copy(
                        name = name,
                        description = desc,
                        systemPrompt = sysPrompt,
                        postHistoryInstructions = postHistory,
                        authorNote = authorNote
                    )
                )
                editingPreset = null
            },
            onDismiss = { editingPreset = null }
        )
    }

    if (deletingPreset != null) {
        AlertDialog(
            onDismissRequest = { deletingPreset = null },
            title = { Text(stringResource(R.string.delete_preset_title)) },
            text = { Text(stringResource(R.string.delete_preset_text, deletingPreset!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePreset(deletingPreset!!)
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
        if (presets.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
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
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium
                )
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

@Composable
private fun PresetEditDialog(
    preset: PresetEntity?,
    onConfirm: (name: String, desc: String, sysPrompt: String, postHistory: String, authorNote: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(preset?.name ?: "") }
    var description by remember { mutableStateOf(preset?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(preset?.systemPrompt ?: "") }
    var postHistoryInstructions by remember { mutableStateOf(preset?.postHistoryInstructions ?: "") }
    var authorNote by remember { mutableStateOf(preset?.authorNote ?: "") }

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
                onClick = { onConfirm(name, description, systemPrompt, postHistoryInstructions, authorNote) },
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
