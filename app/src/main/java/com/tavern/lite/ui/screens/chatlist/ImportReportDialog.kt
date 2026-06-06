package com.tavern.lite.ui.screens.chatlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavern.lite.R
import com.tavern.lite.util.ImportReport

@Composable
fun ImportReportDialog(
    report: ImportReport,
    onDismiss: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_complete)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.import_format, report.format),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.import_messages_count, report.importedMessages),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (report.skippedMessages > 0) {
                    Text(
                        text = stringResource(R.string.import_skipped_count, report.skippedMessages),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (report.skippedFields.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.import_skipped_fields, report.skippedFields.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (report.warnings.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.import_warnings, report.warnings.joinToString("; ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onNavigateToChat()
            }) {
                Text(stringResource(R.string.open_chat))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
