package com.tavern.lite.ui.screens.quickreply

import com.tavern.lite.data.model.StScriptCommand
import com.tavern.lite.data.model.StScriptCommandType
import com.tavern.lite.domain.usecase.StScriptLiteParser

enum class QuickReplyItemWarning {
    AutomationRequiresAutoRun,
    AutomationSkipsConfirmation,
    AutomationBlocksUnsafeCommands,
    ContainsUnknownCommand
}

fun buildQuickReplyItemWarnings(
    script: String,
    automationId: String,
    requiresConfirmation: Boolean,
    allowAutoRun: Boolean,
    parser: StScriptLiteParser = StScriptLiteParser(),
    // 调用方若已算过 findUnknownCommandLines（UI 侧 remember(script)），传入复用避免重扫；
    // 默认自算保持既有测试与独立调用零改动（X 审计 Low：同脚本原本扫两遍）。
    hasUnknownCommand: Boolean = containsUnknownCommand(script)
): List<QuickReplyItemWarning> {
    return buildList {
        // 未知命令警告与 automation 无关：手动回复也该提示手写错命令名。
        if (hasUnknownCommand) {
            add(QuickReplyItemWarning.ContainsUnknownCommand)
        }

        if (automationId.isBlank()) return@buildList

        if (requiresConfirmation) add(QuickReplyItemWarning.AutomationSkipsConfirmation)
        if (!allowAutoRun) {
            add(QuickReplyItemWarning.AutomationRequiresAutoRun)
        } else if (containsUnsafeAutoRunCommand(script, parser)) {
            add(QuickReplyItemWarning.AutomationBlocksUnsafeCommands)
        }
    }
}

// 复用 findUnknownCommandLines 而非 parser 的 Unknown 判定：
// 二者对空 token（裸 `/`）、前导空白（`/   typo`）的处理需保持一致，
// 否则会出现「警告说有未知命令，但行诊断/高亮都定位不到」的三路径分歧（X4 审计 Medium）。
private fun containsUnknownCommand(script: String): Boolean =
    findUnknownCommandLines(script).isNotEmpty()

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
