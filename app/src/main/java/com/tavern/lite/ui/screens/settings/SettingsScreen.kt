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
import androidx.compose.ui.unit.dp
import com.tavern.lite.data.model.BubbleStyleConfig
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            SectionHeader("API 配置")
            ApiProviderSection(config, viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            // 连接测试
            ConnectionTestSection(viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 生成参数区
            SectionHeader("生成参数")
            GenerationParamsSection(config, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 用户设置
            SectionHeader("用户设置")
            UserSettingsSection(config, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 用户角色
            SectionHeader("用户角色")
            PersonaEntrySection(onPersonaClick)

            Spacer(modifier = Modifier.height(16.dp))

            // 聊天气泡样式
            SectionHeader("聊天气泡")
            BubbleStyleSection(bubbleStyle, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 主题设置
            SectionHeader("主题")
            ThemeSection(bubbleStyle, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 关于
            SectionHeader("关于")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("酒馆 AI", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "版本 1.0.0 · 基于 SillyTavern 数据格式",
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
                    Text("连接测试", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "发送一条测试消息验证 API 配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { viewModel.testConnection() },
                    enabled = testState !is ConnectionTestState.Testing
                ) {
                    Text(if (testState is ConnectionTestState.Testing) "测试中..." else "测试")
                }
            }

            // 测试结果
            when (val state = testState) {
                is ConnectionTestState.Success -> {
                    Text(
                        text = "回复: ${state.reply}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is ConnectionTestState.Error -> {
                    Text(
                        text = "错误: ${state.message}",
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
    val providers = listOf("OpenAI", "Claude", "Ollama", "自定义")
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
    showApiKey: Boolean = true
) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text("Base URL") },
        modifier = Modifier.fillMaxWidth()
    )
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
        label = { Text("模型") },
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
                label = { Text("上下文条数") },
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
                label = { Text("你的名字（用于 {{user}} 占位符）") },
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
                Text("管理用户角色", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "创建和管理你在对话中的身份",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "进入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private val bubbleColorOptions = listOf(
    0L to "默认",
    Color(0xFF3D3427).toArgb().toLong() and 0xFFFFFFFF to "深棕",
    Color(0xFF2D3A4A).toArgb().toLong() and 0xFFFFFFFF to "深蓝",
    Color(0xFF3A2D4A).toArgb().toLong() and 0xFFFFFFFF to "深紫",
    Color(0xFF2D4A3A).toArgb().toLong() and 0xFFFFFFFF to "深绿",
    Color(0xFF4A3A2D).toArgb().toLong() and 0xFFFFFFFF to "暖棕",
    Color(0xFFE8DCC8).toArgb().toLong() and 0xFFFFFFFF to "米色",
    Color(0xFFD4E8DC).toArgb().toLong() and 0xFFFFFFFF to "薄荷",
    Color(0xFFDCE0E8).toArgb().toLong() and 0xFFFFFFFF to "银灰",
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
            Text("用户气泡颜色", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bubbleColorOptions.forEach { (colorValue, label) ->
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
                                text = "默认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 助手气泡颜色
            Text("助手气泡颜色", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bubbleColorOptions.forEach { (colorValue, label) ->
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
                                text = "默认",
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
                text = "气泡圆角: ${style.cornerRadius}dp",
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
                text = "字体大小: ${style.fontSize}sp",
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
                Text("Material You 动态取色", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "跟随系统壁纸配色（Android 12+）",
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
