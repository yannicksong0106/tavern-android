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
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import com.tavern.lite.domain.usecase.StScriptCommandCatalog
import com.tavern.lite.domain.usecase.StScriptCommandInfo
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
    // 派生值按依赖 memoize：script 字段每次按键都会重组，未 memoize 会每帧全脚本重扫 3+ 遍（X4 审计 Low）。
    val unknownCommandLines = remember(script) { findUnknownCommandLines(script) }
    val warnings = remember(script, automationId, requiresConfirmation, allowAutoRun, unknownCommandLines) {
        buildQuickReplyItemWarnings(
            script = script,
            automationId = automationId,
            requiresConfirmation = requiresConfirmation,
            allowAutoRun = allowAutoRun,
            parser = stScriptParser,
            hasUnknownCommand = unknownCommandLines.isNotEmpty()
        )
    }

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
                    val highlightColors = StScriptHighlightColors(
                        command = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        knownAlias = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        comment = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        variable = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                        unknown = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                    val scriptTransformation = remember(highlightColors) {
                        VisualTransformation { text ->
                            TransformedText(
                                highlightStScript(text.text, highlightColors),
                                OffsetMapping.Identity
                            )
                        }
                    }
                    OutlinedTextField(
                        value = scriptField,
                        onValueChange = { scriptField = it },
                        label = { Text(stringResource(R.string.quick_reply_script)) },
                        visualTransformation = scriptTransformation,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val insertIntoScript: (String) -> Unit = { template ->
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
                    val appendParamToScript: (String) -> Unit = { fragment ->
                        val result = appendStScriptParam(
                            current = scriptField.text,
                            selectionStart = scriptField.selection.start,
                            selectionEnd = scriptField.selection.end,
                            fragment = fragment
                        )
                        scriptField = TextFieldValue(
                            text = result.text,
                            selection = TextRange(result.selection)
                        )
                    }
                    StScriptCommandPalette(
                        onInsert = insertIntoScript,
                        onInsertParam = appendParamToScript
                    )
                    val scriptVariables = remember(script) { collectStScriptVariableNames(script) }
                    if (scriptVariables.isNotEmpty()) {
                        StScriptVariablePalette(
                            names = scriptVariables,
                            onInsert = { name -> insertIntoScript("{{$name}}") }
                        )
                    }
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
                    unknownCommandLines.forEach { diagnostic ->
                        Text(
                            text = stringResource(
                                R.string.quick_reply_unknown_command_line,
                                diagnostic.line,
                                diagnostic.commandName
                            ),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
    onInsert: (String) -> Unit,
    onInsertParam: (String) -> Unit
) {
    var showReference by remember { mutableStateOf(false) }
    // 记住最后点击的、带参数提示的命令；用于展示二级参数 chip 行。
    var selectedCommand by remember { mutableStateOf<StScriptCommandInfo?>(null) }

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
                onClick = {
                    onInsert(info.insertTemplate)
                    // 点击后若该命令有参数提示，展开二级 chip；否则收起。
                    selectedCommand = info.takeIf { it.paramHints.isNotEmpty() }
                },
                label = { Text("/${info.name}") }
            )
        }
    }

    val paramCommand = selectedCommand
    if (paramCommand != null && paramCommand.paramHints.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.quick_reply_param_palette_hint, "/${paramCommand.name}"),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(paramCommand.paramHints, key = { it.label }) { hint ->
                AssistChip(
                    onClick = { onInsertParam(hint.insert) },
                    label = { Text(hint.label) }
                )
            }
        }
    }

    if (showReference) {
        StScriptCommandReferenceDialog(onDismiss = { showReference = false })
    }
}

/**
 * STscript 变量面板：列出脚本里已定义/已引用的变量名，点击 chip 插入 `{{name}}` 到光标处。
 * 仅在脚本中出现过至少一个变量名时展示。
 */
@Composable
private fun StScriptVariablePalette(
    names: List<String>,
    onInsert: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.quick_reply_variable_palette_hint),
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(names, key = { it }) { name ->
            AssistChip(
                onClick = { onInsert(name) },
                label = { Text("{{$name}}") }
            )
        }
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
