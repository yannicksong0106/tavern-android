package com.tavern.lite.ui.screens.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.ui.screens.chat.QuickReplyUiResult

@Composable
fun QuickReplyPanel(
    replies: List<QuickReplyEntity>,
    executeReply: (QuickReplyEntity) -> QuickReplyUiResult,
    onResult: (QuickReplyUiResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var replyToConfirm by remember { mutableStateOf<QuickReplyEntity?>(null) }

    fun runReply(reply: QuickReplyEntity) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onResult(executeReply(reply))
    }

    replyToConfirm?.let { reply ->
        QuickReplyConfirmDialog(
            reply = reply,
            onConfirm = {
                replyToConfirm = null
                runReply(reply)
            },
            onDismiss = { replyToConfirm = null }
        )
    }

    QuickReplyBar(
        replies = replies,
        onReplyClick = { reply ->
            if (reply.requiresConfirmation) {
                replyToConfirm = reply
            } else {
                runReply(reply)
            }
        },
        modifier = modifier
    )
}
