package com.tavern.lite.ui.screens.quickreply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReplyScreen(
    onBack: () -> Unit,
    viewModel: QuickReplyViewModel = hiltViewModel()
) {
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    val selectedSetId by viewModel.selectedSetId.collectAsStateWithLifecycle()
    val replies by viewModel.replies.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val shareResult by viewModel.shareResult.collectAsStateWithLifecycle()
    val selectedSet = sets.find { it.id == selectedSetId }

    var showSetDialog by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<QuickReplySetEntity?>(null) }
    var deletingSet by remember { mutableStateOf<QuickReplySetEntity?>(null) }
    var showReplyDialog by remember { mutableStateOf(false) }
    var editingReply by remember { mutableStateOf<QuickReplyEntity?>(null) }
    var deletingReply by remember { mutableStateOf<QuickReplyEntity?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val importSuccessText = stringResource(R.string.quick_reply_import_success)
    val exportFailedText = stringResource(R.string.quick_reply_export_failed)
    var exportPayload by remember { mutableStateOf<QuickReplyShareResult.Exported?>(null) }

    LaunchedEffect(shareResult) {
        when (val result = shareResult) {
            is QuickReplyShareResult.Exported -> {
                exportPayload = result
                viewModel.clearShareResult()
            }
            QuickReplyShareResult.ExportFailed -> {
                snackbarHostState.showSnackbar(exportFailedText)
                viewModel.clearShareResult()
            }
            QuickReplyShareResult.Imported -> {
                showImportDialog = false
                snackbarHostState.showSnackbar(importSuccessText)
                viewModel.clearShareResult()
            }
            is QuickReplyShareResult.ImportFailed -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearShareResult()
            }
            null -> Unit
        }
    }

    exportPayload?.let { payload ->
        QuickReplyExportDialog(
            setName = payload.setName,
            json = payload.json,
            onDismiss = { exportPayload = null }
        )
    }

    if (showImportDialog) {
        QuickReplyImportDialog(
            onConfirm = { viewModel.importFromJson(it) },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showSetDialog) {
        QuickReplySetDialog(
            set = null,
            characters = characters,
            chats = chats,
            onConfirm = { name, scope, characterId, chatId, enabled, order ->
                viewModel.createSet(name, scope, characterId, chatId, enabled, order)
                showSetDialog = false
            },
            onDismiss = { showSetDialog = false }
        )
    }

    editingSet?.let { set ->
        QuickReplySetDialog(
            set = set,
            characters = characters,
            chats = chats,
            onConfirm = { name, scope, characterId, chatId, enabled, order ->
                viewModel.updateSet(set, name, scope, characterId, chatId, enabled, order)
                editingSet = null
            },
            onDismiss = { editingSet = null }
        )
    }

    deletingSet?.let { set ->
        AlertDialog(
            onDismissRequest = { deletingSet = null },
            title = { Text(stringResource(R.string.quick_reply_delete_set_title)) },
            text = { Text(stringResource(R.string.quick_reply_delete_set_text, set.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSet(set)
                    deletingSet = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSet = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showReplyDialog && selectedSet != null) {
        QuickReplyItemDialog(
            reply = null,
            onConfirm = { label, script, icon, automationId, enabled, confirm, autoRun, send, trigger, order ->
                viewModel.createReply(selectedSet.id, label, script, icon, automationId, enabled, confirm, autoRun, send, trigger, order)
                showReplyDialog = false
            },
            onDismiss = { showReplyDialog = false }
        )
    }

    editingReply?.let { reply ->
        QuickReplyItemDialog(
            reply = reply,
            onConfirm = { label, script, icon, automationId, enabled, confirm, autoRun, send, trigger, order ->
                viewModel.updateReply(reply, label, script, icon, automationId, enabled, confirm, autoRun, send, trigger, order)
                editingReply = null
            },
            onDismiss = { editingReply = null }
        )
    }

    deletingReply?.let { reply ->
        AlertDialog(
            onDismissRequest = { deletingReply = null },
            title = { Text(stringResource(R.string.quick_reply_delete_reply_title)) },
            text = { Text(stringResource(R.string.quick_reply_delete_reply_text, reply.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReply(reply)
                    deletingReply = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingReply = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_replies)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.quick_reply_import_set))
                    }
                    if (selectedSet != null) {
                        IconButton(onClick = { viewModel.exportSet(selectedSet) }) {
                            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.quick_reply_export_set))
                        }
                    }
                    IconButton(onClick = { showSetDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.quick_reply_add_set))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedSet != null) {
                FloatingActionButton(onClick = { showReplyDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.quick_reply_add_reply))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (sets.isEmpty()) {
                EmptyQuickReplyState(
                    onCreateSet = { showSetDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                QuickReplySetSelector(
                    sets = sets,
                    selectedSetId = selectedSetId,
                    characters = characters,
                    chats = chats,
                    onSelectSet = viewModel::selectSet,
                    onEditSet = { editingSet = it },
                    onDeleteSet = { deletingSet = it }
                )

                if (selectedSet != null) {
                    Text(
                        text = stringResource(R.string.quick_reply_reply_count, replies.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (selectedSet == null || replies.isEmpty()) {
                    EmptyQuickReplyReplies(
                        canAdd = selectedSet != null,
                        onAddReply = { showReplyDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(replies, key = { it.id }, contentType = { "quickReply" }) { reply ->
                            QuickReplyCard(
                                reply = reply,
                                onEdit = { editingReply = reply },
                                onDelete = { deletingReply = reply }
                            )
                        }
                    }
                }
            }
        }
    }
}
