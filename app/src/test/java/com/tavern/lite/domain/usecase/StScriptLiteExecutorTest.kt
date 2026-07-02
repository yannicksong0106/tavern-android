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

    // ==================== Phase X: 新增命令测试 ====================

    @Test
    fun `parser recognizes delay command`() {
        val program = parser.parse("/delay 1000")
        assertEquals(StScriptCommandType.Delay, program.commands[0].type)
        assertEquals("1000", program.commands[0].argument)
    }

    @Test
    fun `parser recognizes delay aliases sleep and wait`() {
        val program = parser.parse("/sleep 500\n/wait 200")
        assertEquals(StScriptCommandType.Delay, program.commands[0].type)
        assertEquals(StScriptCommandType.Delay, program.commands[1].type)
    }

    @Test
    fun `parser recognizes cancel command`() {
        val program = parser.parse("/cancel")
        assertEquals(StScriptCommandType.Cancel, program.commands[0].type)
    }

    @Test
    fun `parser recognizes cancel alias stop`() {
        val program = parser.parse("/stop")
        assertEquals(StScriptCommandType.Cancel, program.commands[0].type)
    }

    @Test
    fun `parser recognizes clearvar command`() {
        val program = parser.parse("/clearvar mood")
        assertEquals(StScriptCommandType.ClearVar, program.commands[0].type)
        assertEquals("mood", program.commands[0].variableName)
    }

    @Test
    fun `parser recognizes clearvar alias clear`() {
        val program = parser.parse("/clear mood")
        assertEquals(StScriptCommandType.ClearVar, program.commands[0].type)
    }

    @Test
    fun `parser recognizes if command`() {
        val program = parser.parse("/if {{mood}} == happy")
        assertEquals(StScriptCommandType.If, program.commands[0].type)
        assertEquals("{{mood}} == happy", program.commands[0].argument)
    }

    @Test
    fun `delay action produced with valid millis`() {
        val result = executor.execute("/delay 500")
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is StScriptAction.Delay)
        assertEquals(500L, (result.actions[0] as StScriptAction.Delay).millis)
    }

    @Test
    fun `delay with invalid millis produces no action`() {
        val result = executor.execute("/delay abc")
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `delay with zero produces no action`() {
        val result = executor.execute("/delay 0")
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `delay resolves variables`() {
        executor.execute("/setvar wait 300")
        val result = executor.execute(
            source = "/delay {{wait}}",
            initialVariables = mapOf("wait" to "300")
        )
        assertTrue(result.actions.any { it is StScriptAction.Delay && it.millis == 300L })
    }

    @Test
    fun `cancel action produced when canTriggerGeneration`() {
        val result = executor.execute(
            source = "/cancel",
            permissions = StScriptPermissions(canTriggerGeneration = true)
        )
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is StScriptAction.CancelGeneration)
    }

    @Test
    fun `cancel blocked when cannot trigger generation`() {
        val result = executor.execute(
            source = "/cancel",
            permissions = StScriptPermissions(canTriggerGeneration = false)
        )
        assertTrue(result.actions.isEmpty())
        assertTrue(result.hasBlockedCommands)
    }

    @Test
    fun `clearvar removes specific variable`() {
        val result = executor.execute(
            source = "/clearvar mood",
            initialVariables = mapOf("mood" to "happy", "name" to "Alice")
        )
        assertFalse(result.variables.containsKey("mood"))
        assertTrue(result.variables.containsKey("name"))
    }

    @Test
    fun `clearvar without name clears all variables`() {
        val result = executor.execute(
            source = "/clearvar",
            initialVariables = mapOf("mood" to "happy", "name" to "Alice")
        )
        assertTrue(result.variables.isEmpty())
    }

    @Test
    fun `clearvar blocked when cannot write variables`() {
        val result = executor.execute(
            source = "/clearvar mood",
            permissions = StScriptPermissions(canWriteVariables = false),
            initialVariables = mapOf("mood" to "happy")
        )
        assertTrue(result.hasBlockedCommands)
        assertTrue(result.variables.containsKey("mood"))
    }

    @Test
    fun `if command with equality true does not skip`() {
        val result = executor.execute(
            source = "/if {{mood}} == happy",
            initialVariables = mapOf("mood" to "happy")
        )
        // if 条件为真，不产生 blocked
        assertFalse(result.hasBlockedCommands)
    }

    @Test
    fun `if command with equality false skips next command`() {
        val result = executor.execute(
            source = "/if {{mood}} == sad\n/send should_not_send",
            permissions = StScriptPermissions(canSendMessages = true),
            initialVariables = mapOf("mood" to "happy")
        )
        // 条件为假，下一行 /send 被跳过
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `if command with equality true executes next command`() {
        val result = executor.execute(
            source = "/if {{mood}} == happy\n/send should_send",
            permissions = StScriptPermissions(canSendMessages = true),
            initialVariables = mapOf("mood" to "happy")
        )
        // 条件为真，下一行 /send 正常执行
        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is StScriptAction.SendMessage)
        assertEquals("should_send", (result.actions[0] as StScriptAction.SendMessage).content)
    }

    @Test
    fun `if command with contains operator`() {
        val result = executor.execute(
            source = "/if {{text}} contains hello\n/echo matched",
            initialVariables = mapOf("text" to "hello world")
        )
        // 条件为真，/echo 执行
        assertTrue(result.echoes.contains("matched"))
    }

    @Test
    fun `if command with contains false skips next`() {
        val result = executor.execute(
            source = "/if {{text}} contains goodbye\n/echo should_skip",
            initialVariables = mapOf("text" to "hello world")
        )
        // 条件为假，/echo 被跳过
        assertFalse(result.echoes.contains("should_skip"))
    }

    @Test
    fun `if command with numeric comparison`() {
        val result = executor.execute(
            source = "/if {{count}} > 5\n/echo big",
            initialVariables = mapOf("count" to "10")
        )
        assertTrue(result.echoes.contains("big"))
    }

    @Test
    fun `if command with numeric comparison false skips next`() {
        val result = executor.execute(
            source = "/if {{count}} > 5\n/echo should_skip",
            initialVariables = mapOf("count" to "3")
        )
        assertFalse(result.echoes.contains("should_skip"))
    }

    @Test
    fun `if command with non-zero value is truthy`() {
        val result = executor.execute(
            source = "/if {{flag}}\n/echo truthy",
            initialVariables = mapOf("flag" to "1")
        )
        assertTrue(result.echoes.contains("truthy"))
    }

    @Test
    fun `if command with zero value is falsy skips next`() {
        val result = executor.execute(
            source = "/if {{flag}}\n/echo should_skip",
            initialVariables = mapOf("flag" to "0")
        )
        // 0 视为假，跳过下一行
        assertFalse(result.echoes.contains("should_skip"))
    }

    @Test
    fun `new commands are safe for auto-run`() {
        assertTrue(StScriptCommandType.Delay in StScriptCommandType.autoRunSafeCommands)
        assertTrue(StScriptCommandType.ClearVar in StScriptCommandType.autoRunSafeCommands)
        assertTrue(StScriptCommandType.If in StScriptCommandType.autoRunSafeCommands)
    }

    @Test
    fun `cancel is not safe for auto-run`() {
        assertFalse(StScriptCommandType.Cancel in StScriptCommandType.autoRunSafeCommands)
    }
}
