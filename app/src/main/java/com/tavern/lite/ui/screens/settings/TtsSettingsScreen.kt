package com.tavern.lite.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.R
import com.tavern.lite.data.store.TtsSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val ttsSettings by viewModel.ttsSettings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tts_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TtsContent(ttsSettings, viewModel)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsContent(ttsSettings: TtsSettings, viewModel: SettingsViewModel) {
    var engineExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tts_enabled), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.tts_enabled_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ttsSettings.enabled,
                    onCheckedChange = { viewModel.updateTtsSettings(ttsSettings.copy(enabled = it)) }
                )
            }

            if (ttsSettings.enabled) {
                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = engineExpanded,
                    onExpandedChange = { engineExpanded = !engineExpanded }
                ) {
                    OutlinedTextField(
                        value = if (ttsSettings.engine == "openai") stringResource(R.string.tts_engine_openai) else stringResource(R.string.tts_engine_system),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.tts_engine)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(engineExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = engineExpanded, onDismissRequest = { engineExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tts_engine_system)) },
                            onClick = {
                                engineExpanded = false
                                viewModel.updateTtsSettings(ttsSettings.copy(engine = "system"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tts_engine_openai)) },
                            onClick = {
                                engineExpanded = false
                                viewModel.updateTtsSettings(ttsSettings.copy(engine = "openai"))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (ttsSettings.engine == "system") {
                    Text(
                        text = stringResource(R.string.tts_speed, ttsSettings.speed),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = ttsSettings.speed,
                        onValueChange = { viewModel.updateTtsSettings(ttsSettings.copy(speed = it)) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.tts_pitch, ttsSettings.pitch),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = ttsSettings.pitch,
                        onValueChange = { viewModel.updateTtsSettings(ttsSettings.copy(pitch = it)) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = ttsSettings.openAiEndpoint,
                        onValueChange = { viewModel.updateTtsSettings(ttsSettings.copy(openAiEndpoint = it)) },
                        label = { Text(stringResource(R.string.tts_openai_endpoint)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = ttsSettings.openAiApiKey,
                        onValueChange = { viewModel.updateTtsSettings(ttsSettings.copy(openAiApiKey = it)) },
                        label = { Text(stringResource(R.string.tts_openai_api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = ttsSettings.openAiModel,
                        onValueChange = { viewModel.updateTtsSettings(ttsSettings.copy(openAiModel = it)) },
                        label = { Text(stringResource(R.string.tts_openai_model)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val voices = listOf(
                        "alloy" to R.string.tts_voice_alloy,
                        "echo" to R.string.tts_voice_echo,
                        "fable" to R.string.tts_voice_fable,
                        "onyx" to R.string.tts_voice_onyx,
                        "nova" to R.string.tts_voice_nova,
                        "shimmer" to R.string.tts_voice_shimmer
                    )

                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = !voiceExpanded }
                    ) {
                        OutlinedTextField(
                            value = voices.find { it.first == ttsSettings.openAiVoice }?.second?.let { stringResource(it) } ?: ttsSettings.openAiVoice,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.tts_openai_voice)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(voiceExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = voiceExpanded, onDismissRequest = { voiceExpanded = false }) {
                            voices.forEach { (id, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = {
                                        voiceExpanded = false
                                        viewModel.updateTtsSettings(ttsSettings.copy(openAiVoice = id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
