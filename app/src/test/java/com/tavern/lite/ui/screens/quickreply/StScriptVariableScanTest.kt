package com.tavern.lite.ui.screens.quickreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StScriptVariableScanTest {

    @Test
    fun `empty script yields no names`() {
        assertTrue(collectStScriptVariableNames("").isEmpty())
    }

    @Test
    fun `setvar name is collected`() {
        assertEquals(listOf("mood"), collectStScriptVariableNames("/setvar mood happy"))
    }

    @Test
    fun `set alias is collected`() {
        assertEquals(listOf("mood"), collectStScriptVariableNames("/set mood happy"))
    }

    @Test
    fun `getvar and clearvar names collected`() {
        val source = "/getvar a\n/clearvar b"
        assertEquals(listOf("a", "b"), collectStScriptVariableNames(source))
    }

    @Test
    fun `macro name collected`() {
        assertEquals(listOf("greet"), collectStScriptVariableNames("/macro greet /echo hi"))
    }

    @Test
    fun `variable reference in braces collected`() {
        assertEquals(listOf("mood"), collectStScriptVariableNames("/echo {{mood}}"))
    }

    @Test
    fun `names are deduped and ordered by first appearance`() {
        val source = "/setvar a 1\n/echo {{b}}\n/setvar a 2\n/getvar c"
        assertEquals(listOf("a", "b", "c"), collectStScriptVariableNames(source))
    }

    @Test
    fun `braces with whitespace are trimmed`() {
        assertEquals(listOf("mood"), collectStScriptVariableNames("/echo {{ mood }}"))
    }

    @Test
    fun `non-defining commands do not collect their argument`() {
        // /send 的参数不是变量名
        assertTrue(collectStScriptVariableNames("/send hello world").isEmpty())
    }

    @Test
    fun `setvar without name collects nothing`() {
        assertTrue(collectStScriptVariableNames("/setvar").isEmpty())
    }

    @Test
    fun `brace-wrapped defining arg is not captured as bogus name`() {
        // X4 审计 Low：`/setvar {{x}} val` 的定义名不该是 `{{x}}`；
        // 同行的 {{x}} 引用仍按引用收集，结果只含真实名字 x。
        assertEquals(listOf("x"), collectStScriptVariableNames("/setvar {{x}} val"))
    }

    @Test
    fun `comment line references produce no phantom names`() {
        // X4 审计 Low：注释行的 {{ghost}} 不是真实引用，不该产生幻影 chip。
        assertTrue(collectStScriptVariableNames("/comment {{ghost}}").isEmpty())
        assertTrue(collectStScriptVariableNames("# {{ghost}}").isEmpty())
        assertTrue(collectStScriptVariableNames("// {{ghost}}").isEmpty())
    }

    @Test
    fun `whitespace after slash still collects defining name`() {
        // X4 审计 Low：`/   setvar counter 5` 斜杠后有空白，
        // 应与 parser 对齐先 trim 再取命令 token，counter 仍进 chip。
        assertEquals(listOf("counter"), collectStScriptVariableNames("/   setvar counter 5"))
    }
}
