package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.model.StScriptCommandType
import com.tavern.lite.data.model.StScriptPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StScriptLiteExecutorTest {
    private val parser = StScriptLiteParser()
    private val executor = StScriptLiteExecutor(parser)

    @Test
    fun `parser converts supported commands`() {
        val program = parser.parse(
            """
            # note
            /send hello
            /trigger next beat
            /continue
            /setvar mood bright
            /getvar mood
            /echo done
            /input draft
            plain draft
            """.trimIndent()
        )

        assertEquals(
            listOf(
                StScriptCommandType.Comment,
                StScriptCommandType.Send,
                StScriptCommandType.Trigger,
                StScriptCommandType.Continue,
                StScriptCommandType.SetVar,
                StScriptCommandType.GetVar,
                StScriptCommandType.Echo,
                StScriptCommandType.SetInput,
                StScriptCommandType.SetInput
            ),
            program.commands.map { it.type }
        )
        assertEquals("mood", program.commands[4].variableName)
        assertEquals("bright", program.commands[4].argument)
    }

    @Test
    fun `manual execution returns actions and variable echoes`() {
        val result = executor.execute(
            source = """
                /setvar mood calm
                /getvar mood
                /send hello
                /trigger
                /continue
                /input next line
            """.trimIndent(),
            permissions = StScriptPermissions(
                canSendMessages = true,
                canTriggerGeneration = true
            )
        )

        assertEquals("calm", result.variables["mood"])
        assertEquals(listOf("calm"), result.echoes)
        assertEquals(
            listOf(
                StScriptAction.SendMessage("hello"),
                StScriptAction.TriggerGeneration(),
                StScriptAction.ContinueGeneration,
                StScriptAction.SetInput("next line")
            ),
            result.actions
        )
        assertFalse(result.hasBlockedCommands)
    }

    @Test
    fun `execution resolves variables in action arguments and echoes`() {
        val result = executor.execute(
            source = """
                /setvar mood calm
                /setvar draft hello {{mood}}
                /echo {{draft}}
                /send send {{mood}}
                /trigger next {{mood}}
                /input input {{missing}} {{mood}}
            """.trimIndent(),
            permissions = StScriptPermissions(
                canSendMessages = true,
                canTriggerGeneration = true
            )
        )

        assertEquals("hello calm", result.variables["draft"])
        assertEquals(listOf("hello calm"), result.echoes)
        assertEquals(
            listOf(
                StScriptAction.SendMessage("send calm"),
                StScriptAction.TriggerGeneration("next calm"),
                StScriptAction.SetInput("input  calm")
            ),
            result.actions
        )
    }

    @Test
    fun `execution blocks unsafe actions without explicit permissions`() {
        val result = executor.execute(
            source = """
                /send hello
                /trigger now
                /continue
            """.trimIndent()
        )

        assertTrue(result.actions.isEmpty())
        assertEquals(3, result.blockedCommands.size)
        assertTrue(result.blockedCommands[0].reason.contains("Sending"))
        assertTrue(result.blockedCommands[1].reason.contains("Triggering"))
        assertTrue(result.blockedCommands[2].reason.contains("Continuing"))
    }

    @Test
    fun `send command blocks content that resolves to blank`() {
        val result = executor.execute(
            source = "/send {{missing}}",
            permissions = StScriptPermissions(canSendMessages = true)
        )

        assertTrue(result.actions.isEmpty())
        assertEquals(1, result.blockedCommands.size)
        assertEquals("Message content is empty", result.blockedCommands.single().reason)
    }

    @Test
    fun `auto run blocks non safe commands even when send is allowed`() {
        val result = executor.execute(
            source = """
                /setvar mood alert
                /send hello
            """.trimIndent(),
            permissions = StScriptPermissions(
                allowAutoRun = true,
                canSendMessages = true
            ),
            autoRun = true
        )

        assertEquals("alert", result.variables["mood"])
        assertEquals(emptyList<StScriptAction>(), result.actions)
        assertEquals(1, result.blockedCommands.size)
        assertEquals(StScriptCommandType.Send, result.blockedCommands.single().command.type)
        assertTrue(result.blockedCommands.single().reason.contains("auto-run"))
    }

    @Test
    fun `auto run requires allow auto run permission`() {
        val result = executor.execute(
            source = "/echo done",
            autoRun = true
        )

        assertEquals(emptyList<String>(), result.echoes)
        assertEquals(1, result.blockedCommands.size)
        assertTrue(result.blockedCommands.single().reason.contains("Auto-run"))
    }

    @Test
    fun `unknown command is reported and blocked`() {
        val result = executor.execute("/unknown value")

        assertEquals(1, result.unknownCommands.size)
        assertEquals(1, result.blockedCommands.size)
        assertEquals(StScriptCommandType.Unknown, result.unknownCommands.single().type)
    }

    @Test
    fun `quick reply entity maps execution permissions`() {
        val entity = QuickReplyEntity(
            setId = 1,
            label = "Go",
            script = "/send hi",
            allowAutoRun = true,
            canSendMessages = true,
            canTriggerGeneration = false
        )

        val permissions = entity.toStScriptPermissions()

        assertTrue(permissions.allowAutoRun)
        assertTrue(permissions.canSendMessages)
        assertFalse(permissions.canTriggerGeneration)
    }

    @Test
    fun `quick reply entity executes with stored permissions`() {
        val entity = QuickReplyEntity(
            setId = 1,
            label = "Go",
            script = "/send hi",
            canSendMessages = true
        )

        val result = executor.execute(entity)

        assertEquals(listOf(StScriptAction.SendMessage("hi")), result.actions)
        assertFalse(result.hasBlockedCommands)
    }
}
