package com.tavern.lite.ui.screens.quickreply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity

@Composable
fun QuickReplySetSelector(
    sets: List<QuickReplySetEntity>,
    selectedSetId: Long?,
    characters: List<CharacterEntity>,
    chats: List<ChatEntity>,
    onSelectSet: (Long) -> Unit,
    onEditSet: (QuickReplySetEntity) -> Unit,
    onDeleteSet: (QuickReplySetEntity) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(sets, key = { it.id }) { set ->
            FilterChip(
                selected = set.id == selectedSetId,
                onClick = { onSelectSet(set.id) },
                label = {
                    Text(
                        text = set.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }

    val selectedSet = sets.find { it.id == selectedSetId }
    if (selectedSet != null) {
        QuickReplySetSummary(
            set = selectedSet,
            characters = characters,
            chats = chats,
            onEdit = { onEditSet(selectedSet) },
            onDelete = { onDeleteSet(selectedSet) }
        )
    }
}

@Composable
private fun QuickReplySetSummary(
    set: QuickReplySetEntity,
    characters: List<CharacterEntity>,
    chats: List<ChatEntity>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(set.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = buildScopeLabel(set, characters, chats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun QuickReplyCard(
    reply: QuickReplyEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reply.enabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(reply.icon, reply.label).joinToString(" ")
                        .ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (reply.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = reply.script,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (reply.requiresConfirmation) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.quick_reply_requires_confirmation)) })
                }
                if (reply.canSendMessages) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.quick_reply_can_send)) })
                }
                if (reply.canTriggerGeneration) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.quick_reply_can_trigger)) })
                }
                if (reply.allowAutoRun) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.quick_reply_allow_auto_run)) })
                }
            }
        }
    }
}

@Composable
fun EmptyQuickReplyState(onCreateSet: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp)
    ) {
        Text(stringResource(R.string.quick_reply_no_sets), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCreateSet) {
            Text(stringResource(R.string.quick_reply_add_set))
        }
    }
}

@Composable
fun EmptyQuickReplyReplies(canAdd: Boolean, onAddReply: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp)
    ) {
        Text(stringResource(R.string.quick_reply_no_replies), style = MaterialTheme.typography.titleMedium)
        if (canAdd) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAddReply) {
                Text(stringResource(R.string.quick_reply_add_reply))
            }
        }
    }
}

@Composable
private fun buildScopeLabel(
    set: QuickReplySetEntity,
    characters: List<CharacterEntity>,
    chats: List<ChatEntity>
): String = when (set.scope) {
    "character" -> "${stringResource(R.string.scope_character)} ${buildCharacterScopeName(set.characterId, characters)}"
    "chat" -> "${stringResource(R.string.scope_chat)} ${buildChatScopeName(set.chatId, chats)}"
    else -> stringResource(R.string.scope_global)
}

private fun buildCharacterScopeName(characterId: Long?, characters: List<CharacterEntity>): String {
    if (characterId == null) return "-"
    val character = characters.find { it.id == characterId }
    return character?.let { "${it.name} #${it.id}" } ?: "#$characterId"
}

@Composable
private fun buildChatScopeName(chatId: Long?, chats: List<ChatEntity>): String {
    if (chatId == null) return "-"
    val chat = chats.find { it.id == chatId }
    return chat?.let { "${it.name ?: stringResource(R.string.chat_name_default, it.id)} #${it.id}" } ?: "#$chatId"
}
