package com.tavern.lite.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.R
import com.tavern.lite.data.model.ApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationParamsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.generation_params)) },
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
            GenerationParamsContent(config, viewModel)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GenerationParamsContent(config: ApiConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Temperature: ${"%.2f".format(config.temperature)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = config.temperature,
                onValueChange = { viewModel.updateTemperature(it) },
                valueRange = 0f..2f,
                steps = 39,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Top P: ${"%.2f".format(config.topP)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = config.topP,
                onValueChange = { viewModel.updateTopP(it) },
                valueRange = 0f..1f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Frequency Penalty: ${"%.2f".format(config.frequencyPenalty)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = config.frequencyPenalty,
                onValueChange = { viewModel.updateFrequencyPenalty(it) },
                valueRange = -2f..2f,
                steps = 39,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Presence Penalty: ${"%.2f".format(config.presencePenalty)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = config.presencePenalty,
                onValueChange = { viewModel.updatePresencePenalty(it) },
                valueRange = -2f..2f,
                steps = 39,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = config.maxTokens.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { viewModel.updateMaxTokens(it) }
                },
                label = { Text("Max Tokens") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = config.contextLength.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { viewModel.updateContextLength(it) }
                },
                label = { Text(stringResource(R.string.context_length)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
