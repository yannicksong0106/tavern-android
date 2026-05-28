package com.tavern.lite.ui.screens.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.GroupSchedulingStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChattinessSheet(
    isGroupChat: Boolean,
    characterName: String,
    characterChattiness: Int,
    groupChattiness: Int,
    groupCharacters: List<CharacterEntity>,
    groupCharacterChattiness: Map<Long, Int>,
    schedulingStrategy: GroupSchedulingStrategy = GroupSchedulingStrategy.NATURAL,
    messageIntervalMs: Long = 1500L,
    onCharacterChattinessChange: (Int) -> Unit,
    onGroupChattinessChange: (Int) -> Unit,
    onGroupCharacterChattinessChange: (Long, Int) -> Unit,
    onSchedulingStrategyChange: (GroupSchedulingStrategy) -> Unit = {},
    onMessageIntervalChange: (Long) -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isGroupChat) {
                Text(
                    text = stringResource(R.string.scheduling_strategy),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.scheduling_strategy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = schedulingStrategy == GroupSchedulingStrategy.NATURAL,
                        onClick = { onSchedulingStrategyChange(GroupSchedulingStrategy.NATURAL) },
                        label = { Text(stringResource(R.string.strategy_natural)) }
                    )
                    FilterChip(
                        selected = schedulingStrategy == GroupSchedulingStrategy.LIST_ORDER,
                        onClick = { onSchedulingStrategyChange(GroupSchedulingStrategy.LIST_ORDER) },
                        label = { Text(stringResource(R.string.strategy_list_order)) }
                    )
                    FilterChip(
                        selected = schedulingStrategy == GroupSchedulingStrategy.ROUND_ROBIN,
                        onClick = { onSchedulingStrategyChange(GroupSchedulingStrategy.ROUND_ROBIN) },
                        label = { Text(stringResource(R.string.strategy_round_robin)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.message_interval),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.message_interval_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val intervalSec = messageIntervalMs / 1000f
                val intervalLabel = stringResource(R.string.message_interval_value, intervalSec)
                Slider(
                    value = messageIntervalMs.toFloat(),
                    onValueChange = { onMessageIntervalChange(it.toLong()) },
                    valueRange = 500f..5000f,
                    steps = 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = intervalLabel }
                )
                Text(
                    text = intervalLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.group_chattiness),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.group_chattiness_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val groupLabel = stringResource(R.string.a11y_chattiness_value, groupChattiness, chattinessLabel(groupChattiness))
                Slider(
                    value = groupChattiness.toFloat(),
                    onValueChange = { onGroupChattinessChange(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = groupLabel }
                )
                ChattinessLabel(value = groupChattiness)

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.character_chattiness),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                groupCharacters.forEach { char ->
                    val charValue = groupCharacterChattiness[char.id] ?: 50
                    val charLabel = stringResource(R.string.a11y_chattiness_value, charValue, chattinessLabel(charValue))
                    Text(
                        text = char.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Slider(
                        value = charValue.toFloat(),
                        onValueChange = { onGroupCharacterChattinessChange(char.id, it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = charLabel }
                    )
                    ChattinessLabel(value = charValue)
                }
            } else {
                Text(
                    text = stringResource(R.string.chattiness),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.chattiness_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val charLabel = stringResource(R.string.a11y_chattiness_value, characterChattiness, chattinessLabel(characterChattiness))
                Slider(
                    value = characterChattiness.toFloat(),
                    onValueChange = { onCharacterChattinessChange(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = charLabel }
                )
                ChattinessLabel(value = characterChattiness)
            }
        }
    }
}

@Composable
fun ChattinessLabel(value: Int) {
    Text(
        text = chattinessLabel(value),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun chattinessLabel(value: Int): String = when {
    value <= 20 -> stringResource(R.string.chattiness_silent)
    value <= 40 -> stringResource(R.string.chattiness_quiet)
    value <= 60 -> stringResource(R.string.chattiness_normal)
    value <= 80 -> stringResource(R.string.chattiness_talkative)
    else -> stringResource(R.string.chattiness_very_talkative)
}

