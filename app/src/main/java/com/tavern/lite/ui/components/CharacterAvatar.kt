package com.tavern.lite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun CharacterAvatar(
    name: String,
    avatarPath: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    // File().exists() 是主线程磁盘 stat；快滚列表每个新可见头像都会命中组合体，
    // 未 remember 会在滚动组合路径反复同步 stat 掉帧。按 avatarPath 缓存（X2 审计 Med）。
    val exists = remember(avatarPath) { avatarPath != null && File(avatarPath).exists() }
    if (exists) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(avatarPath))
                .memoryCacheKey(avatarPath)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .semantics { contentDescription = name }
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
