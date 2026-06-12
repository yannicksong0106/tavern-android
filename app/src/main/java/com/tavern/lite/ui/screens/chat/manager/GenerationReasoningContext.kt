package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.domain.helper.MessageExecutionHelper

internal class GenerationReasoningContext {
    private val reasoningByAssistantMessageId = mutableMapOf<Long, String>()

    fun previousFor(assistantMessageId: Long): String? {
        return reasoningByAssistantMessageId[assistantMessageId]
    }

    fun record(result: MessageExecutionHelper.ExecutionResult?) {
        val assistantMessageId = result?.assistantMsgId ?: return
        val reasoningContent = result.reasoningContent
        if (reasoningContent == null) {
            reasoningByAssistantMessageId.remove(assistantMessageId)
        } else {
            reasoningByAssistantMessageId[assistantMessageId] = reasoningContent
        }
    }
}
