package com.tavern.lite.ui.screens.quickreply

import com.tavern.lite.domain.usecase.StScriptLiteParser

enum class QuickReplyItemWarning {
    AutomationRequiresAutoRun,
    AutomationSkipsConfirmation,
    AutomationBlocksUnsafeCommands
}

fun buildQuickReplyItemWarnings(
    script: String,
    automationId: String,
    requiresConfirmation: Boolean,
    allowAutoRun: Boolean,
    parser: StScriptLiteParser = StScriptLiteParser()
): List<QuickReplyItemWarning> {
    if (automationId.isBlank()) return emptyList()

    return buildList {
        if (requiresConfirmation) add(QuickReplyItemWarning.AutomationSkipsConfirmation)
        if (!allowAutoRun) {
            add(QuickReplyItemWarning.AutomationRequiresAutoRun)
        } else if (parser.parse(script).commands.any { !it.isSafeForAutoRun }) {
            add(QuickReplyItemWarning.AutomationBlocksUnsafeCommands)
        }
    }
}
