package com.tavern.lite.ui.screens.character

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.BgmEntity
import com.tavern.lite.data.repository.BgmRepository
import kotlinx.coroutines.launch
import java.io.File

/**
 * BGM 管理弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgmSheet(
    characterId: Long,
    bgmRepository: BgmRepository,
    onDismiss: () -> Unit
) {
    val bgms by bgmRepository.getBgmsForCharacter(characterId)
        .collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BgmEntity?>(null) }
    var pendingAudioPath by remember { mutableStateOf<String?>(null) }

    // Audio picker
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val audioDir = File(context.filesDir, "bgm")
            audioDir.mkdirs()
            val audioFile = File(audioDir, "bgm_${System.currentTimeMillis()}.mp3")
            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                audioFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            pendingAudioPath = audioFile.absolutePath
            showAddDialog = true
        }
    }

    // Delete confirmation
    deleteTarget?.let { bgm ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.bgm_delete)) },
            text = { Text(stringResource(R.string.bgm_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        bgmRepository.deleteBgm(bgm.id)
                        File(bgm.audioPath).delete()
                        deleteTarget = null
                    }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Add BGM dialog
    if (showAddDialog && pendingAudioPath != null) {
        AddBgmDialog(
            onDismiss = {
                showAddDialog = false
                pendingAudioPath = null
            },
            onConfirm = { name, loop, volume, emotion ->
                val path = pendingAudioPath
                if (path != null) {
                    scope.launch {
                        bgmRepository.addBgm(
                            characterId = characterId,
                            name = name,
                            audioPath = path,
                            loop = loop,
                            volume = volume,
                            emotion = emotion
                        )
                    }
                }
                showAddDialog = false
                pendingAudioPath = null
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.bgm_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.bgm_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (bgms.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bgm_no_bgms),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.bgm_no_bgms_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(bgms, key = { it.id }) { bgm ->
                        BgmItem(
                            bgm = bgm,
                            onDelete = { deleteTarget = bgm }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add button
            androidx.compose.material3.Button(
                onClick = { audioPicker.launch("audio/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bgm_add))
            }
        }
    }
}

@Composable
private fun BgmItem(
    bgm: BgmEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bgm.name.ifBlank { bgm.audioPath.substringAfterLast("/") },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (bgm.emotion.isNotBlank()) append("${bgm.emotion} · ")
                        if (bgm.loop) append("Loop")
                        else append("Once")
                        append(" · Vol: ${(bgm.volume * 100).toInt()}%")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.bgm_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBgmDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, loop: Boolean, volume: Float, emotion: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var loop by remember { mutableStateOf(true) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var emotion by remember { mutableStateOf("") }
    var emotionExpanded by remember { mutableStateOf(false) }

    val emotionOptions = listOf(
        "" to "无 (默认)",
        "happy" to "开心",
        "sad" to "悲伤",
        "angry" to "愤怒",
        "surprised" to "惊讶",
        "scared" to "恐惧",
        "disgusted" to "厌恶",
        "confused" to "困惑",
        "embarrassed" to "害羞",
        "love" to "爱慕",
        "neutral" to "平静"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bgm_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.bgm_name_hint)) },
                    label = { Text(stringResource(R.string.bgm_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 情感选择
                ExposedDropdownMenuBox(
                    expanded = emotionExpanded,
                    onExpandedChange = { emotionExpanded = it }
                ) {
                    OutlinedTextField(
                        value = emotionOptions.find { it.first == emotion }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("关联情感") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = emotionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = emotionExpanded,
                        onDismissRequest = { emotionExpanded = false }
                    ) {
                        emotionOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    emotion = value
                                    emotionExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = loop,
                        onCheckedChange = { loop = it }
                    )
                    Text(
                        text = stringResource(R.string.bgm_loop),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.bgm_volume),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, loop, volume, emotion) }
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
