package com.tavern.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StScriptProgram(
    val source: String,
    val commands: List<StScriptCommand> = emptyList(),
    val permissions: StScriptPermissions = StScriptPermissions()
) {
    val isAutoExecutable: Boolean
        get() = permissions.allowAutoRun && commands.all { it.isSafeForAutoRun }
}

@Serializable
data class StScriptCommand(
    val type: StScriptCommandType,
    val argument: String = "",
    val variableName: String? = null,
    val displayText: String? = null
) {
    val isSafeForAutoRun: Boolean
        get() = type in StScriptCommandType.autoRunSafeCommands
}

@Serializable
enum class StScriptCommandType {
    Send,
    Trigger,
    Continue,
    SetVar,
    GetVar,
    Echo,
    Comment,
    SetInput,
    Delay,
    Cancel,
    ClearVar,
    If,
    MacroDef,
    MacroCall,
    MacroEnd,
    Unknown;

    companion object {
        val autoRunSafeCommands = setOf(
            Comment,
            SetVar,
            GetVar,
            Echo,
            SetInput,
            Delay,
            ClearVar,
            If,
            MacroDef,
            MacroCall,
            MacroEnd
        )
    }
}

@Serializable
data class StScriptPermissions(
    val allowAutoRun: Boolean = false,
    val canSendMessages: Boolean = false,
    val canTriggerGeneration: Boolean = false,
    val canReadVariables: Boolean = true,
    val canWriteVariables: Boolean = true
)
