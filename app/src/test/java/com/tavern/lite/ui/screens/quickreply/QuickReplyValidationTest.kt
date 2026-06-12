package com.tavern.lite.ui.screens.quickreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyValidationTest {
    @Test
    fun `automation reply warns when auto run is disabled`() {
        val warnings = buildQuickReplyItemWarnings(
            script = "/input hello",
            automationId = "chat_open",
            requiresConfirmation = false,
            allowAutoRun = false
        )

        assertEquals(listOf(QuickReplyItemWarning.AutomationRequiresAutoRun), warnings)
    }

    @Test
    fun `automation reply warns when confirmation would skip automatic trigger`() {
        val warnings = buildQuickReplyItemWarnings(
            script = "/input hello",
            automationId = "assistant_reply",
            requiresConfirmation = true,
            allowAutoRun = true
        )

        assertEquals(listOf(QuickReplyItemWarning.AutomationSkipsConfirmation), warnings)
    }

    @Test
    fun `automation reply warns when auto run contains unsafe commands`() {
        val warnings = buildQuickReplyItemWarnings(
            script = """
                /setvar mood calm
                /send hello
            """.trimIndent(),
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertEquals(listOf(QuickReplyItemWarning.AutomationBlocksUnsafeCommands), warnings)
    }

    @Test
    fun `automation warning follows parser aliases for unsafe generation commands`() {
        val warnings = buildQuickReplyItemWarnings(
            script = """
                /generate next beat
                /gen again
            """.trimIndent(),
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertEquals(listOf(QuickReplyItemWarning.AutomationBlocksUnsafeCommands), warnings)
    }

    @Test
    fun `automation reply does not warn for comments and safe parser commands`() {
        val warnings = buildQuickReplyItemWarnings(
            script = """
                # note
                // also note
                /set mood calm
                /get mood
                /echo done
                /input draft
            """.trimIndent(),
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `manual reply has no automation warnings`() {
        val warnings = buildQuickReplyItemWarnings(
            script = "/send hello",
            automationId = "",
            requiresConfirmation = true,
            allowAutoRun = false
        )

        assertTrue(warnings.isEmpty())
    }
}
