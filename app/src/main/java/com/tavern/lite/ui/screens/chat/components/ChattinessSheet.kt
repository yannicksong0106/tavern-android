package com.tavern.lite.ui.screens.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.CharacterEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChattinessSheet(
    isGroupChat: Boolean,
    characterName: String,
    characterChattiness: Int,
    groupChattiness: Int,
    groupCharacters: List<CharacterEntity>,
    groupCharacterChattiness: Map<Long, Int>,
    onCharacterChattinessChange: (Int) -> Unit,
    onGroupChattinessChange: (Int) -> Unit,
    onGroupCharacterChattinessChange: (Long, Int) -> Unit,
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
                    text = stringResource(R.string.group_chattiness),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.group_chattiness_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = groupChattiness.toFloat(),
                    onValueChange = { onGroupChattinessChange(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.fillMaxWidth()
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
                Slider(
                    value = characterChattiness.toFloat(),
                    onValueChange = { onCharacterChattinessChange(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                ChattinessLabel(value = characterChattiness)
            }
        }
    }
}

@Composable
fun ChattinessLabel(value: Int) {
    Text(
        text = when {
            value <= 20 -> stringResource(R.string.chattiness_silent)
            value <= 40 -> stringResource(R.string.chattiness_quiet)
            value <= 60 -> stringResource(R.string.chattiness_normal)
            value <= 80 -> stringResource(R.string.chattiness_talkative)
            else -> stringResource(R.string.chattiness_very_talkative)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}
