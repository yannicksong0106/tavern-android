package com.tavern.lite.ui.screens.worldbook

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookEditScreen(
    onBack: () -> Unit,
    viewModel: WorldBookEditViewModel = hiltViewModel()
) {
    val worldBook by viewModel.worldBook.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WorldBookEntryEntity?>(null) }
    var deletingEntry by remember { mutableStateOf<WorldBookEntryEntity?>(null) }

    // 添加条目对话框
    if (showAddEntryDialog) {
        EntryEditDialog(
            title = stringResource(R.string.add_entry),
            entry = null,
            onConfirm = { comment, content, keys, keysSecondary, constant, selective, selectiveLogic ->
                viewModel.addEntry(comment, content, keys, keysSecondary, constant, selective, selectiveLogic)
                showAddEntryDialog = false
            },
            onDismiss = { showAddEntryDialog = false }
        )
    }

    // 编辑条目对话框
    editingEntry?.let { entry ->
        EntryEditDialog(
            title = stringResource(R.string.edit_entry),
            entry = entry,
            onConfirm = { comment, content, keys, keysSecondary, constant, selective, selectiveLogic ->
                viewModel.updateEntry(
                    entry.copy(
                        comment = comment,
                        content = content,
                        keys = JsonArray(keys.map { JsonPrimitive(it) }).toString(),
                        keysSecondary = JsonArray(keysSecondary.map { JsonPrimitive(it) }).toString(),
                        constant = constant,
                        selective = selective,
                        selectiveLogic = selectiveLogic
                    )
                )
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }

    // 删除确认
    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_text, entry.comment.ifBlank { stringResource(R.string.untitled) })) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry)
                    deletingEntry = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(worldBook?.name ?: stringResource(R.string.world_book_list), style = MaterialTheme.typography.headlineMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddEntryDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_entry))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 世界书描述
            worldBook?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 条目统计
            Text(
                text = stringResource(R.string.entry_count, entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (entries.isEmpty()) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.no_entries_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onEdit = { editingEntry = entry },
                            onToggleDisabled = { viewModel.toggleEntryDisabled(entry) },
                            onDelete = { deletingEntry = entry }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: WorldBookEntryEntity,
    onEdit: () -> Unit,
    onToggleDisabled: () -> Unit,
    onDelete: () -> Unit
) {
    val keys: List<String> = try {
        Json.decodeFromString(entry.keys)
    } catch (e: Exception) {
        Log.w("WorldBookEditScreen", "Failed to decode entry keys: ${e.message}", e)
        emptyList()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.disabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = entry.comment.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (entry.constant) {
                    Text(
                        text = stringResource(R.string.constant_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (entry.selective) {
                    val logicName = when (entry.selectiveLogic) {
                        0 -> "AND"
                        1 -> "OR"
                        2 -> "NOT"
                        else -> "?"
                    }
                    Text(
                        text = stringResource(R.string.selective_tag, logicName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                IconButton(onClick = onToggleDisabled, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        if (entry.disabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (entry.disabled) stringResource(R.string.enable) else stringResource(R.string.disable),
                        modifier = Modifier.padding(4.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.padding(4.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // 关键词
            if (keys.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.keywords_label, keys.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 内容预览
            if (entry.content.isNotBlank()) {
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditDialog(
    title: String,
    entry: WorldBookEntryEntity?,
    onConfirm: (comment: String, content: String, keys: List<String>, keysSecondary: List<String>, constant: Boolean, selective: Boolean, selectiveLogic: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var comment by remember { mutableStateOf(entry?.comment ?: "") }
    var content by remember { mutableStateOf(entry?.content ?: "") }
    var keysText by remember {
        val keys: List<String> = try {
            entry?.keys?.let { Json.decodeFromString(it) } ?: emptyList()
        } catch (e: Exception) {
            Log.w("WorldBookEditScreen", "Failed to decode keys: ${e.message}", e)
            emptyList()
        }
        mutableStateOf(keys.joinToString(", "))
    }
    var keysSecondaryText by remember {
        val keys: List<String> = try {
            entry?.keysSecondary?.let { Json.decodeFromString(it) } ?: emptyList()
        } catch (e: Exception) {
            Log.w("WorldBookEditScreen", "Failed to decode secondaryKeys: ${e.message}", e)
            emptyList()
        }
        mutableStateOf(keys.joinToString(", "))
    }
    var constant by remember { mutableStateOf(entry?.constant ?: false) }
    var selective by remember { mutableStateOf(entry?.selective ?: false) }
    var selectiveLogic by remember { mutableStateOf(entry?.selectiveLogic ?: 0) }
    var logicExpanded by remember { mutableStateOf(false) }
    val logicOptions = listOf(
        stringResource(R.string.logic_and),
        stringResource(R.string.logic_or),
        stringResource(R.string.logic_not)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.entry_comment)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keysText,
                    onValueChange = { keysText = it },
                    label = { Text(stringResource(R.string.primary_keywords)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.content)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = constant, onCheckedChange = { constant = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.constant_entry), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selective, onCheckedChange = { selective = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.selective_match), style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(visible = selective) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = keysSecondaryText,
                            onValueChange = { keysSecondaryText = it },
                            label = { Text(stringResource(R.string.secondary_keywords)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = logicExpanded,
                            onExpandedChange = { logicExpanded = !logicExpanded }
                        ) {
                            OutlinedTextField(
                                value = logicOptions[selectiveLogic],
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.match_logic)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(logicExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = logicExpanded,
                                onDismissRequest = { logicExpanded = false }
                            ) {
                                logicOptions.forEachIndexed { index, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectiveLogic = index
                                            logicExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val keys = keysText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val keys2 = keysSecondaryText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onConfirm(comment, content, keys, keys2, constant, selective, selectiveLogic)
                },
                enabled = content.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
