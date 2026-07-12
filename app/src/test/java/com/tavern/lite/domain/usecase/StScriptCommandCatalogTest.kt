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
