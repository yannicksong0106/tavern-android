package com.tavern.lite.domain.usecase

/**
 * STscript Lite 命令参考条目。用于编辑 UI 的命令面板（chip 插入 + 参数提示）。
 *
 * [name] 是插入的主命令名（不含前导 `/`）；[insertTemplate] 是点击 chip 时插入到脚本的完整文本，
 * 通常形如 `/name `（尾随空格便于继续输入参数）。[usage] 是简短用法示例，[summary] 是一句话说明。
 */
data class StScriptCommandInfo(
    val name: String,
    val aliases: List<String>,
    val insertTemplate: String,
    val usage: String,
    val summary: String,
    val safeForAutoRun: Boolean,
    /**
     * 参数级补全提示：点击命令 chip 后可继续点选的参数片段（操作符、单位占位等）。
     * 每一项点击后追加到脚本光标处（[StScriptParamHint.insert]）。空列表表示该命令无参数提示。
     */
    val paramHints: List<StScriptParamHint> = emptyList()
)

/**
 * 单个参数提示片段。[label] 是 chip 显示文本，[insert] 是点击后插入脚本的文本
 * （通常尾随空格便于继续输入；纯占位数值则不带空格）。
 */
data class StScriptParamHint(
    val label: String,
    val insert: String
)

/**
 * STscript Lite 支持的全部命令参考。顺序即命令面板展示顺序（高频/安全命令靠前）。
 *
 * 该清单与 [StScriptLiteParser] 的 parse 分支一一对应；新增命令时两处需同步更新，
 * [StScriptCommandCatalogTest] 会校验此清单不遗漏 parser 已识别的命令名。
 */
object StScriptCommandCatalog {

    val commands: List<StScriptCommandInfo> = listOf(
        StScriptCommandInfo(
            name = "echo",
            aliases = emptyList(),
            insertTemplate = "/echo ",
            usage = "/echo 文本",
            summary = "在聊天中显示一段文本，不触发生成。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "setvar",
            aliases = listOf("set"),
            insertTemplate = "/setvar ",
            usage = "/setvar 名称 值",
            summary = "设置一个脚本变量。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "getvar",
            aliases = listOf("get"),
            insertTemplate = "/getvar ",
            usage = "/getvar 名称",
            summary = "读取变量并显示其值。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "clearvar",
            aliases = listOf("clear"),
            insertTemplate = "/clearvar ",
            usage = "/clearvar 名称",
            summary = "清除一个脚本变量。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "if",
            aliases = emptyList(),
            insertTemplate = "/if ",
            usage = "/if {{变量}} == 值",
            summary = "条件为假时跳过下一行命令。",
            safeForAutoRun = true,
            paramHints = listOf(
                StScriptParamHint(label = "==", insert = "== "),
                StScriptParamHint(label = "!=", insert = "!= "),
                StScriptParamHint(label = ">", insert = "> "),
                StScriptParamHint(label = "<", insert = "< "),
                StScriptParamHint(label = ">=", insert = ">= "),
                StScriptParamHint(label = "<=", insert = "<= "),
                StScriptParamHint(label = "contains", insert = "contains ")
            )
        ),
        StScriptCommandInfo(
            name = "delay",
            aliases = listOf("sleep", "wait"),
            insertTemplate = "/delay ",
            usage = "/delay 毫秒",
            summary = "等待指定毫秒后继续。",
            safeForAutoRun = true,
            paramHints = listOf(
                StScriptParamHint(label = "500ms", insert = "500"),
                StScriptParamHint(label = "1s", insert = "1000"),
                StScriptParamHint(label = "2s", insert = "2000"),
                StScriptParamHint(label = "5s", insert = "5000")
            )
        ),
        StScriptCommandInfo(
            name = "input",
            aliases = listOf("setinput"),
            insertTemplate = "/input ",
            usage = "/input 文本",
            summary = "把文本填入输入框，不自动发送。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "comment",
            aliases = listOf("rem"),
            insertTemplate = "/comment ",
            usage = "/comment 备注",
            summary = "注释，执行时忽略。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "macro",
            aliases = listOf("def"),
            insertTemplate = "/macro ",
            usage = "/macro 名称 命令",
            summary = "定义单行或多行（配合 /endmacro）宏。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "call",
            aliases = listOf("invoke"),
            insertTemplate = "/call ",
            usage = "/call 名称",
            summary = "调用已定义的宏。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "endmacro",
            aliases = listOf("enddef"),
            insertTemplate = "/endmacro",
            usage = "/endmacro",
            summary = "结束多行宏定义。",
            safeForAutoRun = true
        ),
        StScriptCommandInfo(
            name = "send",
            aliases = emptyList(),
            insertTemplate = "/send ",
            usage = "/send 文本",
            summary = "以用户身份发送消息（需授权）。",
            safeForAutoRun = false
        ),
        StScriptCommandInfo(
            name = "trigger",
            aliases = listOf("generate", "gen"),
            insertTemplate = "/trigger ",
            usage = "/trigger [文本]",
            summary = "触发一次 AI 生成（需授权）。",
            safeForAutoRun = false
        ),
        StScriptCommandInfo(
            name = "continue",
            aliases = emptyList(),
            insertTemplate = "/continue",
            usage = "/continue",
            summary = "续写上一条 AI 消息（需授权）。",
            safeForAutoRun = false
        ),
        StScriptCommandInfo(
            name = "cancel",
            aliases = listOf("stop"),
            insertTemplate = "/cancel",
            usage = "/cancel",
            summary = "取消进行中的生成。",
            safeForAutoRun = false
        )
    )
}
