package com.tavern.lite.ui.screens.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.ui.screens.chat.PromptInspectorFormatter
import com.tavern.lite.ui.screens.chat.PromptInspectorState
import com.tavern.lite.util.TokenEstimator

@Composable
fun PromptInspectorDialog(
    state: PromptInspectorState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        title = { Text(stringResource(R.string.prompt_inspector)) },
        text = {
            when {
                state == null -> Text(stringResource(R.string.prompt_inspector_loading))
                state.error != null -> Text(state.error)
                else -> PromptInspectorContent(state)
            }
        }
    )
}

@Composable
private fun PromptInspectorContent(state: PromptInspectorState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.prompt_messages_count, state.messageCount)) }
            )
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.prompt_tokens_count, TokenEstimator.formatTokenCount(state.tokenEstimate))) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.prompt_worldbook_count, state.worldBookCount)) })
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.prompt_memory_count, state.memoryCount)) })
        }
        Text(
            text = buildString {
                state.respondingCharacterName?.let { append(stringResource(R.string.prompt_responding_character, it)).append('\n') }
                append(stringResource(R.string.prompt_author_note_status, if (state.hasAuthorNote) "ON" else "OFF")).append('\n')
                append(stringResource(R.string.prompt_persona_status, if (state.hasPersona) "ON" else "OFF")).append('\n')
                append(stringResource(R.string.prompt_preset_status, if (state.hasPreset) "ON" else "OFF")).append('\n')
                append(stringResource(R.string.prompt_summary_status, if (state.summaryInjected) "ON" else "OFF"))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.sections.isNotEmpty()) {
            Text(
                text = "Token Distribution by Source:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            state.tokenDistribution.forEach { (source, tokens) ->
                val percentage = if (state.totalTokensFromSections > 0) {
                    (tokens * 100) / state.totalTokensFromSections
                } else 0
                Text(
                    text = "  $source: $tokens tokens ($percentage%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = PromptInspectorFormatter.format(state.messages),
            onValueChange = {},
            readOnly = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 460.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
