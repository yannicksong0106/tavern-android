package com.tavern.lite.ui.screens.quickreply

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.tavern.lite.domain.usecase.StScriptCommandCatalog

/**
 * STscript 语法高亮所用的颜色。抽成数据类便于在预览/测试中注入固定值，
 * UI 层从 MaterialTheme 取色后传入。
 */
data class StScriptHighlightColors(
    val command: Color,
    val knownAlias: Color,
    val comment: Color,
    val variable: Color,
    val unknown: Color
)

/**
 * 所有已知命令名 + 别名（不含前导 `/`），小写。用于区分已知命令与拼错的未知命令。
 */
private val KNOWN_COMMAND_TOKENS: Set<String> =
    StScriptCommandCatalog.commands
        .flatMap { listOf(it.name) + it.aliases }
        .map { it.lowercase() }
        .toSet()

private val VARIABLE_REGEX = Regex("""\{\{[^}]*}}""")

/**
 * 把 STscript 源码渲染为高亮 [AnnotatedString]。纯逻辑、不改变字符数，
 * 因此可配合 `OffsetMapping.Identity` 用于 [androidx.compose.ui.text.input.VisualTransformation]。
 *
 * 高亮规则（逐行）：
 * - `#` / `//` 开头整行注释 → [StScriptHighlightColors.comment]。
 * - `/命令` 行首命令 token：已知命令 → [StScriptHighlightColors.command]；未知 → [StScriptHighlightColors.unknown]。
 * - 任意位置的 `{{变量}}` → [StScriptHighlightColors.variable]。
 *
 * 该函数供 [StScriptHighlighterTest] 覆盖 span 边界与已知/未知判定。
 */
fun highlightStScript(source: String, colors: StScriptHighlightColors): AnnotatedString {
    return buildAnnotatedString {
        append(source)

        var lineStart = 0
        for (line in source.split("\n")) {
            val lineEnd = lineStart + line.length
            highlightLine(line, lineStart, colors)
            // +1 跳过被 split 吃掉的换行符
            lineStart = lineEnd + 1
        }

        // 变量高亮跨行统一扫描（变量不会跨行，但整体正则更简单）
        for (match in VARIABLE_REGEX.findAll(source)) {
            addStyle(SpanStyle(color = colors.variable), match.range.first, match.range.last + 1)
        }
    }
}

private fun AnnotatedString.Builder.highlightLine(
    line: String,
    lineStart: Int,
    colors: StScriptHighlightColors
) {
    val trimmedStart = line.indexOfFirst { !it.isWhitespace() }
    if (trimmedStart < 0) return

    val content = line.substring(trimmedStart)
    val contentStart = lineStart + trimmedStart

    if (content.startsWith("#") || content.startsWith("//")) {
        addStyle(SpanStyle(color = colors.comment), contentStart, lineStart + line.length)
        return
    }

    if (!content.startsWith("/")) return

    // 取 `/` 之后到首个空白前的 token 作为命令名
    val afterSlash = content.drop(1)
    val token = afterSlash.takeWhile { !it.isWhitespace() }
    if (token.isEmpty()) return

    val tokenStart = contentStart
    val tokenEnd = contentStart + 1 + token.length
    val color = if (token.lowercase() in KNOWN_COMMAND_TOKENS) colors.command else colors.unknown
    addStyle(SpanStyle(color = color), tokenStart, tokenEnd)
}
