package com.tavern.lite.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.data.db.entity.QuickReplyEntity

@Composable
fun QuickReplyBar(
    replies: List<QuickReplyEntity>,
    onReplyClick: (QuickReplyEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (replies.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        items(replies, key = { it.id }) { reply ->
            AssistChip(
                onClick = { onReplyClick(reply) },
                label = {
                    Text(
                        text = reply.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = reply.icon?.takeIf { it.isNotBlank() }?.let { icon ->
                    {
                        Text(
                            text = icon,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun QuickReplyConfirmDialog(
    reply: QuickReplyEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_reply_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = reply.label,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.quick_reply_confirm_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.quick_reply_run))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
