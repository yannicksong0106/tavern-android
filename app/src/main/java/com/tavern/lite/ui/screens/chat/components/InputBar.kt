package com.tavern.lite.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.util.TokenEstimator

@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit = {},
    isGenerating: Boolean,
    showContinue: Boolean = false,
    isGroupChat: Boolean = false,
    groupCharacters: List<CharacterEntity> = emptyList(),
    contextTokens: Int = 0,
    inputTokens: Int = 0,
    isListening: Boolean = false,
    onVoiceInput: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAtMenu by remember { mutableStateOf(false) }
    var atSearchText by remember { mutableStateOf("") }

    LaunchedEffect(value) {
        if (isGroupChat && value.endsWith("@")) {
            showAtMenu = true
            atSearchText = ""
        } else if (isGroupChat && showAtMenu) {
            val lastAtIndex = value.lastIndexOf("@")
            if (lastAtIndex >= 0) {
                val afterAt = value.substring(lastAtIndex + 1)
                if (!afterAt.contains(" ")) {
                    atSearchText = afterAt
                } else {
                    showAtMenu = false
                }
            } else {
                showAtMenu = false
            }
        }
    }

    Box {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surface)
        ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.input_hint)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 5,
                modifier = Modifier.weight(1f)
            )

            if (isGenerating) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = stringResource(R.string.stop),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                if (showContinue) {
                    FilledTonalButton(
                        onClick = onContinue,
                        modifier = Modifier.padding(start = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.continue_generation),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                // 语音输入按钮（输入为空时显示）
                if (value.isBlank() && !isListening) {
                    IconButton(
                        onClick = onVoiceInput,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.voice_input),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // 正在录音时显示停止按钮
                if (isListening) {
                    IconButton(
                        onClick = onVoiceInput,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(
                            Icons.Default.MicOff,
                            contentDescription = stringResource(R.string.voice_input),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (value.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send),
                        tint = if (value.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
            // Token count display
            if (contextTokens > 0) {
                val totalTokens = contextTokens + inputTokens
                val tokenText = if (inputTokens > 0) {
                    "${TokenEstimator.formatTokenCount(totalTokens)} (${TokenEstimator.formatTokenCount(contextTokens)} + ${TokenEstimator.formatTokenCount(inputTokens)})"
                } else {
                    TokenEstimator.formatTokenCount(contextTokens)
                }
                Text(
                    text = tokenText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
                )
            }
        } // Column

        if (showAtMenu && groupCharacters.isNotEmpty()) {
            val filteredCharacters = remember(atSearchText, groupCharacters) {
                if (atSearchText.isBlank()) {
                    groupCharacters
                } else {
                    groupCharacters.filter {
                        it.name.contains(atSearchText, ignoreCase = true)
                    }
                }
            }

            if (filteredCharacters.isNotEmpty()) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { showAtMenu = false }
                ) {
                    filteredCharacters.forEach { char ->
                        DropdownMenuItem(
                            text = { Text(char.name) },
                            onClick = {
                                val lastAtIndex = value.lastIndexOf("@")
                                if (lastAtIndex >= 0) {
                                    val newValue = value.substring(0, lastAtIndex) + "@${char.name} "
                                    onValueChange(newValue)
                                }
                                showAtMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
