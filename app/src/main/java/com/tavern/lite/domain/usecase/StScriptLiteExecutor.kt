package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.model.StScriptCommand
import com.tavern.lite.data.model.StScriptCommandType
import com.tavern.lite.data.model.StScriptPermissions
import com.tavern.lite.data.model.StScriptProgram
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StScriptLiteParser @Inject constructor() {
    fun parse(source: String, permissions: StScriptPermissions = StScriptPermissions()): StScriptProgram {
        val commands = source
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parseLine(it) }
            .toList()

        return StScriptProgram(
            source = source,
            commands = commands,
            permissions = permissions
        )
    }

    private fun parseLine(line: String): StScriptCommand {
        if (line.startsWith("#")) {
            return StScriptCommand(StScriptCommandType.Comment, argument = line.removePrefix("#").trim())
        }
        if (line.startsWith("//")) {
            return StScriptCommand(StScriptCommandType.Comment, argument = line.removePrefix("//").trim())
        }
        if (!line.startsWith("/")) {
            return StScriptCommand(StScriptCommandType.SetInput, argument = line)
        }

        val withoutSlash = line.drop(1).trim()
        val commandName = withoutSlash.takeWhile { !it.isWhitespace() }.lowercase()
        val argument = withoutSlash.drop(commandName.length).trim()

        return when (commandName) {
            "send" -> StScriptCommand(StScriptCommandType.Send, argument = argument)
            "trigger", "generate", "gen" -> StScriptCommand(StScriptCommandType.Trigger, argument = argument)
            "continue" -> StScriptCommand(StScriptCommandType.Continue, argument = argument)
            "setvar", "set" -> parseVariableCommand(StScriptCommandType.SetVar, argument)
            "getvar", "get" -> parseVariableCommand(StScriptCommandType.GetVar, argument)
            "echo" -> StScriptCommand(StScriptCommandType.Echo, argument = argument, displayText = argument)
            "input", "setinput" -> StScriptCommand(StScriptCommandType.SetInput, argument = argument)
            "comment", "rem" -> StScriptCommand(StScriptCommandType.Comment, argument = argument)
            "delay", "sleep", "wait" -> StScriptCommand(StScriptCommandType.Delay, argument = argument)
            "cancel", "stop" -> StScriptCommand(StScriptCommandType.Cancel, argument = argument)
            "clearvar", "clear" -> parseVariableCommand(StScriptCommandType.ClearVar, argument)
            "if" -> StScriptCommand(StScriptCommandType.If, argument = argument)
            "macro", "def" -> parseVariableCommand(StScriptCommandType.MacroDef, argument)
            "call", "invoke" -> StScriptCommand(StScriptCommandType.MacroCall, argument = argument, variableName = argument.takeWhile { !it.isWhitespace() })
            "endmacro", "enddef" -> StScriptCommand(StScriptCommandType.MacroEnd)
            else -> StScriptCommand(StScriptCommandType.Unknown, argument = line)
        }
    }

    private fun parseVariableCommand(type: StScriptCommandType, argument: String): StScriptCommand {
        val variableName = argument.takeWhile { !it.isWhitespace() }.ifBlank { null }
        val value = if (variableName == null) "" else argument.drop(variableName.length).trim()
        return StScriptCommand(
            type = type,
            variableName = variableName,
            argument = value
        )
    }
}

