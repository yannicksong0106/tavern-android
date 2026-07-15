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
    fun `contains operand with angle-bracket literal is not hijacked by numeric branch`() {
        // X4 审计 Med：`contains` 需先于操作符扫描判定，否则操作数里的 `<`/`>`
        // 字面量会劫持数值分支，compareNumeric 对非数值返回 false，误跳过守卫命令。
        val result = executor.execute(
            source = "/if {{msg}} contains <div>\n/echo matched",
            initialVariables = mapOf("msg" to "<div>hello</div>")
        )
        assertTrue(result.echoes.contains("matched"))
    }

    @Test
    fun `CJK variable name resolves in echo`() {
        // X4 审计 Med：VARIABLE_PATTERN 需匹配非 ASCII 名字，否则 {{计数}} 留字面量。
        val result = executor.execute(
            source = "/setvar 计数 5\n/echo {{计数}}"
        )
        assertTrue(result.echoes.contains("5"))
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
    fun `parser recognizes macro definition and call`() {
        val program = parser.parse("/macro greet /echo hello\n/call greet\n/endmacro")

        assertEquals(StScriptCommandType.MacroDef, program.commands[0].type)
        assertEquals("greet", program.commands[0].variableName)
        assertEquals("/echo hello", program.commands[0].argument)
        assertEquals(StScriptCommandType.MacroCall, program.commands[1].type)
        assertEquals("greet", program.commands[1].variableName)
        assertEquals(StScriptCommandType.MacroEnd, program.commands[2].type)
    }

    @Test
    fun `macro call expands defined echo command`() {
        val result = executor.execute(
            source = """
                /macro greet /echo hello
                /call greet
            """.trimIndent()
        )

        assertEquals(listOf("hello"), result.echoes)
        assertFalse(result.hasBlockedCommands)
    }

    @Test
    fun `macro body resolves variables at call time`() {
        val result = executor.execute(
            source = """
                /macro greet /echo hello {{name}}
                /setvar name Alice
                /call greet
            """.trimIndent()
        )

        assertEquals(listOf("hello Alice"), result.echoes)
    }

    @Test
    fun `macro call can expand send action when permission allows`() {
        val result = executor.execute(
            source = """
                /macro sendHello /send hello
                /call sendHello
            """.trimIndent(),
            permissions = StScriptPermissions(canSendMessages = true)
        )

        assertEquals(listOf(StScriptAction.SendMessage("hello")), result.actions)
        assertFalse(result.hasBlockedCommands)
    }

    @Test
    fun `macro expanded send keeps permission checks`() {
        val result = executor.execute(
            source = """
                /macro sendHello /send hello
                /call sendHello
            """.trimIndent()
        )

        assertTrue(result.actions.isEmpty())
        assertEquals("Sending messages is not allowed", result.blockedCommands.single().reason)
    }

    @Test
    fun `auto run blocks unsafe commands expanded by macro`() {
        val result = executor.execute(
            source = """
                /macro sendHello /send hello
                /call sendHello
            """.trimIndent(),
            permissions = StScriptPermissions(
                allowAutoRun = true,
                canSendMessages = true
            ),
            autoRun = true
        )

        assertTrue(result.actions.isEmpty())
        assertEquals(StScriptCommandType.Send, result.blockedCommands.single().command.type)
        assertEquals("Command is not safe for auto-run", result.blockedCommands.single().reason)
    }

    @Test
    fun `undefined macro call is blocked`() {
        val result = executor.execute("/call missing")

        assertTrue(result.actions.isEmpty())
        assertEquals("Macro is not defined", result.blockedCommands.single().reason)
    }

    @Test
    fun `recursive macro call is blocked at expansion limit`() {
        val result = executor.execute(
            source = """
                /macro loop /call loop
                /call loop
            """.trimIndent()
        )

        assertEquals("Macro expansion limit exceeded", result.blockedCommands.single().reason)
    }

    @Test
    fun `macro definition requires name and body`() {
        val missingName = executor.execute("/macro")
        val missingBody = executor.execute("/macro empty\n/endmacro")

        assertEquals("Macro name is empty", missingName.blockedCommands.single().reason)
        assertEquals("Macro body is empty", missingBody.blockedCommands.single().reason)
    }

    @Test
    fun `block macro does not execute body until call`() {
        val result = executor.execute(
            source = """
                /macro greet
                /echo hello {{name}}
                /setvar mood calm
                /endmacro
                /echo before
                /call greet
            """.trimIndent(),
            initialVariables = mapOf("name" to "Alice")
        )

        assertEquals(listOf("before", "hello Alice"), result.echoes)
        assertEquals("calm", result.variables["mood"])
        assertFalse(result.hasBlockedCommands)
    }

    @Test
    fun `uncalled block macro body is skipped at top level`() {
        val result = executor.execute(
            source = """
                /macro hidden
                /echo hidden
                /setvar mood changed
                /endmacro
                /echo visible
            """.trimIndent(),
            initialVariables = mapOf("mood" to "original")
        )

        assertEquals(listOf("visible"), result.echoes)
        assertEquals("original", result.variables["mood"])
    }

    @Test
    fun `block macro can expand multiple actions`() {
        val result = executor.execute(
            source = """
                /macro act
                /send hello
                /trigger next
                /endmacro
                /call act
            """.trimIndent(),
            permissions = StScriptPermissions(
                canSendMessages = true,
                canTriggerGeneration = true
            )
        )

        assertEquals(
            listOf(
                StScriptAction.SendMessage("hello"),
                StScriptAction.TriggerGeneration("next")
            ),
            result.actions
        )
    }

    @Test
    fun `missing block macro end blocks and skips dangling body`() {
        val result = executor.execute(
            source = """
                /macro broken
                /echo hidden
            """.trimIndent()
        )

        assertTrue(result.echoes.isEmpty())
        assertEquals("Macro block is missing /endmacro", result.blockedCommands.single().reason)
    }

    @Test
    fun `unmatched macro end is blocked`() {
        val result = executor.execute("/endmacro")

        assertEquals("Macro end without matching definition", result.blockedCommands.single().reason)
    }

    @Test
    fun `if false skips whole block macro definition`() {
        val result = executor.execute(
            source = """
                /if {{flag}}
                /macro hidden
                /echo hidden
                /endmacro
                /echo visible
            """.trimIndent(),
            initialVariables = mapOf("flag" to "0")
        )

        assertEquals(listOf("visible"), result.echoes)
        assertFalse(result.hasBlockedCommands)
    }

    @Test
    fun `new commands are safe for auto-run`() {
        assertTrue(StScriptCommandType.Delay in StScriptCommandType.autoRunSafeCommands)
        assertTrue(StScriptCommandType.ClearVar in StScriptCommandType.autoRunSafeCommands)
        assertTrue(StScriptCommandType.If in StScriptCommandType.autoRunSafeCommands)
        assertTrue(StScriptCommandType.MacroDef in StScriptCommandType.autoRunSafeCommands)
        assertTrue(StScriptCommandType.MacroCall in StScriptCommandType.autoRunSafeCommands)
        assertTrue(StScriptCommandType.MacroEnd in StScriptCommandType.autoRunSafeCommands)
    }

    @Test
    fun `cancel is not safe for auto-run`() {
        assertFalse(StScriptCommandType.Cancel in StScriptCommandType.autoRunSafeCommands)
    }

    // ==================== Phase X3 修复回归测试 ====================

    @Test
    fun `nameless block macro definition does not leak body to top level`() {
        // 修复前：`/macro` 无名 → defineMacro 直接返回 nextIndex，body 作为顶层命令执行泄漏。
        val result = executor.execute(
            source = """
                /macro
                /send secret
                /endmacro
                /echo after
            """.trimIndent(),
            permissions = StScriptPermissions(canSendMessages = true)
        )

        assertEquals(listOf("after"), result.echoes)
        // 未定义宏名产生 blocked，但 /send secret 必须被跳过（未产生 action）
        assertTrue(result.actions.isEmpty())
        assertTrue(result.blockedCommands.any { it.reason == "Macro name is empty" })
    }

    @Test
    fun `if false followed by dangling macro reports missing endmacro`() {
        // 修复前：skipMacroBlock 找不到 /endmacro 时静默吞掉剩余命令。
        val result = executor.execute(
            source = """
                /if {{flag}}
                /macro dangling
                /echo lost
            """.trimIndent(),
            initialVariables = mapOf("flag" to "0")
        )

        assertTrue(result.echoes.isEmpty())
        assertTrue(result.blockedCommands.any { it.reason == "Macro block is missing /endmacro" })
    }

    @Test
    fun `if condition ignores operator characters inside variable values`() {
        // 修复前：resolved="a==b >= 5" 会先被 `==` 劫持返回 false。
        val result = executor.execute(
            source = "/if {{code}} >= 5\n/echo pass",
            initialVariables = mapOf("code" to "a==b")
        )
        // code 非数字，>= 应返回 false（compareNumeric 拿不到 double）
        assertFalse(result.echoes.contains("pass"))

        // 反例：数值比较不再被变量值中的 `==` 干扰
        val numeric = executor.execute(
            source = "/if {{n}} >= 5\n/echo pass",
            initialVariables = mapOf("n" to "10")
        )
        assertTrue(numeric.echoes.contains("pass"))
    }

    @Test
    fun `truthy check on string containing word contains returns true`() {
        // 修复前：`contains` 会被识别为操作符，split 后取 left.contains(right)=false。
        val result = executor.execute(
            source = "/if {{desc}}\n/echo truthy",
            initialVariables = mapOf("desc" to "file contains data")
        )
        assertTrue(result.echoes.contains("truthy"))
    }

    @Test
    fun `numeric comparison supports decimal values`() {
        // 修复前：toLongOrNull("7.5") 为 null，7.5 > 5 静默返回 false。
        val result = executor.execute(
            source = "/if {{score}} > 5\n/echo big",
            initialVariables = mapOf("score" to "7.5")
        )
        assertTrue(result.echoes.contains("big"))
    }

    @Test
    fun `mutual macro recursion is bounded by depth limit`() {
        // 回归保护：a -> b -> a -> b ... 应被 macroDepth 上限 16 拦下，不能爆栈。
        val result = executor.execute(
            source = """
                /macro a /call b
                /macro b /call a
                /call a
            """.trimIndent()
        )
        assertTrue(
            "expected macro depth error, got ${result.blockedCommands}",
            result.blockedCommands.any { it.reason.contains("Macro expansion limit exceeded") }
        )
    }

    @Test
    fun `def alias defines macro identically to macro keyword`() {
        val result = executor.execute(
            source = """
                /def greet /echo hello
                /call greet
            """.trimIndent()
        )
        assertTrue(result.echoes.contains("hello"))
    }

    @Test
    fun `invoke alias calls macro identically to call keyword`() {
        val result = executor.execute(
            source = """
                /macro greet /echo hi
                /invoke greet
            """.trimIndent()
        )
        assertTrue(result.echoes.contains("hi"))
    }

    @Test
    fun `enddef alias closes block macro identically to endmacro`() {
        val result = executor.execute(
            source = """
                /macro greet
                /echo hi
                /echo there
                /enddef
                /call greet
            """.trimIndent()
        )
        assertTrue(result.echoes.containsAll(listOf("hi", "there")))
    }
}
