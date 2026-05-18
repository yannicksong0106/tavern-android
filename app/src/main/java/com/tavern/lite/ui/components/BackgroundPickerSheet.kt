package com.tavern.lite.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.tavern.lite.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/**
 * 预设背景：渐变色列表（不需要图片资源，纯代码生成）
 */
data class PresetBackground(
    val id: String,
    val labelResId: Int,
    val colors: List<Color>
)

val presetBackgrounds = listOf(
    PresetBackground("dark_night", R.string.bg_dark_night, listOf(Color(0xFF0D1117), Color(0xFF161B22))),
    PresetBackground("warm_sunset", R.string.bg_warm_sunset, listOf(Color(0xFF2D1B2E), Color(0xFF4A2040), Color(0xFF6B3A5A))),
    PresetBackground("forest", R.string.bg_forest, listOf(Color(0xFF0A1A0F), Color(0xFF1A3A25))),
    PresetBackground("ocean", R.string.bg_ocean, listOf(Color(0xFF0A1628), Color(0xFF152238))),
    PresetBackground("lavender", R.string.bg_lavender, listOf(Color(0xFF1A1025), Color(0xFF2D1B40))),
    PresetBackground("rose_gold", R.string.bg_rose_gold, listOf(Color(0xFF1A1215), Color(0xFF2D1B20))),
    PresetBackground("arctic", R.string.bg_arctic, listOf(Color(0xFF0D1B2A), Color(0xFF1B2D4A), Color(0xFF2A4A3D))),
    PresetBackground("cherry", R.string.bg_cherry, listOf(Color(0xFF1A0F12), Color(0xFF2D1520), Color(0xFF3D1A2A))),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    currentBackgroundPath: String?,
    onSelectPreset: (PresetBackground) -> Unit,
    onSelectImage: (Uri) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSelectImage(uri)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题行
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.select_background),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 自定义图片 + 清除按钮
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.custom_background),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                // 自定义图片按钮
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .aspectRatio(0.56f) // 手机屏幕比例
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { imagePicker.launch("image/*") }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.from_gallery),
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.from_gallery),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 当前自定义背景预览（如果有）
                if (currentBackgroundPath != null) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .aspectRatio(0.56f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onClear() }
                        ) {
                            AsyncImage(
                                model = File(currentBackgroundPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            // "移除" 标签
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.remove_background),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 预设渐变背景
            Text(
                text = stringResource(R.string.preset_backgrounds),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                items(presetBackgrounds) { preset ->
                    PresetBackgroundItem(
                        preset = preset,
                        onClick = {
                            onSelectPreset(preset)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetBackgroundItem(
    preset: PresetBackground,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(0.56f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(preset.colors))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(preset.labelResId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
