package com.tavern.lite.ui.screens.quickreply

import com.tavern.lite.domain.usecase.StScriptCommandCatalog

/**
 * 编辑器行内诊断：定位未识别命令所在的原始行号（1 起）。
 *
 * 与 [com.tavern.lite.domain.usecase.StScriptLiteParser] 不同，此扫描保留空行与原始行号，
 * 因此可用于在编辑器里精确提示「第 N 行：未识别命令 /foo」。parser 会 trim + 丢空行，
 * 行号会偏，故诊断走独立扫描，不复用 parser 的 command 列表。
 */
data class StScriptLineDiagnostic(
    val line: Int,
    val commandName: String
)

/** 已知命令名 + 别名（不含前导 `/`），小写。 */
private val KNOWN_COMMAND_TOKENS: Set<String> =
    StScriptCommandCatalog.commands
        .flatMap { listOf(it.name) + it.aliases }
        .map { it.lowercase() }
        .toSet()

/**
 * 扫描 [source]，返回所有以 `/命令` 开头但命令名不在 catalog 里的行。
 *
 * 规则（与 parser 的 [com.tavern.lite.domain.usecase.StScriptLiteParser.parseLine] 对齐）：
 * - 空行、`#` / `//` 注释行、不以 `/` 开头的纯文本行都不算命令，跳过。
 * - `/` 之后到首个空白前的 token 即命令名；不在 [KNOWN_COMMAND_TOKENS] → 记为未知。
 */
fun findUnknownCommandLines(source: String): List<StScriptLineDiagnostic> {
    val diagnostics = mutableListOf<StScriptLineDiagnostic>()
    source.split("\n").forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEachIndexed
        if (line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
        if (!line.startsWith("/")) return@forEachIndexed

        val token = line.drop(1).takeWhile { !it.isWhitespace() }
        if (token.isEmpty()) return@forEachIndexed
        if (token.lowercase() !in KNOWN_COMMAND_TOKENS) {
            diagnostics += StScriptLineDiagnostic(line = index + 1, commandName = token)
        }
    }
    return diagnostics
}
