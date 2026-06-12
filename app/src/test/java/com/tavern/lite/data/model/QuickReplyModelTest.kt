package com.tavern.lite.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyModelTest {

    @Test
    fun `activeReplies returns enabled replies sorted by display order`() {
        val set = QuickReplySet(
            name = "Default",
            replies = listOf(
                QuickReply(label = "Later", script = "/echo later", displayOrder = 2),
                QuickReply(label = "Disabled", script = "/echo disabled", enabled = false, displayOrder = 0),
                QuickReply(label = "First", script = "/echo first", displayOrder = 1)
            )
        )

        assertEquals(listOf("First", "Later"), set.activeReplies().map { it.label })
    }

    @Test
    fun `automation trigger is true only when automation id is present`() {
        assertTrue(QuickReply(label = "Auto", script = "/trigger", automationId = "start").isAutomationTrigger)
        assertFalse(QuickReply(label = "Manual", script = "/send hi").isAutomationTrigger)
    }

    @Test
    fun `STscript program requires explicit auto run permission and safe commands`() {
        val safeProgram = StScriptProgram(
            source = "/setvar mood happy\n/echo done",
            commands = listOf(
                StScriptCommand(type = StScriptCommandType.SetVar, variableName = "mood", argument = "happy"),
                StScriptCommand(type = StScriptCommandType.Echo, argument = "done")
            ),
            permissions = StScriptPermissions(allowAutoRun = true)
        )
        val sendingProgram = safeProgram.copy(
            commands = safeProgram.commands + StScriptCommand(type = StScriptCommandType.Send, argument = "hi")
        )

        assertTrue(safeProgram.isAutoExecutable)
        assertFalse(sendingProgram.isAutoExecutable)
        assertFalse(safeProgram.copy(permissions = StScriptPermissions(allowAutoRun = false)).isAutoExecutable)
    }
}
