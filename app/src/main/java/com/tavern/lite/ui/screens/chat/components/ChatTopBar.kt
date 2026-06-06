package com.tavern.lite.ui.screens.chat.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.ui.components.CharacterAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    character: CharacterEntity?,
    isGroupChat: Boolean,
    groupCharacters: List<CharacterEntity>,
    isGenerating: Boolean,
    respondingCharacter: CharacterEntity?,
    pinnedMessages: List<MessageEntity>,
    onBack: () -> Unit,
    onVnMode: () -> Unit,
    onToggleSearch: () -> Unit,
    onShowBookmarksSheet: () -> Unit,
    onShowSummarySheet: () -> Unit,
    onShowChattinessSheet: () -> Unit,
    onShowBackgroundPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isGroupChat) {
                    Box(modifier = Modifier.size(36.dp)) {
                        val chars = groupCharacters.take(2)
                        chars.forEachIndexed { index, char ->
                            CharacterAvatar(
                                name = char.name,
                                avatarPath = char.avatarPath,
                                size = 24.dp,
                                modifier = Modifier
                                    .align(if (index == 0) Alignment.TopStart else Alignment.BottomEnd)
                                    .padding(if (index == 0) 0.dp else 4.dp)
                            )
                        }
                    }
                } else {
                    CharacterAvatar(
                        name = character?.name ?: "?",
                        avatarPath = character?.avatarPath,
                        size = 36.dp
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = if (isGroupChat) {
                            groupCharacters.joinToString(", ") { it.name }
                        } else {
                            character?.name ?: stringResource(R.string.loading)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    if (isGenerating) {
                        val infiniteTransition = rememberInfiniteTransition(label = "topBar")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.4f,
                            animationSpec = InfiniteRepeatableSpec(
                                animation = androidx.compose.animation.core.tween(800),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "typingAlpha"
                        )
                        val typingName = if (isGroupChat) {
                            respondingCharacter?.name ?: stringResource(R.string.typing)
                        } else {
                            stringResource(R.string.typing)
                        }
                        Text(
                            text = typingName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            IconButton(onClick = onToggleSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
            }
            if (pinnedMessages.isNotEmpty()) {
                IconButton(onClick = onShowBookmarksSheet) {
                    Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.bookmarks))
                }
            }
            IconButton(onClick = onShowSummarySheet) {
                Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.summaries))
            }
            IconButton(onClick = onShowChattinessSheet) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.chat_settings))
            }
            IconButton(onClick = onShowBackgroundPicker) {
                Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.change_background))
            }
            FilledTonalButton(
                onClick = onVnMode,
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "VN",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
