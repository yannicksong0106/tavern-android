package com.tavern.lite.ui.screens.chat

import com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerResult
import com.tavern.lite.domain.usecase.StScriptAction
import com.tavern.lite.domain.usecase.StScriptExecutionResult

data class QuickReplyUiResult(
    val actions: List<StScriptAction> = emptyList(),
    val echoes: List<String> = emptyList(),
    val blockedReasons: List<String> = emptyList()
)

fun StScriptExecutionResult.toQuickReplyUiResult(): QuickReplyUiResult =
    QuickReplyUiResult(
        actions = actions,
        echoes = buildCleanUiMessages { seen -> echoes.forEach { addCleanUiMessage(it, seen) } },
        blockedReasons = buildCleanUiMessages { seen ->
            blockedCommands.forEach { addCleanUiMessage(it.reason, seen) }
        }
    )

fun QuickReplyAutomationTriggerResult.toQuickReplyUiResult(): QuickReplyUiResult =
    buildQuickReplyUiResult()

private fun QuickReplyAutomationTriggerResult.buildQuickReplyUiResult(): QuickReplyUiResult {
    val actions = mutableListOf<StScriptAction>()
    val echoes = mutableListOf<String>()
    val blockedReasons = mutableListOf<String>()
    val seenEchoes = mutableSetOf<String>()
    val seenBlockedReasons = mutableSetOf<String>()

    executions.forEach { execution ->
        actions += execution.result.actions
        execution.result.echoes.forEach { echoes.addCleanUiMessage(it, seenEchoes) }
        execution.skippedReason?.let { blockedReasons.addCleanUiMessage(it, seenBlockedReasons) }
        execution.result.blockedCommands.forEach {
            blockedReasons.addCleanUiMessage(it.reason, seenBlockedReasons)
        }
    }

    return QuickReplyUiResult(
        actions = actions,
        echoes = echoes,
        blockedReasons = blockedReasons
    )
}

private fun buildCleanUiMessages(block: MutableList<String>.(MutableSet<String>) -> Unit): List<String> {
    val messages = mutableListOf<String>()
    messages.block(mutableSetOf())
    return messages
}

private fun MutableList<String>.addCleanUiMessage(message: String, seen: MutableSet<String>) {
    if (message.isNotBlank() && seen.add(message)) add(message)
}
