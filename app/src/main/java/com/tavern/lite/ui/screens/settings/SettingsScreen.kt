package com.tavern.lite.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.store.TtsSettings
import com.tavern.lite.ui.theme.AssistantBubbleDark
import com.tavern.lite.ui.theme.AssistantBubbleLight
import com.tavern.lite.ui.theme.UserBubbleDark
import com.tavern.lite.ui.theme.UserBubbleLight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPersonaClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val bubbleStyle by viewModel.bubbleStyle.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val backgroundProactive by viewModel.backgroundProactive.collectAsStateWithLifecycle()
    val ttsSettings by viewModel.ttsSettings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            // API 配置区
            SectionHeader(stringResource(R.string.api_config))
            ApiProviderSection(config, viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            // 连接测试
            ConnectionTestSection(viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 生成参数区
            SectionHeader(stringResource(R.string.generation_params))
            GenerationParamsSection(config, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 用户设置
            SectionHeader(stringResource(R.string.user_settings))
            UserSettingsSection(config, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 用户角色
            SectionHeader(stringResource(R.string.user_persona))
            PersonaEntrySection(onPersonaClick)

            Spacer(modifier = Modifier.height(16.dp))

            // 聊天气泡样式
            SectionHeader(stringResource(R.string.chat_bubble))
            BubbleStyleSection(bubbleStyle, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 主题设置
            SectionHeader(stringResource(R.string.theme))
            ThemeSection(bubbleStyle, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 语言设置
            SectionHeader(stringResource(R.string.language_setting))
            LanguageSection(language, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 后台主动对话
            SectionHeader(stringResource(R.string.background_proactive))
            BackgroundProactiveSection(backgroundProactive, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // TTS 语音朗读
            SectionHeader(stringResource(R.string.tts_settings))
            TtsSettingsSection(ttsSettings, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 关于
            SectionHeader(stringResource(R.string.about))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ConnectionTestSection(viewModel: SettingsViewModel) {
    val testState by viewModel.testState.collectAsStateWithLifecycle()

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

            // 测试结果
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiProviderSection(config: ApiConfig, viewModel: SettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val providers = listOf("OpenAI", "Claude", "Ollama", "KoboldAI", "Gemini", "Custom")
    val currentProviderName = config.provider.displayName

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Provider 选择
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
                                    else -> ApiProvider.Custom()
                                }
                                viewModel.updateProvider(provider)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 根据 provider 类型显示不同的配置项
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
    if (showBaseUrl) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (showApiKey) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
    OutlinedTextField(
        value = model,
        onValueChange = onModelChange,
        label = { Text(stringResource(R.string.model)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun GenerationParamsSection(config: ApiConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Temperature
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

            // Top P
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

            // Frequency Penalty
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

            // Presence Penalty
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

            // Max Tokens
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

            // Context Length
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

@Composable
private fun UserSettingsSection(config: ApiConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = config.userName,
                onValueChange = { viewModel.updateUserName(it) },
                label = { Text(stringResource(R.string.user_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PersonaEntrySection(onPersonaClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPersonaClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.manage_persona), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.manage_persona_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.enter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private val bubbleColorOptions = listOf(
    0L to R.string.default_label,
    Color(0xFF3D3427).toArgb().toLong() and 0xFFFFFFFF to R.string.color_deep_brown,
    Color(0xFF2D3A4A).toArgb().toLong() and 0xFFFFFFFF to R.string.color_deep_blue,
    Color(0xFF3A2D4A).toArgb().toLong() and 0xFFFFFFFF to R.string.color_deep_purple,
    Color(0xFF2D4A3A).toArgb().toLong() and 0xFFFFFFFF to R.string.color_deep_green,
    Color(0xFF4A3A2D).toArgb().toLong() and 0xFFFFFFFF to R.string.color_warm_brown,
    Color(0xFFE8DCC8).toArgb().toLong() and 0xFFFFFFFF to R.string.color_beige,
    Color(0xFFD4E8DC).toArgb().toLong() and 0xFFFFFFFF to R.string.color_mint,
    Color(0xFFDCE0E8).toArgb().toLong() and 0xFFFFFFFF to R.string.color_silver,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BubbleStyleSection(style: BubbleStyleConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 用户气泡颜色
            Text(stringResource(R.string.bubble_color_user), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bubbleColorOptions.forEach { (colorValue, _) ->
                    val isSelected = style.userBubbleColor == colorValue
                    val bgColor = if (colorValue == 0L) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color(colorValue.toInt() or (0xFF000000.toInt()))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable {
                                viewModel.updateBubbleStyle(style.copy(userBubbleColor = colorValue))
                            }
                    ) {
                        if (colorValue == 0L) {
                            Text(
                                text = stringResource(R.string.default_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 助手气泡颜色
            Text(stringResource(R.string.bubble_color_assistant), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bubbleColorOptions.forEach { (colorValue, _) ->
                    val isSelected = style.assistantBubbleColor == colorValue
                    val bgColor = if (colorValue == 0L) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color(colorValue.toInt() or (0xFF000000.toInt()))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable {
                                viewModel.updateBubbleStyle(style.copy(assistantBubbleColor = colorValue))
                            }
                    ) {
                        if (colorValue == 0L) {
                            Text(
                                text = stringResource(R.string.default_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 圆角大小
            Text(
                text = stringResource(R.string.bubble_corner_radius, style.cornerRadius),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = style.cornerRadius.toFloat(),
                onValueChange = {
                    viewModel.updateBubbleStyle(style.copy(cornerRadius = it.toInt()))
                },
                valueRange = 4f..24f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 字体大小
            Text(
                text = stringResource(R.string.font_size, style.fontSize),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = style.fontSize.toFloat(),
                onValueChange = {
                    viewModel.updateBubbleStyle(style.copy(fontSize = it.toInt()))
                },
                valueRange = 12f..20f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ThemeSection(style: BubbleStyleConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.material_you), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.material_you_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = style.dynamicColor,
                onCheckedChange = {
                    viewModel.updateBubbleStyle(style.copy(dynamicColor = it))
                }
            )
        }
    }
}

@Composable
private fun BackgroundProactiveSection(enabled: Boolean, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.background_proactive), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.background_proactive_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { viewModel.updateBackgroundProactive(it) }
            )
        }
    }
}

@Composable
private fun LanguageSection(currentLanguage: String, viewModel: SettingsViewModel) {
    val languages = listOf(
        "system" to stringResource(R.string.language_system),
        "zh" to stringResource(R.string.language_chinese),
        "en" to stringResource(R.string.language_english)
    )

    fun applyLanguage(code: String) {
        viewModel.updateLanguage(code)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            languages.forEach { (code, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { applyLanguage(code) }
                        .padding(vertical = 8.dp)
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = currentLanguage == code,
                        onClick = { applyLanguage(code) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsSettingsSection(ttsSettings: TtsSettings, viewModel: SettingsViewModel) {
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
