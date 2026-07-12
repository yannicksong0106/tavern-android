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
    fun `automation reply warns when single line macro contains unsafe command`() {
        val warnings = buildQuickReplyItemWarnings(
            script = """
                /macro unsafe /send hello
                /call unsafe
            """.trimIndent(),
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertEquals(listOf(QuickReplyItemWarning.AutomationBlocksUnsafeCommands), warnings)
    }

    @Test
    fun `automation reply warns when nested single line macro contains unsafe command`() {
        val warnings = buildQuickReplyItemWarnings(
            script = """
                /macro outer /macro inner /trigger next
                /call outer
                /call inner
            """.trimIndent(),
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertEquals(listOf(QuickReplyItemWarning.AutomationBlocksUnsafeCommands), warnings)
    }

    @Test
    fun `automation reply does not warn when single line macro is safe`() {
        val warnings = buildQuickReplyItemWarnings(
            script = """
                /macro safe /echo hello
                /call safe
            """.trimIndent(),
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertTrue(warnings.isEmpty())
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
    fun `automation reply warns when macro nesting exceeds analysis depth`() {
        // Regression: 达到 MAX_MACRO_WARNING_DEPTH 时必须视作"可能不安全"(fail-closed)。
        // 之前 `return false` 让 17 层嵌套单行宏 body 里的 /send 绕过 validation。
        val depth = 20
        val script = buildString {
            repeat(depth) { append("/macro m$it ") }
            append("/send hi")
            append('\n')
            append("/call m0")
        }

        val warnings = buildQuickReplyItemWarnings(
            script = script,
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertEquals(listOf(QuickReplyItemWarning.AutomationBlocksUnsafeCommands), warnings)
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

    @Test
    fun `unknown command warns even for manual reply`() {
        val warnings = buildQuickReplyItemWarnings(
            script = "/notacommand foo",
            automationId = "",
            requiresConfirmation = false,
            allowAutoRun = false
        )

        assertEquals(listOf(QuickReplyItemWarning.ContainsUnknownCommand), warnings)
    }

    @Test
    fun `unknown command warning coexists with automation warnings`() {
        val warnings = buildQuickReplyItemWarnings(
            script = "/typo bar\n/send hi",
            automationId = "assistant_reply",
            requiresConfirmation = false,
            allowAutoRun = true
        )

        assertEquals(
            listOf(
                QuickReplyItemWarning.ContainsUnknownCommand,
                QuickReplyItemWarning.AutomationBlocksUnsafeCommands
            ),
            warnings
        )
    }

    @Test
    fun `known commands and aliases produce no unknown warning`() {
        val warnings = buildQuickReplyItemWarnings(
            script = "/set mood calm\n/gen\n/echo done",
            automationId = "",
            requiresConfirmation = false,
            allowAutoRun = false
        )

        assertTrue(warnings.isEmpty())
    }
}
