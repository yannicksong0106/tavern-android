package com.tavern.lite.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.R
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_config)) },
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
            // API Provider 配置
            ApiProviderSection(config, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 连接测试
            ConnectionTestSection(testState, viewModel)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiProviderSection(config: ApiConfig, viewModel: SettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val providers = listOf("OpenAI", "Claude", "Ollama", "KoboldAI", "Gemini", "OpenRouter", "Custom")
    val currentProviderName = config.provider.displayName

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = currentProviderName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    providers.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                expanded = false
                                val provider = when (name) {
                                    "OpenAI" -> ApiProvider.OpenAI()
                                    "Claude" -> ApiProvider.Claude()
                                    "Ollama" -> ApiProvider.Ollama()
                                    "KoboldAI" -> ApiProvider.KoboldAI()
                                    "Gemini" -> ApiProvider.Gemini()
                                    "OpenRouter" -> ApiProvider.OpenRouter()
                                    else -> ApiProvider.Custom()
                                }
                                viewModel.updateProvider(provider)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val provider = config.provider) {
                is ApiProvider.OpenAI -> {
                    ProviderFields(
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        model = provider.model,
                        onBaseUrlChange = { viewModel.updateProvider(provider.copy(baseUrl = it)) },
                        onApiKeyChange = { viewModel.updateProvider(provider.copy(apiKey = it)) },
                        onModelChange = { viewModel.updateProvider(provider.copy(model = it)) }
                    )
                }
                is ApiProvider.Claude -> {
                    ProviderFields(
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        model = provider.model,
                        onBaseUrlChange = { viewModel.updateProvider(provider.copy(baseUrl = it)) },
                        onApiKeyChange = { viewModel.updateProvider(provider.copy(apiKey = it)) },
                        onModelChange = { viewModel.updateProvider(provider.copy(model = it)) }
                    )
                }
                is ApiProvider.Ollama -> {
                    ProviderFields(
                        baseUrl = provider.baseUrl,
                        apiKey = "",
                        model = provider.model,
                        onBaseUrlChange = { viewModel.updateProvider(provider.copy(baseUrl = it)) },
                        onApiKeyChange = {},
                        onModelChange = { viewModel.updateProvider(provider.copy(model = it)) },
                        showApiKey = false
                    )
                }
                is ApiProvider.KoboldAI -> {
                    ProviderFields(
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        model = provider.model,
                        onBaseUrlChange = { viewModel.updateProvider(provider.copy(baseUrl = it)) },
                        onApiKeyChange = { viewModel.updateProvider(provider.copy(apiKey = it)) },
                        onModelChange = { viewModel.updateProvider(provider.copy(model = it)) }
                    )
                }
                is ApiProvider.Gemini -> {
                    ProviderFields(
                        baseUrl = "",
                        apiKey = provider.apiKey,
                        model = provider.model,
                        onBaseUrlChange = {},
                        onApiKeyChange = { viewModel.updateProvider(provider.copy(apiKey = it)) },
                        onModelChange = { viewModel.updateProvider(provider.copy(model = it)) },
                        showBaseUrl = false
                    )
                }
                is ApiProvider.OpenRouter -> {
                    ProviderFields(
                        baseUrl = "https://openrouter.ai/api/v1",
                        apiKey = provider.apiKey,
                        model = provider.model,
                        onBaseUrlChange = {},
                        onApiKeyChange = { viewModel.updateProvider(provider.copy(apiKey = it)) },
                        onModelChange = { viewModel.updateProvider(provider.copy(model = it)) },
                        showBaseUrl = false
                    )
                }
                is ApiProvider.Custom -> {
                    ProviderFields(
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        model = provider.model,
                        onBaseUrlChange = { viewModel.updateProvider(provider.copy(baseUrl = it)) },
                        onApiKeyChange = { viewModel.updateProvider(provider.copy(apiKey = it)) },
                        onModelChange = { viewModel.updateProvider(provider.copy(model = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderFields(
    baseUrl: String,
    apiKey: String,
    model: String,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    showApiKey: Boolean = true,
    showBaseUrl: Boolean = true
) {
    var localBaseUrl by remember(baseUrl) { mutableStateOf(baseUrl) }
    var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var localModel by remember(model) { mutableStateOf(model) }

    if (showBaseUrl) {
        OutlinedTextField(
            value = localBaseUrl,
            onValueChange = {
                localBaseUrl = it
                onBaseUrlChange(it)
            },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (showApiKey) {
        OutlinedTextField(
            value = localApiKey,
            onValueChange = {
                localApiKey = it
                onApiKeyChange(it)
            },
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
    OutlinedTextField(
        value = localModel,
        onValueChange = {
            localModel = it
            onModelChange(it)
        },
        label = { Text(stringResource(R.string.model)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun ConnectionTestSection(testState: ConnectionTestState, viewModel: SettingsViewModel) {
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
                    Text(stringResource(R.string.connection_test), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.connection_test_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { viewModel.testConnection() },
                    enabled = testState !is ConnectionTestState.Testing
                ) {
                    Text(if (testState is ConnectionTestState.Testing) stringResource(R.string.testing) else stringResource(R.string.test))
                }
            }

            when (val state = testState) {
                is ConnectionTestState.Success -> {
                    Text(
                        text = stringResource(R.string.test_reply, state.reply),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is ConnectionTestState.Error -> {
                    Text(
                        text = stringResource(R.string.test_error, state.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                else -> {}
            }
        }
    }
}
