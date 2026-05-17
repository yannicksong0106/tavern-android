package com.tavern.lite.ui.screens.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.db.entity.MemoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    characterId: Long,
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    if (showAddDialog) {
        MemoryDialog(
            title = "添加记忆",
            initialContent = "",
            initialImportance = 5,
            onConfirm = { content, importance ->
                viewModel.addMemory(content, importance)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingMemory != null) {
        MemoryDialog(
            title = "编辑记忆",
            initialContent = editingMemory!!.content,
            initialImportance = editingMemory!!.importance,
            onConfirm = { content, importance ->
                viewModel.updateMemory(editingMemory!!.copy(content = content, importance = importance))
                editingMemory = null
            },
            onDismiss = { editingMemory = null }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("清除所有记忆") },
            text = { Text("确定要清除该角色的所有记忆吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showDeleteAllConfirm = false
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (memories.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清除全部")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加记忆")
            }
        }
    ) { padding ->
        if (memories.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无记忆",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角 + 手动添加，或在对话中自动积累",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onEdit = { editingMemory = memory },
                        onDelete = { viewModel.deleteMemory(memory.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 重要度指示
                Text(
                    text = "★".repeat(memory.importance / 2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${memory.source} · ${dateFormat.format(Date(memory.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.padding(0.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                onEdit()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
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

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (memory.accessCount > 0) {
                Text(
                    text = "访问 ${memory.accessCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MemoryDialog(
    title: String,
    initialContent: String,
    initialImportance: Int,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var importance by remember { mutableStateOf(initialImportance.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("记忆内容...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("重要度: ${importance.toInt()}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = importance,
                    onValueChange = { importance = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(content, importance.toInt()) },
                enabled = content.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
