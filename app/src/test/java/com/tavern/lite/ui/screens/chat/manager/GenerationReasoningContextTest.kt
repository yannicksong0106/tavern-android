package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.domain.helper.MessageExecutionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationReasoningContextTest {

    @Test
    fun `record stores reasoning by assistant message id`() {
        val context = GenerationReasoningContext()

        context.record(
            MessageExecutionHelper.ExecutionResult(
                assistantMsgId = 10L,
                reasoningContent = "thinking"
            )
        )

        assertEquals("thinking", context.previousFor(10L))
        assertNull(context.previousFor(11L))
    }

    @Test
    fun `record removes stale reasoning when new result has none`() {
        val context = GenerationReasoningContext()

        context.record(
            MessageExecutionHelper.ExecutionResult(
                assistantMsgId = 10L,
                reasoningContent = "old"
            )
        )
        context.record(MessageExecutionHelper.ExecutionResult(assistantMsgId = 10L))

        assertNull(context.previousFor(10L))
    }

    @Test
    fun `record ignores results without assistant message id`() {
        val context = GenerationReasoningContext()

        context.record(MessageExecutionHelper.ExecutionResult(reasoningContent = "thinking"))

        assertNull(context.previousFor(10L))
    }
}
