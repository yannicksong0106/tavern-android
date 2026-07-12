package com.tavern.lite.ui.screens.quickreply

/**
 * 从 STscript 源码里收集「已定义」的名字，供编辑器变量引用辅助（点击 chip 插入 `{{name}}`）。
 *
 * 收集来源（与 parser 的命令别名对齐）：
 * - `/setvar name ...` / `/set name ...`
 * - `/getvar name` / `/get name`（读取处也算见过该名）
 * - `/clearvar name` / `/clear name`
 * - `/macro name ...` / `/def name ...`（宏名收进来便于 /call 引用）
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

private val COMMENT_COMMANDS = setOf("comment", "rem")

private val VARIABLE_REFERENCE_REGEX = Regex("""\{\{\s*([^}\s]+)\s*}}""")

fun collectStScriptVariableNames(source: String): List<String> {
    val ordered = LinkedHashSet<String>()

    // 单遍逐行扫描：命令定义名与该行内 {{引用}} 按出现顺序一起收集，
    // 保证返回顺序 = 名字在源码里的真实首次出现顺序（两阶段扫描会破坏顺序）。
    source.split("\n").forEach { rawLine ->
        val line = rawLine.trim()

        // 注释行（`#` / `//` / /comment / /rem）在执行时是惰性的，
        // 其中的 {{name}} 不是真实引用，跳过整行以免产生幻影 chip（X4 审计 Low）。
        if (line.startsWith("#") || line.startsWith("//")) return@forEach

        if (line.startsWith("/")) {
            val cmd = line.drop(1).takeWhile { !it.isWhitespace() }.lowercase()
            if (cmd in COMMENT_COMMANDS) return@forEach
            if (cmd in VARIABLE_DEFINING_COMMANDS) {
                val rest = line.drop(1).drop(cmd.length).trim()
                val name = rest.takeWhile { !it.isWhitespace() }
                // 拒绝 `{{...}}` 开头的伪名字：`/setvar {{x}} val` 的定义名不该是 `{{x}}`（X4 审计 Low）。
                if (name.isNotEmpty() && !name.startsWith("{{")) ordered += name
            }
        }

        for (match in VARIABLE_REFERENCE_REGEX.findAll(line)) {
            val name = match.groupValues[1]
            if (name.isNotEmpty()) ordered += name
        }
    }

    return ordered.toList()
}
