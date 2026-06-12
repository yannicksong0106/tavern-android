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
        val actions = mutableListOf<StScriptAction>()
        val echoes = mutableListOf<String>()
        val blockedCommands = mutableListOf<StScriptBlockedCommand>()
        val unknownCommands = mutableListOf<StScriptCommand>()

        for (command in program.commands) {
            val autoRunBlockReason = autoRunBlockReason(command, program.permissions, autoRun)
            if (autoRunBlockReason != null) {
                blockedCommands += StScriptBlockedCommand(command, autoRunBlockReason)
                continue
            }

            when (command.type) {
                StScriptCommandType.Send -> {
                    val content = resolveVariables(command.argument, variables)
                    when {
                        !program.permissions.canSendMessages ->
                            blockedCommands += StScriptBlockedCommand(command, "Sending messages is not allowed")
                        content.isBlank() ->
                            blockedCommands += StScriptBlockedCommand(command, "Message content is empty")
                        else -> actions += StScriptAction.SendMessage(content)
                    }
                }
                StScriptCommandType.Trigger -> {
                    if (program.permissions.canTriggerGeneration) {
                        actions += StScriptAction.TriggerGeneration(
                            resolveVariables(command.argument, variables).ifBlank { null }
                        )
                    } else {
                        blockedCommands += StScriptBlockedCommand(command, "Triggering generation is not allowed")
                    }
                }
                StScriptCommandType.Continue -> {
                    if (program.permissions.canTriggerGeneration) {
                        actions += StScriptAction.ContinueGeneration
                    } else {
                        blockedCommands += StScriptBlockedCommand(command, "Continuing generation is not allowed")
                    }
                }
                StScriptCommandType.SetVar -> {
                    val variableName = command.variableName
                    when {
                        !program.permissions.canWriteVariables ->
                            blockedCommands += StScriptBlockedCommand(command, "Writing variables is not allowed")
                        variableName.isNullOrBlank() ->
                            blockedCommands += StScriptBlockedCommand(command, "Variable name is empty")
                        else -> variables[variableName] = resolveVariables(command.argument, variables)
                    }
                }
                StScriptCommandType.GetVar -> {
                    val variableName = command.variableName
                    when {
                        !program.permissions.canReadVariables ->
                            blockedCommands += StScriptBlockedCommand(command, "Reading variables is not allowed")
                        variableName.isNullOrBlank() ->
                            blockedCommands += StScriptBlockedCommand(command, "Variable name is empty")
                        else -> echoes += variables[variableName].orEmpty()
                    }
                }
                StScriptCommandType.Echo -> echoes += resolveVariables(command.displayText ?: command.argument, variables)
                StScriptCommandType.Comment -> Unit
                StScriptCommandType.SetInput -> actions += StScriptAction.SetInput(resolveVariables(command.argument, variables))
                StScriptCommandType.Unknown -> {
                    unknownCommands += command
                    blockedCommands += StScriptBlockedCommand(command, "Unknown command")
                }
            }
        }

        return StScriptExecutionResult(
            actions = actions,
            variables = variables,
            echoes = echoes,
            blockedCommands = blockedCommands,
            unknownCommands = unknownCommands
        )
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

    private fun resolveVariables(text: String, variables: Map<String, String>): String =
        VARIABLE_PATTERN.replace(text) { match ->
            variables[match.groupValues[1].trim()].orEmpty()
        }

    private companion object {
        private val VARIABLE_PATTERN = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")
    }
}

sealed class StScriptAction {
    data class SendMessage(val content: String) : StScriptAction()
    data class TriggerGeneration(val userInput: String? = null) : StScriptAction()
    object ContinueGeneration : StScriptAction()
    data class SetInput(val content: String) : StScriptAction()
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
