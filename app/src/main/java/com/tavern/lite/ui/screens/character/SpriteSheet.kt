package com.tavern.lite.ui.screens.character

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.SpriteEntity
import com.tavern.lite.data.repository.SpriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 立绘管理弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpriteSheet(
    characterId: Long,
    spriteRepository: SpriteRepository,
    onDismiss: () -> Unit
) {
    val sprites by spriteRepository.getSpritesForCharacter(characterId)
        .collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SpriteEntity?>(null) }

    // Image picker for adding sprites
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Save image and create sprite
            val spriteDir = File(context.filesDir, "sprites")
            spriteDir.mkdirs()
            val spriteFile = File(spriteDir, "sprite_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                spriteFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            showAddDialog = true
            // Store the path for use in the dialog
            _pendingSpritePath = spriteFile.absolutePath
        }
    }

    // Delete confirmation
    deleteTarget?.let { sprite ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.vn_delete_sprite)) },
            text = { Text(stringResource(R.string.vn_delete_sprite_text)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        spriteRepository.deleteSprite(sprite.id)
                        // Delete file
                        File(sprite.imagePath).delete()
                        deleteTarget = null
                    }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Add sprite dialog
    if (showAddDialog && _pendingSpritePath != null) {
        AddSpriteDialog(
            onDismiss = {
                showAddDialog = false
                _pendingSpritePath = null
            },
            onConfirm = { emotion ->
                val path = _pendingSpritePath
                if (path != null) {
                    scope.launch {
                        spriteRepository.addSprite(characterId, emotion, path)
                    }
                }
                showAddDialog = false
                _pendingSpritePath = null
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.vn_sprites),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.vn_sprites_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (sprites.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vn_no_sprites),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.vn_no_sprites_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(sprites) { sprite ->
                        SpriteItem(
                            sprite = sprite,
                            onDelete = { deleteTarget = sprite }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add button
            androidx.compose.material3.Button(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.vn_add_sprite))
            }
        }
    }
}

@Composable
private fun SpriteItem(
    sprite: SpriteEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Sprite preview
            AsyncImage(
                model = File(sprite.imagePath),
                contentDescription = sprite.emotion,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Emotion label
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sprite.emotion.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = sprite.imagePath.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.vn_delete_sprite),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddSpriteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var emotion by remember { mutableStateOf("neutral") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vn_add_sprite)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.vn_emotion),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = emotion,
                    onValueChange = { emotion = it.lowercase().trim() },
                    placeholder = { Text(stringResource(R.string.vn_emotion_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(emotion) },
                enabled = emotion.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// Temporary storage for pending sprite path
private var _pendingSpritePath: String? = null
