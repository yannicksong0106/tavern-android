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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.R
import com.tavern.lite.data.model.BubbleStyleConfig

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatStyleScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val bubbleStyle by viewModel.bubbleStyle.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_bubble)) },
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
            BubbleStyleContent(bubbleStyle, viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            ThemeContent(bubbleStyle, viewModel)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BubbleStyleContent(style: BubbleStyleConfig, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
private fun ThemeContent(style: BubbleStyleConfig, viewModel: SettingsViewModel) {
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
