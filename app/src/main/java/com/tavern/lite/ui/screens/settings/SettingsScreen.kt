package com.tavern.lite.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.R
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.data.model.SearchEngine
import com.tavern.lite.data.model.WebSearchConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPersonaClick: () -> Unit = {},
    onDevLogClick: () -> Unit = {},
    onMemoryLibraryClick: () -> Unit = {},
    onApiConfigClick: () -> Unit = {},
    onGenerationParamsClick: () -> Unit = {},
    onChatStyleClick: () -> Unit = {},
    onTtsClick: () -> Unit = {},
    onDataManagementClick: () -> Unit = {},
    onQuickRepliesClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val bubbleStyle by viewModel.bubbleStyle.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val backgroundProactive by viewModel.backgroundProactive.collectAsStateWithLifecycle()
    val ttsSettings by viewModel.ttsSettings.collectAsStateWithLifecycle()
    val webSearchConfig by viewModel.webSearchConfig.collectAsStateWithLifecycle()
    val storageSizes by viewModel.storageSizes.collectAsStateWithLifecycle()

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
            // API 配置
            SectionHeader(stringResource(R.string.api_config))
            val providerModel = when (val p = config.provider) {
                is ApiProvider.OpenAI -> p.model
                is ApiProvider.Claude -> p.model
                is ApiProvider.Ollama -> p.model
                is ApiProvider.KoboldAI -> p.model
                is ApiProvider.Gemini -> p.model
                is ApiProvider.OpenRouter -> p.model
                is ApiProvider.Custom -> p.model
            }
            SettingsEntryCard(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.api_config),
                subtitle = "${config.provider.displayName} / $providerModel",
                onClick = onApiConfigClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 生成参数
            SectionHeader(stringResource(R.string.generation_params))
            SettingsEntryCard(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.generation_params),
                subtitle = "Temperature: ${"%.2f".format(config.temperature)}  |  Top P: ${"%.2f".format(config.topP)}",
                onClick = onGenerationParamsClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // API 超时时间
            SectionHeader(stringResource(R.string.api_timeout))
            ApiTimeoutSection(config.readTimeoutSeconds, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 用户设置
            SectionHeader(stringResource(R.string.user_settings))
            UserSettingsSection(config, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 用户角色
            SectionHeader(stringResource(R.string.user_persona))
            PersonaEntrySection(onPersonaClick)

            Spacer(modifier = Modifier.height(16.dp))

            // 记忆库
            SectionHeader(stringResource(R.string.memory_library))
            MemoryLibraryEntrySection(onMemoryLibraryClick)

            Spacer(modifier = Modifier.height(16.dp))

            // 聊天样式
            SectionHeader(stringResource(R.string.chat_bubble))
            SettingsEntryCard(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.chat_bubble),
                subtitle = "${stringResource(R.string.font_size, bubbleStyle.fontSize)}  |  ${stringResource(R.string.bubble_corner_radius, bubbleStyle.cornerRadius)}",
                onClick = onChatStyleClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(stringResource(R.string.quick_replies))
            SettingsEntryCard(
                icon = Icons.Default.Quickreply,
                title = stringResource(R.string.quick_replies),
                subtitle = stringResource(R.string.quick_replies_desc),
                onClick = onQuickRepliesClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 主题设置（内联）
            SectionHeader(stringResource(R.string.theme))
            ThemeSection(bubbleStyle, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 语言设置（内联）
            SectionHeader(stringResource(R.string.language_setting))
            LanguageSection(language, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 后台主动对话（内联）
            SectionHeader(stringResource(R.string.background_proactive))
            BackgroundProactiveSection(backgroundProactive, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // TTS 语音
            SectionHeader(stringResource(R.string.tts_settings))
            SettingsEntryCard(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = stringResource(R.string.tts_settings),
                subtitle = if (ttsSettings.enabled) {
                    "${stringResource(R.string.tts_enabled)} · ${if (ttsSettings.engine == "openai") stringResource(R.string.tts_engine_openai) else stringResource(R.string.tts_engine_system)}"
                } else {
                    stringResource(R.string.tts_enabled_desc)
                },
                onClick = onTtsClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 网络搜索
            SectionHeader(stringResource(R.string.web_search))
            WebSearchSection(webSearchConfig, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // 数据管理
            SectionHeader(stringResource(R.string.data_management))
            SettingsEntryCard(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.data_management),
                subtitle = "${stringResource(R.string.database_size)}: ${viewModel.formatFileSize(storageSizes.first)}",
                onClick = onDataManagementClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 关于
            SectionHeader(stringResource(R.string.about))
            AboutEntrySection(onDevLogClick)

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
private fun SettingsEntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UserSettingsSection(config: com.tavern.lite.data.model.ApiConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.material3.OutlinedTextField(
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
private fun ApiTimeoutSection(readTimeoutSeconds: Long, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.api_timeout_desc, readTimeoutSeconds),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = readTimeoutSeconds.toFloat(),
                onValueChange = { viewModel.updateReadTimeout(it.toLong()) },
                valueRange = 30f..600f,
                steps = 18,
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

@Composable
private fun MemoryLibraryEntrySection(onMemoryLibraryClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMemoryLibraryClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.memory_library), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.memory_library_desc),
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

@Composable
private fun ThemeSection(style: com.tavern.lite.data.model.BubbleStyleConfig, viewModel: SettingsViewModel) {
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
        "en" to stringResource(R.string.language_english),
        "ja" to stringResource(R.string.language_japanese),
        "ko" to stringResource(R.string.language_korean)
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
                    RadioButton(
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

@Composable
private fun WebSearchSection(config: WebSearchConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 启用开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.web_search_enabled), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.web_search_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { viewModel.updateWebSearchConfig(config.copy(enabled = it)) }
                )
            }

            if (config.enabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // 搜索引擎选择
                var engineExpanded by remember { mutableStateOf(false) }
                val engineLabel = when (config.engine) {
                    SearchEngine.DUCKDUCKGO -> stringResource(R.string.web_search_engine_ddg)
                    SearchEngine.BING -> stringResource(R.string.web_search_engine_bing)
                    SearchEngine.GOOGLE -> stringResource(R.string.web_search_engine_google)
                }
                Text(stringResource(R.string.web_search_engine), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedTextField(
                        value = engineLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { engineExpanded = true },
                        enabled = false,
                        singleLine = true
                    )
                    DropdownMenu(
                        expanded = engineExpanded,
                        onDismissRequest = { engineExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.web_search_engine_ddg)) },
                            onClick = {
                                viewModel.updateWebSearchConfig(config.copy(engine = SearchEngine.DUCKDUCKGO))
                                engineExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.web_search_engine_bing)) },
                            onClick = {
                                viewModel.updateWebSearchConfig(config.copy(engine = SearchEngine.BING))
                                engineExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.web_search_engine_google)) },
                            onClick = {
                                viewModel.updateWebSearchConfig(config.copy(engine = SearchEngine.GOOGLE))
                                engineExpanded = false
                            }
                        )
                    }
                }

                // API Key (Bing/Google)
                if (config.engine != SearchEngine.DUCKDUCKGO) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { viewModel.updateWebSearchConfig(config.copy(apiKey = it)) },
                        label = { Text(stringResource(R.string.web_search_api_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 最大结果数
                Text(
                    text = stringResource(R.string.web_search_max_results, config.maxResults),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = config.maxResults.toFloat(),
                    onValueChange = { viewModel.updateWebSearchConfig(config.copy(maxResults = it.toInt())) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                // 自动搜索
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.web_search_auto), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(R.string.web_search_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.autoSearch,
                        onCheckedChange = { viewModel.updateWebSearchConfig(config.copy(autoSearch = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.web_search_command_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutEntrySection(onDevLogClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDevLogClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_click_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .graphicsLayer { rotationZ = 180f },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
  