@Singleton
class StScriptLiteExecutor @Inject constructor(
    private val parser: StScriptLiteParser
) {
    fun parse(source: String, permissions: StScriptPermissions = StScriptPermissions()): StScriptProgram =
        parser.parse(source, permissions)

    fun execute(
        source: String,
        permissions: StScriptPermissions = StScriptPermissions(),
        initialVariables: Map<String, String> = emptyMap(),
        autoRun: Boolean = false
    ): StScriptExecutionResult = execute(
        program = parser.parse(source, permissions),
        initialVariables = initialVariables,
        autoRun = autoRun
    )

    fun execute(
        quickReply: QuickReplyEntity,
        initialVariables: Map<String, String> = emptyMap(),
        autoRun: Boolean = false
    ): StScriptExecutionResult = execute(
        source = quickReply.script,
        permissions = quickReply.toStScriptPermissions(),
        initialVariables = initialVariables,
        autoRun = autoRun
    )

    fun execute(
        program: StScriptProgram,
        initialVariables: Map<String, String> = emptyMap(),
        autoRun: Boolean = false
    ): StScriptExecutionResult {
        val variables = initialVariables.toMutableMap()
        val macros = mutableMapOf<String, List<StScriptCommand>>()
        val actions = mutableListOf<StScriptAction>()
        val echoes = mutableListOf<String>()
        val blockedCommands = mutableListOf<StScriptBlockedCommand>()
        val unknownCommands = mutableListOf<StScriptCommand>()

        executeCommands(
            commands = program.commands,
            permissions = program.permissions,
            autoRun = autoRun,
            variables = variables,
            macros = macros,
            actions = actions,
            echoes = echoes,
            blockedCommands = blockedCommands,
            unknownCommands = unknownCommands
        )

        return StScriptExecutionResult(
            actions = actions,
            variables = variables,
            echoes = echoes,
            blockedCommands = blockedCommands,
            unknownCommands = unknownCommands
        )
    }

    private fun executeCommands(
        commands: List<StScriptCommand>,
        permissions: StScriptPermissions,
        autoRun: Boolean,
        variables: MutableMap<String, String>,
        macros: MutableMap<String, List<StScriptCommand>>,
        actions: MutableList<StScriptAction>,
        echoes: MutableList<String>,
        blockedCommands: MutableList<StScriptBlockedCommand>,
        unknownCommands: MutableList<StScriptCommand>,
        macroDepth: Int = 0
    ) {
        var skipNext = false
        var index = 0

        while (index < commands.size) {
            val command = commands[index]
            index += 1

            if (skipNext) {
                skipNext = false
                if (command.type == StScriptCommandType.MacroDef && command.argument.isBlank()) {
                    index = skipMacroBlock(command, commands, index, blockedCommands)
                }
                continue
            }

            val autoRunBlockReason = autoRunBlockReason(command, permissions, autoRun)
            if (autoRunBlockReason != null) {
                blockedCommands += StScriptBlockedCommand(command, autoRunBlockReason)
                if (command.type == StScriptCommandType.MacroDef && command.argument.isBlank()) {
                    index = skipMacroBlock(command, commands, index, blockedCommands)
                }
                continue
            }

            when (command.type) {
                StScriptCommandType.Send -> {
                    val content = resolveVariables(command.argument, variables)
                    when {
                        !permissions.canSendMessages ->
                            blockedCommands += StScriptBlockedCommand(command, "Sending messages is not allowed")
                        content.isBlank() ->
                            blockedCommands += StScriptBlockedCommand(command, "Message content is empty")
                        else -> actions += StScriptAction.SendMessage(content)
                    }
                }
                StScriptCommandType.Trigger -> {
                    if (permissions.canTriggerGeneration) {
                        actions += StScriptAction.TriggerGeneration(
                            resolveVariables(command.argument, variables).ifBlank { null }
                        )
                    } else {
                        blockedCommands += StScriptBlockedCommand(command, "Triggering generation is not allowed")
                    }
                }
                StScriptCommandType.Continue -> {
                    if (permissions.canTriggerGeneration) {
                        actions += StScriptAction.ContinueGeneration
                    } else {
                        blockedCommands += StScriptBlockedCommand(command, "Continuing generation is not allowed")
                    }
                }
                StScriptCommandType.SetVar -> {
                    val variableName = command.variableName
                    when {
                        !permissions.canWriteVariables ->
                            blockedCommands += StScriptBlockedCommand(command, "Writing variables is not allowed")
                        variableName.isNullOrBlank() ->
                            blockedCommands += StScriptBlockedCommand(command, "Variable name is empty")
                        else -> variables[variableName] = resolveVariables(command.argument, variables)
                    }
                }
                StScriptCommandType.GetVar -> {
                    val variableName = command.variableName
                    when {
                        !permissions.canReadVariables ->
                            blockedCommands += StScriptBlockedCommand(command, "Reading variables is not allowed")
                        variableName.isNullOrBlank() ->
                            blockedCommands += StScriptBlockedCommand(command, "Variable name is empty")
                        else -> echoes += variables[variableName].orEmpty()
                    }
                }
                StScriptCommandType.Echo -> echoes += resolveVariables(command.displayText ?: command.argument, variables)
                StScriptCommandType.Comment -> Unit
                StScriptCommandType.SetInput -> actions += StScriptAction.SetInput(resolveVariables(command.argument, variables))
                StScriptCommandType.Delay -> {
                    val delayMs = resolveVariables(command.argument, variables).trim().toLongOrNull() ?: 0L
                    if (delayMs > 0) actions += StScriptAction.Delay(delayMs)
                }
                StScriptCommandType.Cancel -> {
                    if (permissions.canTriggerGeneration) {
                        actions += StScriptAction.CancelGeneration
                    } else {
                        blockedCommands += StScriptBlockedCommand(command, "Cancelling generation is not allowed")
                    }
                }
                StScriptCommandType.ClearVar -> {
                    val variableName = command.variableName
                    when {
                        !permissions.canWriteVariables ->
                            blockedCommands += StScriptBlockedCommand(command, "Writing variables is not allowed")
                        variableName.isNullOrBlank() -> variables.clear()
                        else -> variables.remove(variableName)
                    }
                }
                StScriptCommandType.If -> {
                    // /if {{var}} operator value — 条件为假时跳过紧接的下一行命令
                    val conditionResult = evaluateCondition(command.argument, variables)
                    if (!conditionResult) {
                        skipNext = true
                    }
                }
                StScriptCommandType.MacroDef -> {
                    index = defineMacro(
                        command = command,
                        permissions = permissions,
                        commands = commands,
                        nextIndex = index,
                        macros = macros,
                        blockedCommands = blockedCommands
                    )
                }
                StScriptCommandType.MacroCall -> {
                    callMacro(
                        command = command,
                        permissions = permissions,
                        autoRun = autoRun,
                        variables = variables,
                        macros = macros,
                        actions = actions,
                        echoes = echoes,
                        blockedCommands = blockedCommands,
                        unknownCommands = unknownCommands,
                        macroDepth = macroDepth
                    )
                }
                StScriptCommandType.MacroEnd -> {
                    blockedCommands += StScriptBlockedCommand(command, "Macro end without matching definition")
                }
                StScriptCommandType.Unknown -> {
                    unknownCommands += command
                    blockedCommands += StScriptBlockedCommand(command, "Unknown command")
                }
            }
        }
    }

    private fun autoRunBlockReason(
        command: StScriptCommand,
        permissions: StScriptPermissions,
        autoRun: Boolean
    ): String? {
        if (!autoRun) return null
        if (!permissions.allowAutoRun) return "Auto-run is not allowed"
        if (!command.isSafeForAutoRun) return "Command is not safe for auto-run"
        return null
    }

    private fun defineMacro(
        command: StScriptCommand,
        permissions: StScriptPermissions,
        commands: List<StScriptCommand>,
        nextIndex: Int,
        macros: MutableMap<String, List<StScriptCommand>>,
        blockedCommands: MutableList<StScriptBlockedCommand>
    ): Int {
        val macroName = command.variableName
        return when {
            macroName.isNullOrBlank() -> {
                blockedCommands += StScriptBlockedCommand(command, "Macro name is empty")
                // 若是块形式（`/macro` 无 body 参数），仍需消费到 /endmacro，否则 body 会作为顶层命令泄漏执行。
                if (command.argument.isBlank()) {
                    val endIndex = findMacroEnd(commands, nextIndex)
                    if (endIndex == MISSING_MACRO_END_INDEX) commands.size else endIndex + 1
                } else {
                    nextIndex
                }
            }
            command.argument.isBlank() ->
                defineBlockMacro(
                    command = command,
                    commands = commands,
                    nextIndex = nextIndex,
                    macroName = macroName,
                    macros = macros,
                    blockedCommands = blockedCommands
                )
            else -> nextIndex.also {
                macros[macroName] = parser.parse(command.argument, permissions).commands
            }
        }
    }

    private fun defineBlockMacro(
        command: StScriptCommand,
        commands: List<StScriptCommand>,
        nextIndex: Int,
        macroName: String,
        macros: MutableMap<String, List<StScriptCommand>>,
        blockedCommands: MutableList<StScriptBlockedCommand>
    ): Int {
        val endIndex = findMacroEnd(commands, nextIndex)
        if (endIndex == MISSING_MACRO_END_INDEX) {
            blockedCommands += StScriptBlockedCommand(command, "Macro block is missing /endmacro")
            return commands.size
        }

        val body = commands.subList(nextIndex, endIndex).toList()
        if (body.isEmpty()) {
            blockedCommands += StScriptBlockedCommand(command, "Macro body is empty")
        } else {
            macros[macroName] = body
        }
        return endIndex + 1
    }

    private fun callMacro(
        command: StScriptCommand,
        permissions: StScriptPermissions,
        autoRun: Boolean,
        variables: MutableMap<String, String>,
        macros: MutableMap<String, List<StScriptCommand>>,
        actions: MutableList<StScriptAction>,
        echoes: MutableList<String>,
        blockedCommands: MutableList<StScriptBlockedCommand>,
        unknownCommands: MutableList<StScriptCommand>,
        macroDepth: Int
    ) {
        val macroName = command.variableName
        when {
            macroName.isNullOrBlank() ->
                blockedCommands += StScriptBlockedCommand(command, "Macro name is empty")
            macroDepth >= MAX_MACRO_EXPANSION_DEPTH ->
                blockedCommands += StScriptBlockedCommand(command, "Macro expansion limit exceeded")
            !macros.containsKey(macroName) ->
                blockedCommands += StScriptBlockedCommand(command, "Macro is not defined")
            else -> executeCommands(
                commands = macros.getValue(macroName),
                permissions = permissions,
                autoRun = autoRun,
                variables = variables,
                macros = macros,
                actions = actions,
                echoes = echoes,
                blockedCommands = blockedCommands,
                unknownCommands = unknownCommands,
                macroDepth = macroDepth + 1
            )
        }
    }

    private fun resolveVariables(text: String, variables: Map<String, String>): String =
        VARIABLE_PATTERN.replace(text) { match ->
            variables[match.groupValues[1].trim()].orEmpty()
        }

    private fun skipMacroBlock(
        command: StScriptCommand,
        commands: List<StScriptCommand>,
        startIndex: Int,
        blockedCommands: MutableList<StScriptBlockedCommand>
    ): Int {
        val endIndex = findMacroEnd(commands, startIndex)
        if (endIndex == MISSING_MACRO_END_INDEX) {
            blockedCommands += StScriptBlockedCommand(command, "Macro block is missing /endmacro")
            return commands.size
        }
        return endIndex + 1
    }

    private fun findMacroEnd(commands: List<StScriptCommand>, startIndex: Int): Int {
        var nestedBlockDepth = 0
        for (index in startIndex until commands.size) {
            val command = commands[index]
            when {
                command.type == StScriptCommandType.MacroDef && command.argument.isBlank() -> {
                    nestedBlockDepth += 1
                }
                command.type == StScriptCommandType.MacroEnd && nestedBlockDepth == 0 -> return index
                command.type == StScriptCommandType.MacroEnd -> nestedBlockDepth -= 1
            }
        }
        return MISSING_MACRO_END_INDEX
    }

    private fun evaluateCondition(argument: String, variables: Map<String, String>): Boolean {
        val trimmed = argument.trim()
        if (trimmed.isEmpty()) return false

        // 在源文本上定位操作符（先于变量解析），避免变量值中出现的操作符字面量污染匹配。
        // 长操作符优先扫描（`>=` 先于 `>`）。左右两侧分别 resolveVariables。
        for (op in COMPARISON_OPERATORS) {
            val idx = trimmed.indexOf(op)
            if (idx > 0 && idx + op.length < trimmed.length) {
                val leftRaw = trimmed.substring(0, idx).trim()
                val rightRaw = trimmed.substring(idx + op.length).trim()
                if (leftRaw.isEmpty() || rightRaw.isEmpty()) continue
                val left = resolveVariables(leftRaw, variables).trim()
                val right = resolveVariables(rightRaw, variables).trim()
                return when (op) {
                    "==" -> left == right
                    "!=" -> left != right
                    ">=" -> compareNumeric(left, right) { a, b -> a >= b }
                    "<=" -> compareNumeric(left, right) { a, b -> a <= b }
                    ">" -> compareNumeric(left, right) { a, b -> a > b }
                    "<" -> compareNumeric(left, right) { a, b -> a < b }
                    else -> false
                }
            }
        }

        // `contains` 需两侧空白包围，避免匹配变量值或标识符中的 "contains" 子串
        CONTAINS_PATTERN.matchEntire(trimmed)?.let { match ->
            val left = resolveVariables(match.groupValues[1].trim(), variables)
            val right = resolveVariables(match.groupValues[2].trim(), variables)
            return left.contains(right)
        }

        // 无操作符：解析变量后，非空/非 "0"/非 "false" 视为真
        val resolved = resolveVariables(trimmed, variables).trim()
        return resolved.isNotBlank() && resolved != "0" && resolved.lowercase() != "false"
    }

    private inline fun compareNumeric(left: String, right: String, op: (Double, Double) -> Boolean): Boolean {
        val a = left.toDoubleOrNull() ?: return false
        val b = right.toDoubleOrNull() ?: return false
        return op(a, b)
    }

    private companion object {
        private const val MISSING_MACRO_END_INDEX = -1
        private const val MAX_MACRO_EXPANSION_DEPTH = 16
        private val VARIABLE_PATTERN = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")
        // 顺序敏感：两字符操作符必须先于单字符匹配（`>=` 先于 `>`）。
        private val COMPARISON_OPERATORS = listOf("==", "!=", ">=", "<=", ">", "<")
        // `contains` 需两侧空白包围
        private val CONTAINS_PATTERN = Regex("""^(.+?)\s+contains\s+(.+?)$""")
    }
}

sealed class StScriptAction {
    data class SendMessage(val content: String) : StScriptAction()
    data class TriggerGeneration(val userInput: String? = null) : StScriptAction()
    object ContinueGeneration : StScriptAction()
    data class SetInput(val content: String) : StScriptAction()
    data class Delay(val millis: Long) : StScriptAction()
    object CancelGeneration : StScriptAction()
}

data class StScriptBlockedCommand(
    val command: StScriptCommand,
    val reason: String
)

data class StScriptExecutionResult(
    val actions: List<StScriptAction> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val echoes: List<String> = emptyList(),
    val blockedCommands: List<StScriptBlockedCommand> = emptyList(),
    val unknownCommands: List<StScriptCommand> = emptyList()
) {
    val hasBlockedCommands: Boolean
        get() = blockedCommands.isNotEmpty()
}

fun QuickReplyEntity.toStScriptPermissions(): StScriptPermissions =
    StScriptPermissions(
        allowAutoRun = allowAutoRun,
        canSendMessages = canSendMessages,
        canTriggerGeneration = canTriggerGeneration
    )
