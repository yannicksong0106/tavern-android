package com.tavern.lite.ui.screens.quickreply

/**
 * 命令面板插入结果：[text] 为插入后的完整脚本，[selection] 为插入后光标应落在的位置（字符偏移）。
 */
data class StScriptInsertion(
    val text: String,
    val selection: Int
)

/**
 * 在 [current] 文本的光标区间 [[selectionStart], [selectionEnd]] 处插入命令模板 [template]。
 *
 * 规则：
 * - 若插入点不在行首（前一个字符非换行且非空串），先补一个换行，让命令独占一行（STscript 逐行解析）。
 * - 选区非空时替换选区。
 * - 光标落在插入文本末尾。
 *
 * 该函数为纯逻辑，供 [StScriptInsertionTest] 覆盖；UI 层只负责把结果写回 TextFieldValue。
 */
fun insertStScriptCommand(
    current: String,
    selectionStart: Int,
    selectionEnd: Int,
    template: String
): StScriptInsertion {
    val start = selectionStart.coerceIn(0, current.length)
    val end = selectionEnd.coerceIn(start, current.length)

    val before = current.substring(0, start)
    val after = current.substring(end)

    val needsLeadingNewline = before.isNotEmpty() && !before.endsWith("\n")
    val insert = if (needsLeadingNewline) "\n$template" else template

    val newText = before + insert + after
    val cursor = before.length + insert.length
    return StScriptInsertion(text = newText, selection = cursor)
}
