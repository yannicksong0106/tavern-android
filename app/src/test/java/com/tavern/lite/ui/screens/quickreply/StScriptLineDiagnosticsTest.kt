package com.tavern.lite.ui.screens.quickreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StScriptLineDiagnosticsTest {

    @Test
    fun `known commands produce no diagnostics`() {
        val source = "/echo hi\n/setvar x 1\n/send yo"
        assertTrue(findUnknownCommandLines(source).isEmpty())
    }

    @Test
    fun `unknown command reports its line and name`() {
        val source = "/echo ok\n/bogus arg"
        val diagnostics = findUnknownCommandLines(source)

        assertEquals(1, diagnostics.size)
        assertEquals(2, diagnostics[0].line)
        assertEquals("bogus", diagnostics[0].commandName)
    }

    @Test
    fun `blank lines are counted so line numbers stay accurate`() {
        // parser 会丢空行；此扫描必须保留行号
        val source = "/echo ok\n\n\n/nope"
        val diagnostics = findUnknownCommandLines(source)

        assertEquals(1, diagnostics.size)
        assertEquals(4, diagnostics[0].line)
        assertEquals("nope", diagnostics[0].commandName)
    }

    @Test
    fun `comment and plain text lines are ignored`() {
        val source = "# comment\n// note\njust text\n/echo hi"
        assertTrue(findUnknownCommandLines(source).isEmpty())
    }

    @Test
    fun `alias resolves as known`() {
        val source = "/gen\n/def m /echo hi\n/invoke m"
        assertTrue(findUnknownCommandLines(source).isEmpty())
    }

    @Test
    fun `multiple unknown commands all reported in order`() {
        val source = "/foo\n/echo ok\n/bar\n/baz"
        val diagnostics = findUnknownCommandLines(source)

        assertEquals(listOf(1, 3, 4), diagnostics.map { it.line })
        assertEquals(listOf("foo", "bar", "baz"), diagnostics.map { it.commandName })
    }

    @Test
    fun `leading whitespace before command still resolves name`() {
        val source = "   /nope"
        val diagnostics = findUnknownCommandLines(source)

        assertEquals(1, diagnostics.size)
        assertEquals("nope", diagnostics[0].commandName)
    }

    @Test
    fun `bare slash is not treated as unknown command`() {
        val source = "/ "
        assertTrue(findUnknownCommandLines(source).isEmpty())
    }
}
