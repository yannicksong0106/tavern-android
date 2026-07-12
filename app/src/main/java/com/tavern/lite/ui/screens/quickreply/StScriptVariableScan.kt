package com.tavern.lite.ui.screens.quickreply

/**
 * 从 STscript 源码里收集「已定义」的名字，供编辑器变量引用辅助（点击 chip 插入 `{{name}}`）。
 *
 * 收集来源（与 parser 的命令别名对齐）：
 * - `/setvar name ...` / `/set name ...`
 * - `/getvar name` / `/get name`（读取处也算见过该名）
 * - `/clearvar name` / `/clear name`
 * - `/macro name ...` / `/def name ...`（宏名也可 `{{name}}` 无关，但收进来便于 /call 引用）
 * - 源码里已出现的 `{{name}}` 引用本身
 *
 * 返回去重且按首次出现顺序排列的名字列表。纯逻辑，供 [StScriptVariableScanTest] 覆盖。
 */
private val VARIABLE_DEFINING_COMMANDS = setOf(
    "setvar", "set",
    "getvar", "get",
    "clearvar", "clear",
    "macro", "def"
)

private val VARIABLE_REFERENCE_REGEX = Regex("""\{\{\s*([^}\s]+)\s*}}""")

fun collectStScriptVariableNames(source: String): List<String> {
    val ordered = LinkedHashSet<String>()

    // 单遍逐行扫描：命令定义名与该行内 {{引用}} 按出现顺序一起收集，
    // 保证返回顺序 = 名字在源码里的真实首次出现顺序（两阶段扫描会破坏顺序）。
    source.split("\n").forEach { rawLine ->
        val line = rawLine.trim()

        if (line.startsWith("/")) {
            val token = line.drop(1).takeWhile { !it.isWhitespace() }.lowercase()
            if (token in VARIABLE_DEFINING_COMMANDS) {
                val rest = line.drop(1).drop(token.length).trim()
                val name = rest.takeWhile { !it.isWhitespace() }
                if (name.isNotEmpty()) ordered += name
            }
        }

        for (match in VARIABLE_REFERENCE_REGEX.findAll(line)) {
            val name = match.groupValues[1]
            if (name.isNotEmpty()) ordered += name
        }
    }

    return ordered.toList()
}
