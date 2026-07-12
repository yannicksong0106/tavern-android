package com.tavern.lite.ui.screens.quickreply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import com.tavern.lite.domain.usecase.StScriptCommandCatalog
import com.tavern.lite.domain.usecase.StScriptLiteParser

private val QUICK_REPLY_SCOPES = listOf("global", "character", "chat")
private val QUICK_REPLY_AUTOMATION_IDS = listOf("chat_open", "assistant_reply")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReplySetDialog(
    set: QuickReplySetEntity?,
    characters: List<CharacterEntity>,
    chats: List<ChatEntity>,
    onConfirm: (String, String, Long?, Long?, Boolean, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(set?.name.orEmpty()) }
    var scope by remember { mutableStateOf(set?.scope ?: "global") }
    var characterId by remember { mutableStateOf(set?.characterId) }
    var chatId by remember { mutableStateOf(set?.chatId) }
    var enabled by remember { mutableStateOf(set?.enabled ?: true) }
    var displayOrder by remember { mutableStateOf((set?.displayOrder ?: 0).toString()) }
    var expanded by remember { mutableStateOf(false) }
    val scopeTargetValid = when (scope) {
        "character" -> characterId != null
        "chat" -> chatId != null
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (set == null) R.string.quick_reply_add_set else R.string.quick_reply_edit_set)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.quick_reply_set_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = scopeLabel(scope),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.preset_scope)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        QUICK_REPLY_SCOPES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(scopeLabel(option)) },
                                onClick = {
                                    scope = option
                                    if (option != "character") characterId = null
                                    if (option != "chat") chatId = null
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                if (scope == "character") {
                    Spacer(modifier = Modifier.height(8.dp))
                    EntityDropdownField(
                        selectedId = characterId,
                        options = characters,
                        label = stringResource(R.string.quick_reply_character_id),
                        emptyText = stringResource(R.string.no_characters),
                        optionId = { it.id },
                        optionLabel = { "${it.name} #${it.id}" },
                        fallbackLabel = { "#$it" },
                        onSelect = { characterId = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (characterId == null) {
                        ScopeTargetError(text = stringResource(R.string.quick_reply_character_required))
                    }
                }
                if (scope == "chat") {
                    Spacer(modifier = Modifier.height(8.dp))
                    EntityDropdownField(
                        selectedId = chatId,
                        options = chats,
                        label = stringResource(R.string.quick_reply_chat_id),
                        emptyText = stringResource(R.string.no_chats),
                        optionId = { it.id },
                        optionLabel = { chat -> "${chat.name ?: stringResource(R.string.chat_name_default, chat.id)} #${chat.id}" },
                        fallbackLabel = { "#$it" },
                        onSelect = { chatId = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (chatId == null) {
                        ScopeTargetError(text = stringResource(R.string.quick_reply_chat_required))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                NumberField(
                    value = displayOrder,
                    onValueChange = { displayOrder = it },
                    label = stringResource(R.string.quick_reply_display_order)
                )
                CheckRow(checked = enabled, onCheckedChange = { enabled = it }, label = stringResource(R.string.enable))
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && scopeTargetValid,
                onClick = {
                    onConfirm(
                        name,
                        scope,
                        characterId,
                        chatId,
                        enabled,
                        displayOrder.toIntOrNull() ?: 0
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ScopeTargetError(text: String) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun QuickReplyItemDialog(
    reply: QuickReplyEntity?,
    onConfirm: (String, String, String?, String?, Boolean, Boolean, Boolean, Boolean, Boolean, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(reply?.label.orEmpty()) }
    var scriptField by remember {
        val initial = reply?.script.orEmpty()
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    val script = scriptField.text
    var icon by remember { mutableStateOf(reply?.icon.orEmpty()) }
    var automationId by remember { mutableStateOf(reply?.automationId.orEmpty()) }
    var enabled by remember { mutableStateOf(reply?.enabled ?: true) }
    var requiresConfirmation by remember { mutableStateOf(reply?.requiresConfirmation ?: false) }
    var allowAutoRun by remember { mutableStateOf(reply?.allowAutoRun ?: false) }
    var canSendMessages by remember { mutableStateOf(reply?.canSendMessages ?: false) }
    var canTriggerGeneration by remember { mutableStateOf(reply?.canTriggerGeneration ?: false) }
    var displayOrder by remember { mutableStateOf((reply?.displayOrder ?: 0).toString()) }
    val stScriptParser = remember { StScriptLiteParser() }
    val warnings = buildQuickReplyItemWarnings(
        script = script,
        automationId = automationId,
        requiresConfirmation = requiresConfirmation,
        allowAutoRun = allowAutoRun,
        parser = stScriptParser
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (reply == null) R.string.quick_reply_add_reply else R.string.quick_reply_edit_reply)) },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.quick_reply_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text(stringResource(R.string.quick_reply_icon)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scriptField,
                        onValueChange = { scriptField = it },
                        label = { Text(stringResource(R.string.quick_reply_script)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    StScriptCommandPalette(
                        onInsert = { template ->
                            val result = insertStScriptCommand(
                                current = scriptField.text,
                                selectionStart = scriptField.selection.start,
                                selectionEnd = scriptField.selection.end,
                                template = template
                            )
                            scriptField = TextFieldValue(
                                text = result.text,
                                selection = TextRange(result.selection)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = automationId,
                        onValueChange = { automationId = it },
                        label = { Text(stringResource(R.string.quick_reply_automation_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(QUICK_REPLY_AUTOMATION_IDS, key = { it }) { option ->
                            AssistChip(
                                onClick = { automationId = option },
                                label = { Text(option) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NumberField(
                        value = displayOrder,
                        onValueChange = { displayOrder = it },
                        label = stringResource(R.string.quick_reply_display_order)
                    )
                    CheckRow(checked = enabled, onCheckedChange = { enabled = it }, label = stringResource(R.string.enable))
                    CheckRow(
                        checked = requiresConfirmation,
                        onCheckedChange = { requiresConfirmation = it },
                        label = stringResource(R.string.quick_reply_requires_confirmation)
                    )
                    CheckRow(
                        checked = canSendMessages,
                        onCheckedChange = { canSendMessages = it },
                        label = stringResource(R.string.quick_reply_can_send)
                    )
                    CheckRow(
                        checked = canTriggerGeneration,
                        onCheckedChange = { canTriggerGeneration = it },
                        label = stringResource(R.string.quick_reply_can_trigger)
                    )
                    CheckRow(
                        checked = allowAutoRun,
                        onCheckedChange = { allowAutoRun = it },
                        label = stringResource(R.string.quick_reply_allow_auto_run)
                    )
                    warnings.forEach { warning ->
                        QuickReplyItemWarningText(warning = warning)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && script.isNotBlank(),
                onClick = {
                    onConfirm(
                        label,
                        script,
                        icon,
                        automationId,
                        enabled,
                        requiresConfirmation,
                        allowAutoRun,
                        canSendMessages,
                        canTriggerGeneration,
                        displayOrder.toIntOrNull() ?: 0
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun QuickReplyItemWarningText(warning: QuickReplyItemWarning) {
    val text = when (warning) {
        QuickReplyItemWarning.AutomationRequiresAutoRun ->
            stringResource(R.string.quick_reply_warning_auto_run_required)
        QuickReplyItemWarning.AutomationSkipsConfirmation ->
            stringResource(R.string.quick_reply_warning_confirmation_skips_auto)
        QuickReplyItemWarning.AutomationBlocksUnsafeCommands ->
            stringResource(R.string.quick_reply_warning_unsafe_auto_commands)
        QuickReplyItemWarning.ContainsUnknownCommand ->
            stringResource(R.string.quick_reply_warning_unknown_command)
    }
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/**
 * STscript 命令面板：横向 chip 列表，点击将命令模板插入脚本光标处；
 * 右侧信息按钮打开命令参考弹窗（用法 + 说明）。
 */
@Composable
private fun StScriptCommandPalette(
    onInsert: (String) -> Unit
) {
    var showReference by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.quick_reply_command_palette_hint),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { showReference = true }) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.quick_reply_command_reference_title)
            )
        }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(StScriptCommandCatalog.commands, key = { it.name }) { info ->
            AssistChip(
                onClick = { onInsert(info.insertTemplate) },
                label = { Text("/${info.name}") }
            )
        }
    }

    if (showReference) {
        StScriptCommandReferenceDialog(onDismiss = { showReference = false })
    }
}

/**
 * 命令参考弹窗：逐条列出 catalog 里每个命令的用法、别名与说明。
 */
@Composable
private fun StScriptCommandReferenceDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_reply_command_reference_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(StScriptCommandCatalog.commands, key = { it.name }) { info ->
                    Column {
                        Text(
                            text = info.usage,
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                        )
                        if (info.aliases.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.quick_reply_command_aliases,
                                    info.aliases.joinToString(", ") { "/$it" }
                                ),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = info.summary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
