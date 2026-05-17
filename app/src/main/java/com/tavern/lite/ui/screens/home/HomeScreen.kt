package com.tavern.lite.ui.screens.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.ui.components.CharacterAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCharacterClick: (characterId: Long) -> Unit,
    onCreateCharacter: () -> Unit,
    onEditCharacter: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onWorldBookClick: () -> Unit,
    onMemoryClick: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var deletingCharacter by remember { mutableStateOf<CharacterEntity?>(null) }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCharacter(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 删除确认对话框
    if (deletingCharacter != null) {
        AlertDialog(
            onDismissRequest = { deletingCharacter = null },
            title = { Text("删除角色") },
            text = { Text("确定要删除「${deletingCharacter!!.name}」吗？该角色的所有聊天记录也会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCharacter(deletingCharacter!!.id)
                    deletingCharacter = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCharacter = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "酒馆 Lite",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "image/png"))
                    }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "导入角色卡")
                    }
                    IconButton(onClick = onWorldBookClick) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "世界书")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCharacter,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建角色")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AnimatedVisibility(visible = showSearch) {
                SearchBar(onQueryChanged = viewModel::onSearchQueryChanged)
            }

            if (characters.isEmpty()) {
                EmptyState(onCreate = onCreateCharacter)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(characters, key = { it.id }) { character ->
                        CharacterCard(
                            character = character,
                            onClick = { onCharacterClick(character.id) },
                            onLongClick = { onEditCharacter(character.id) },
                            onDelete = { deletingCharacter = character },
                            onExport = { viewModel.exportCharacter(character.id) },
                            onExportPng = { viewModel.exportCharacterPng(character.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(onQueryChanged: (String) -> Unit) {
    var query by remember { mutableStateOf("") }

    TextField(
        value = query,
        onValueChange = {
            query = it
            onQueryChanged(it)
        },
        placeholder = { Text("搜索角色...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterCard(
    character: CharacterEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onExportPng: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            CharacterAvatar(
                name = character.name,
                avatarPath = character.avatarPath,
                size = 56.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (character.description.isNotBlank()) {
                    Text(
                        text = character.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // 显示标签
                val tags = character.tags
                if (tags != "[]" && tags.isNotBlank()) {
                    Text(
                        text = tags.removeSurrounding("[", "]").replace("\"", ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("导出为 JSON") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            onExport()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出为 PNG") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            onExportPng()
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
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "~",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                fontSize = MaterialTheme.typography.headlineLarge.fontSize * 2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "还没有角色",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "点击右下角 + 创建你的第一个角色",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
