package com.tavern.lite.domain.usecase

import com.tavern.lite.data.model.StScriptCommandType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StScriptCommandCatalogTest {

    private val parser = StScriptLiteParser()

    @Test
    fun `every catalog entry parses to a non-unknown command`() {
        StScriptCommandCatalog.commands.forEach { info ->
            val command = parser.parse("/${info.name} x").commands.single()
            assertFalse(
                "命令 /${info.name} 未被 parser 识别（Unknown）",
                command.type == StScriptCommandType.Unknown
            )
        }
    }

    @Test
    fun `every catalog alias parses to same type as primary name`() {
        StScriptCommandCatalog.commands.forEach { info ->
            val primaryType = parser.parse("/${info.name} x").commands.single().type
            info.aliases.forEach { alias ->
                val aliasType = parser.parse("/$alias x").commands.single().type
                assertEquals(
                    "别名 /$alias 应与 /${info.name} 解析为同一类型",
                    primaryType,
                    aliasType
                )
            }
        }
    }

    @Test
    fun `catalog safe flag matches executor auto-run safety`() {
        StScriptCommandCatalog.commands.forEach { info ->
            val type = parser.parse("/${info.name} x").commands.single().type
            val actualSafe = type in StScriptCommandType.autoRunSafeCommands
            assertEquals(
                "命令 /${info.name} 的 safeForAutoRun 标记与执行器不一致",
                actualSafe,
                info.safeForAutoRun
            )
        }
    }

    @Test
    fun `insert template starts with slash and primary name`() {
        StScriptCommandCatalog.commands.forEach { info ->
            assertTrue(
                "insertTemplate 应以 /${info.name} 开头",
                info.insertTemplate.startsWith("/${info.name}")
            )
        }
    }

    @Test
    fun `catalog has no duplicate command names`() {
        val names = StScriptCommandCatalog.commands.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `if param hints cover exactly the executor comparison operators`() {
        // /if 的操作符 chip 必须与执行器 evaluateCondition 支持的操作符一致，
        // 否则用户点选的操作符可能不被识别（或缺失可用操作符）。
        val ifHints = StScriptCommandCatalog.commands.single { it.name == "if" }.paramHints
        val hintOps = ifHints.map { it.insert.trim() }.toSet()
        val executorOps = setOf("==", "!=", ">=", "<=", ">", "<", "contains")
        assertEquals("/if 参数提示与执行器操作符不一致", executorOps, hintOps)
    }

    @Test
    fun `if param hint inserts drive a real branch skip in the executor`() {
        // 端到端：用每个操作符 chip 拼出一条 /if，执行器应据此决定是否跳过下一行。
        val executor = StScriptLiteExecutor(StScriptLiteParser())
        val ifHints = StScriptCommandCatalog.commands.single { it.name == "if" }.paramHints
        ifHints.forEach { hint ->
            val op = hint.insert.trim()
            // 构造一个必为真的条件，下一行 echo 应执行
            val source = when (op) {
                "==" -> "/if a == a\n/echo ok"
                "!=" -> "/if a != b\n/echo ok"
                ">=" -> "/if 2 >= 1\n/echo ok"
                "<=" -> "/if 1 <= 2\n/echo ok"
                ">" -> "/if 2 > 1\n/echo ok"
                "<" -> "/if 1 < 2\n/echo ok"
                "contains" -> "/if hello contains ell\n/echo ok"
                else -> error("未覆盖操作符 $op")
            }
            val result = executor.execute(source = source)
            assertTrue("/if $op 为真时下一行应执行", result.echoes.contains("ok"))
        }
    }

    @Test
    fun `delay param hints are all positive integer millis`() {
        val delayHints = StScriptCommandCatalog.commands.single { it.name == "delay" }.paramHints
        assertTrue("delay 应提供至少一个单位占位", delayHints.isNotEmpty())
        delayHints.forEach { hint ->
            val millis = hint.insert.trim().toLongOrNull()
            assertTrue("delay 占位 ${hint.insert} 应为正整数毫秒", millis != null && millis > 0)
        }
    }

    @Test
    fun `catalog covers every parseable command name`() {
        // parser 识别但目录缺失 → 编辑面板会漏命令。此测试锁死两者一致。
        val catalogNames = StScriptCommandCatalog.commands.map { it.name }.toSet()
        val parserNames = setOf(
            "send", "trigger", "continue", "setvar", "getvar", "echo",
            "input", "comment", "delay", "cancel", "clearvar", "if",
            "macro", "call", "endmacro"
        )
        assertEquals(
            "parser 命令名与目录不一致（缺失或多余）",
            parserNames,
            catalogNames
        )
    }
}
