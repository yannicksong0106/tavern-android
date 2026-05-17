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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            title = "添加脚本",
            script = null,
            onConfirm = { name, comment, scriptType, find, replace, isRegex, caseSensitive ->
                viewModel.addScript(name, comment, scriptType, find, replace, isRegex, caseSensitive)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingScript != null) {
        ScriptEditDialog(
            title = "编辑脚本",
            script = editingScript,
            onConfirm = { name, comment, scriptType, find, replace, isRegex, caseSensitive ->
                viewModel.updateScript(
                    editingScript!!.copy(
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

    if (deletingScript != null) {
        AlertDialog(
            onDismissRequest = { deletingScript = null },
            title = { Text("删除脚本") },
            text = { Text("确定要删除「${deletingScript!!.name.ifBlank { "无标题" }}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScript(deletingScript!!)
                    deletingScript = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingScript = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正则脚本", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加脚本")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "${scripts.size} 个脚本",
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
                            text = "还没有脚本",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "点击右下角 + 添加正则替换脚本",
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
        0 -> "用户消息"
        1 -> "AI 回复"
        2 -> "两者"
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
                    text = script.name.ifBlank { "无标题" },
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
                        text = "正则",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                IconButton(onClick = onToggleEnabled, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        if (script.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (script.enabled) "禁用" else "启用",
                        modifier = Modifier.padding(4.dp)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.padding(4.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
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
    val typeOptions = listOf("用户消息", "AI 回复", "两者")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("脚本名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("备注（可选）") },
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
                        label = { Text("执行时机") },
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
                    label = { Text("查找模式") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = replacePattern,
                    onValueChange = { replacePattern = it },
                    label = { Text("替换为") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("正则表达式", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("区分大小写", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, comment, scriptType, findPattern, replacePattern, isRegex, caseSensitive)
                },
                enabled = findPattern.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
