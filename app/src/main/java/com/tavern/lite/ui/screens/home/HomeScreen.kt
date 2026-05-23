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
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.ui.components.CharacterAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCharacterClick: (characterId: Long) -> Unit,
    onCreateCharacter: () -> Unit,
    onEditCharacter: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onWorldBookClick: () -> Unit,
    onGroupChatClick: () -> Unit = {},
    onGroupChatItemClick: (chatId: Long, primaryCharacterId: Long) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val groupChats by viewModel.groupChats.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var deletingCharacter by remember { mutableStateOf<CharacterEntity?>(null) }
    var deletingGroupChat by remember { mutableStateOf<ChatEntity?>(null) }
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
            title = { Text(stringResource(R.string.delete_character_title)) },
            text = { Text(stringResource(R.string.delete_character_text, deletingCharacter!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCharacter(deletingCharacter!!.id)
                    deletingCharacter = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCharacter = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // 删除群聊确认对话框
    if (deletingGroupChat != null) {
        AlertDialog(
            onDismissRequest = { deletingGroupChat = null },
            title = { Text(stringResource(R.string.delete_group_chat_title)) },
            text = { Text(stringResource(R.string.delete_group_chat_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroupChat(deletingGroupChat!!.id)
                    deletingGroupChat = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroupChat = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "image/png"))
                    }) {
                        Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.import_character))
                    }
                    IconButton(onClick = onGroupChatClick) {
                        Icon(Icons.Default.Group, contentDescription = stringResource(R.string.create_group_chat))
                    }
                    IconButton(onClick = onWorldBookClick) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.world_book))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCharacter,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_character))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AnimatedVisibility(visible = showSearch) {
                SearchBar(onQueryChanged = viewModel::onSearchQueryChanged)
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 群聊区域
                if (groupChats.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.group_chats),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(groupChats, key = { it.id }, contentType = { "group_chat" }) { chat ->
                        GroupChatCard(
                            chat = chat,
                            onClick = { onGroupChatItemClick(chat.id, chat.characterId) },
                            onDelete = { deletingGroupChat = chat }
                        )
                    }
                }

                // 角色区域
                if (characters.isEmpty() && groupChats.isEmpty()) {
                    item { EmptyState(onCreate = onCreateCharacter) }
                } else if (characters.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                top = if (groupChats.isNotEmpty()) 8.dp else 0.dp,
                                bottom = 4.dp
                            )
                        )
                    }
                    items(characters, key = { it.id }, contentType = { "character" }) { character ->
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
        placeholder = { Text(stringResource(R.string.search_characters)) },
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
    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
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
                val displayTags = remember(tags) {
                    if (tags != "[]" && tags.isNotBlank()) {
                        tags.removeSurrounding("[", "]").replace("\"", "")
                    } else null
                }
                if (displayTags != null) {
                    Text(
                        text = displayTags,
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
                        contentDescription = stringResource(R.string.more),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_json)) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            onExport()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_png)) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            onExportPng()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChatCard(
    chat: ChatEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.name ?: stringResource(R.string.group_chats),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(chat.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
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
                text = stringResource(R.string.no_characters),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.no_characters_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
