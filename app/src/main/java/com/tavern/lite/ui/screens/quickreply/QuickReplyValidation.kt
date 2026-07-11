package com.tavern.lite.ui.screens.quickreply

import com.tavern.lite.data.model.StScriptCommand
import com.tavern.lite.data.model.StScriptCommandType
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
        } else if (containsUnsafeAutoRunCommand(script, parser)) {
            add(QuickReplyItemWarning.AutomationBlocksUnsafeCommands)
        }
    }
}

private fun containsUnsafeAutoRunCommand(
    script: String,
    parser: StScriptLiteParser,
    depth: Int = 0
): Boolean {
    // 分析深度耗尽 → 无法证明安全，保守判定为"含不安全命令"。
    // 此前返回 false 会让 17+ 层嵌套单行宏绕过警告，而 executor 仍会执行其内部的 /send。
    if (depth >= MAX_MACRO_WARNING_DEPTH) return true

    return parser.parse(script).commands.any { command ->
        !command.isSafeForAutoRun || command.hasUnsafeSingleLineMacroBody(parser, depth)
    }
}

private fun StScriptCommand.hasUnsafeSingleLineMacroBody(
    parser: StScriptLiteParser,
    depth: Int
): Boolean =
    type == StScriptCommandType.MacroDef &&
        argument.isNotBlank() &&
        containsUnsafeAutoRunCommand(argument, parser, depth + 1)

private const val MAX_MACRO_WARNING_DEPTH = 16
