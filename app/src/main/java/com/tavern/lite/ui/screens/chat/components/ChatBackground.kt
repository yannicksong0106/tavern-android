package com.tavern.lite.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tavern.lite.ui.components.presetBackgrounds
import java.io.File

@Composable
fun ChatBackground(
    backgroundPath: String?,
    onBackgroundMissing: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (backgroundPath == null) return

    Box(modifier = modifier) {
        if (backgroundPath.startsWith("preset:")) {
            val presetId = backgroundPath.removePrefix("preset:")
            val preset = presetBackgrounds.find { it.id == presetId }
            if (preset != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(preset.colors))
                )
            }
        } else {
            val file = File(backgroundPath)
            val fileExists = remember(backgroundPath) { file.exists() }
            if (fileExists) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file)
                        .memoryCacheKey(backgroundPath)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LaunchedEffect(backgroundPath) {
                    onBackgroundMissing()
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
    }
}
