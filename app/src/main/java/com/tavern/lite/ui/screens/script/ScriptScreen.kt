package com.tavern.lite.ui.screens.script

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
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.tavern.lite.data.db.entity.ScriptEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptScreen(
    onBack: () -> Unit,
    viewModel: ScriptViewModel = hiltViewModel()
) {
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf<ScriptEntity?>(null) }
    var deletingScript by remember { mutableStateOf<ScriptEntity?>(null) }

    if (showAddDialog) {
        ScriptEditDialog(
            title = stringResource(R.string.add_script),
            script = null,
            onConfirm = { name, comment, scriptType, find, replace, isRegex, caseSensitive ->
                viewModel.addScript(name, comment, scriptType, find, replace, isRegex, caseSensitive)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingScript?.let { script ->
        ScriptEditDialog(
            title = stringResource(R.string.edit_script),
            script = script,
            onConfirm = { name, comment, scriptType, find, replace, isRegex, caseSensitive ->
                viewModel.updateScript(
                    script.copy(
                        name = name,
                        comment = comment,
                        scriptType = scriptType,
                        findPattern = find,
                        replacePattern = replace,
                        isRegex = isRegex,
                        caseSensitive = caseSensitive
                    )
                )
                editingScript = null
            },
            onDismiss = { editingScript = null }
        )
    }

    deletingScript?.let { script ->
        AlertDialog(
            onDismissRequest = { deletingScript = null },
            title = { Text(stringResource(R.string.delete_script_title)) },
            text = { Text(stringResource(R.string.delete_script_text, script.name.ifBlank { stringResource(R.string.untitled) })) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScript(script)
                    deletingScript = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingScript = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.script_title), style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_script))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(R.string.script_count, scripts.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (scripts.isEmpty()) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.no_scripts),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(R.string.no_scripts_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scripts, key = { it.id }) { script ->
                        ScriptCard(
                            script = script,
                            onEdit = { editingScript = script },
                            onToggleEnabled = { viewModel.toggleEnabled(script) },
                            onDelete = { deletingScript = script }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptCard(
    script: ScriptEntity,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    val typeName = when (script.scriptType) {
        0 -> stringResource(R.string.type_user_message)
        1 -> stringResource(R.string.type_ai_reply)
        2 -> stringResource(R.string.type_both)
        else -> "?"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (script.enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = script.name.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = typeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                if (script.isRegex) {
                    Text(
                        text = stringResource(R.string.regex_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                IconButton(onClick = onToggleEnabled, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        if (script.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (script.enabled) stringResource(R.string.disable) else stringResource(R.string.enable),
                        modifier = Modifier.padding(4.dp)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.padding(4.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            if (script.comment.isNotBlank()) {
                Text(
                    text = script.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = "${script.findPattern} → ${script.replacePattern}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScriptEditDialog(
    title: String,
    script: ScriptEntity?,
    onConfirm: (name: String, comment: String, scriptType: Int, findPattern: String, replacePattern: String, isRegex: Boolean, caseSensitive: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(script?.name ?: "") }
    var comment by remember { mutableStateOf(script?.comment ?: "") }
    var scriptType by remember { mutableStateOf(script?.scriptType ?: 0) }
    var findPattern by remember { mutableStateOf(script?.findPattern ?: "") }
    var replacePattern by remember { mutableStateOf(script?.replacePattern ?: "") }
    var isRegex by remember { mutableStateOf(script?.isRegex ?: true) }
    var caseSensitive by remember { mutableStateOf(script?.caseSensitive ?: false) }
    var typeExpanded by remember { mutableStateOf(false) }
    val typeOptions = listOf(
        stringResource(R.string.type_user_message),
        stringResource(R.string.type_ai_reply),
        stringResource(R.string.type_both)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.script_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.script_comment)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = typeOptions[scriptType],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.execution_timing)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typeOptions.forEachIndexed { index, typeName ->
                            DropdownMenuItem(
                                text = { Text(typeName) },
                                onClick = {
                                    scriptType = index
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = findPattern,
                    onValueChange = { findPattern = it },
                    label = { Text(stringResource(R.string.find_pattern)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = replacePattern,
                    onValueChange = { replacePattern = it },
                    label = { Text(stringResource(R.string.replace_with)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.is_regex), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.case_sensitive), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, comment, scriptType, findPattern, replacePattern, isRegex, caseSensitive)
                },
                enabled = findPattern.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
