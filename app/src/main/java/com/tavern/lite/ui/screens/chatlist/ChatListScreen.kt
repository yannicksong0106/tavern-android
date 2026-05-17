package com.tavern.lite.ui.screens.chatlist

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.ui.components.CharacterAvatar
import com.tavern.lite.util.ExportFormat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    characterId: Long,
    onChatClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val character by viewModel.character.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var deletingChat by remember { mutableStateOf<ChatEntity?>(null) }
    var renamingChat by remember { mutableStateOf<ChatEntity?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exportTargetChatId by remember { mutableStateOf<Long?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val tempFile = File.createTempFile("import_chat_", ".json")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.importChatFromFile(tempFile)
            tempFile.deleteOnExit()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportedFile.collect { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享对话"))
        }
    }

    // 导出格式选择
    if (showExportSheet) {
        FormatBottomSheet(
            title = if (exportTargetChatId != null) "导出对话" else "导出全部对话",
            onFormatSelected = { format ->
                if (exportTargetChatId != null) {
                    viewModel.exportChat(exportTargetChatId!!, format)
                } else {
                    viewModel.exportAllChats(format)
                }
                showExportSheet = false
                exportTargetChatId = null
            },
            onDismiss = {
                showExportSheet = false
                exportTargetChatId = null
            }
        )
    }

    // 删除确认
    if (deletingChat != null) {
        AlertDialog(
            onDismissRequest = { deletingChat = null },
            title = { Text("删除对话") },
            text = { Text("确定要删除这个对话吗？所有消息都会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(deletingChat!!.id)
                    deletingChat = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingChat = null }) { Text("取消") }
            }
        )
    }

    // 重命名对话
    if (renamingChat != null) {
        RenameChatDialog(
            currentName = renamingChat?.name ?: "",
            onConfirm = { newName ->
                viewModel.renameChat(renamingChat!!.id, newName)
                renamingChat = null
            },
            onDismiss = { renamingChat = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharacterAvatar(
                            name = character?.name ?: "?",
                            avatarPath = character?.avatarPath,
                            size = 36.dp
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = character?.name ?: "对话列表",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${chats.size} 个对话",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "导入对话")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导出全部对话") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showExportSheet = true
                                    exportTargetChatId = null
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createChat { chatId -> onChatClick(chatId) } },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建对话")
            }
        }
    ) { padding ->
        if (chats.isEmpty()) {
            EmptyChatList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(chats, key = { it.id }) { chat ->
                    ChatItem(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        onLongClick = { renamingChat = chat },
                        onDelete = { deletingChat = chat },
                        onExport = {
                            exportTargetChatId = chat.id
                            showExportSheet = true
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatItem(
    chat: ChatEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(12.dp),
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
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.name ?: "对话 ${chat.id}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(chat.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(onClick = onExport) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "导出",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyChatList(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
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
                text = "还没有对话",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "点击右下角 + 开始新对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RenameChatDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("输入对话名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatBottomSheet(
    title: String,
    onFormatSelected: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val formats = listOf(
                Triple(ExportFormat.MARKDOWN, "Markdown", ".md — 通用格式，支持富文本"),
                Triple(ExportFormat.HTML, "HTML", ".html — 带样式的网页，暗色主题"),
                Triple(ExportFormat.PLAINTEXT, "纯文本", ".txt — 简单文本格式"),
                Triple(ExportFormat.JSON, "JSON", ".json — 结构化数据，可重新导入")
            )

            for ((format, name, desc) in formats) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFormatSelected(format) }
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
