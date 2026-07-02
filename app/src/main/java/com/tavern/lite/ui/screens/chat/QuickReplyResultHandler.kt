package com.tavern.lite.ui.screens.chat

import android.content.Context
import android.widget.Toast
import com.tavern.lite.R
import com.tavern.lite.domain.usecase.StScriptAction

fun handleQuickReplyResult(
    context: Context,
    result: QuickReplyUiResult,
    allowUnsafeActions: Boolean = true,
    onSetInput: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onTriggerGeneration: (String?) -> Unit,
    onContinueGeneration: () -> Unit,
    onBeforeUnsafeAction: () -> Unit,
    onCancelGeneration: () -> Unit = {}
) {
    result.echoes.forEach { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    result.blockedReasons.forEach { reason ->
        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
    }
    var unsafeActionPrepared = false
    fun prepareUnsafeAction() {
        if (!unsafeActionPrepared) {
            onBeforeUnsafeAction()
            unsafeActionPrepared = true
        }
    }
    result.actions.forEach { action ->
        when (action) {
            is StScriptAction.SetInput -> onSetInput(action.content)
            is StScriptAction.SendMessage -> {
                if (!allowUnsafeActions) {
                    return@forEach showBlockedToast(context, R.string.quick_reply_auto_send_blocked)
                }
                prepareUnsafeAction()
                onSendMessage(action.content)
            }
            is StScriptAction.TriggerGeneration -> {
                if (!allowUnsafeActions) {
                    return@forEach showBlockedToast(context, R.string.quick_reply_auto_generation_blocked)
                }
                prepareUnsafeAction()
                onTriggerGeneration(action.userInput)
            }
            StScriptAction.ContinueGeneration -> {
                if (!allowUnsafeActions) {
                    return@forEach showBlockedToast(context, R.string.quick_reply_auto_continue_blocked)
                }
                prepareUnsafeAction()
                onContinueGeneration()
            }
            is StScriptAction.Delay -> {
                // delay 由 UI 层通过 kotlinx.coroutines.delay 处理
                // 当前实现：忽略 delay（UI 层在 action 间自然异步处理）
            }
            StScriptAction.CancelGeneration -> {
                if (!allowUnsafeActions) {
                    return@forEach showBlockedToast(context, R.string.quick_reply_auto_continue_blocked)
                }
                prepareUnsafeAction()
                onCancelGeneration()
            }
        }
    }
}

private fun showBlockedToast(context: Context, messageResId: Int) {
    Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show()
}
