package com.tavern.lite.ui.screens.persona

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.ui.components.CharacterAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    onBack: () -> Unit,
    viewModel: PersonaViewModel = hiltViewModel()
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<PersonaEntity?>(null) }

    if (showAddDialog) {
        PersonaDialog(
            title = "新建用户角色",
            initialName = "",
            initialBiography = "",
            onConfirm = { name, biography ->
                viewModel.createPersona(name, biography)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingPersona != null) {
        PersonaDialog(
            title = "编辑用户角色",
            initialName = editingPersona!!.name,
            initialBiography = editingPersona!!.biography,
            onConfirm = { name, biography ->
                viewModel.updatePersona(editingPersona!!.id, name, biography, editingPersona!!.avatarPath)
                editingPersona = null
            },
            onDismiss = { editingPersona = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户角色") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建用户角色")
            }
        }
    ) { padding ->
        if (personas.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无用户角色",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角 + 创建你的身份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "用户角色用于在对话中代表\"你\"的身份",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        onEdit = { editingPersona = persona },
                        onDelete = { viewModel.deletePersona(persona.id) },
                        onSetDefault = { viewModel.setDefault(persona.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: PersonaEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (persona.isDefault)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            CharacterAvatar(
                name = persona.name,
                avatarPath = persona.avatarPath,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = persona.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (persona.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "默认",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (persona.biography.isNotBlank()) {
                    Text(
                        text = persona.biography,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "操作",
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
                    if (!persona.isDefault) {
                        DropdownMenuItem(
                            text = { Text("设为默认") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                onSetDefault()
                                showMenu = false
                            }
                        )
                    }
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
    }
}

@Composable
private fun PersonaDialog(
    title: String,
    initialName: String,
    initialBiography: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var biography by remember { mutableStateOf(initialBiography) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("角色名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = biography,
                    onValueChange = { biography = it },
                    placeholder = { Text("自我介绍、背景故事...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "支持 {{user}} 和 {{char}} 占位符",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, biography) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
