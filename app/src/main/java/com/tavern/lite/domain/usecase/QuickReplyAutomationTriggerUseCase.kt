package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.repository.QuickReplyRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickReplyAutomationTriggerUseCase @Inject constructor(
    private val quickReplyRepository: QuickReplyRepository,
    private val stScriptLiteExecutor: StScriptLiteExecutor
) {
    suspend operator fun invoke(
        automationId: String,
        characterId: Long? = null,
        chatId: Long? = null,
        initialVariables: Map<String, String> = emptyMap()
    ): QuickReplyAutomationTriggerResult {
        val normalizedAutomationId = automationId.trim()
        if (normalizedAutomationId.isBlank()) {
            return QuickReplyAutomationTriggerResult(automationId = normalizedAutomationId)
        }

        val replies = quickReplyRepository.getRepliesByAutomationId(
            automationId = normalizedAutomationId,
            characterId = characterId,
            chatId = chatId
        )

        var variables = initialVariables
        val entries = replies.map { reply ->
            if (reply.requiresConfirmation) {
                QuickReplyAutomationExecution(
                    reply = reply,
                    result = StScriptExecutionResult(variables = variables),
                    skippedReason = "Confirmation is required"
                )
            } else {
                val result = stScriptLiteExecutor.execute(
                    quickReply = reply,
                    initialVariables = variables,
                    autoRun = true
                )
                variables = result.variables
                QuickReplyAutomationExecution(reply = reply, result = result)
            }
        }

        return QuickReplyAutomationTriggerResult(
            automationId = normalizedAutomationId,
            executions = entries,
            variables = variables
        )
    }
}

data class QuickReplyAutomationTriggerResult(
    val automationId: String,
    val executions: List<QuickReplyAutomationExecution> = emptyList(),
    val variables: Map<String, String> = emptyMap()
) {
    val hasMatches: Boolean
        get() = executions.isNotEmpty()

    val actions: List<StScriptAction>
        get() = executions.flatMap { it.result.actions }

    val echoes: List<String>
        get() = executions.flatMap { it.result.echoes }.filter { it.isNotBlank() }

    val blockedReasons: List<String>
        get() = executions.flatMap { execution ->
            buildList {
                execution.skippedReason?.let(::add)
                addAll(execution.result.blockedCommands.map { it.reason })
            }
        }
}

data class QuickReplyAutomationExecution(
    val reply: QuickReplyEntity,
    val result: StScriptExecutionResult,
    val skippedReason: String? = null
)
