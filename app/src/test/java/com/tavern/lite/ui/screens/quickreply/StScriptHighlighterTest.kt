package com.tavern.lite.ui.screens.quickreply

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StScriptHighlighterTest {

    private val colors = StScriptHighlightColors(
        command = Color(0xFF0000FF),
        knownAlias = Color(0xFF00AAFF),
        comment = Color(0xFF888888),
        variable = Color(0xFF00AA00),
        unknown = Color(0xFFFF0000)
    )

    /** 找到覆盖 [index] 处、颜色为 [color] 的 span，断言其存在。 */
    private fun assertColoredAt(source: String, index: Int, color: Color) {
        val annotated = highlightStScript(source, colors)
        val hit = annotated.spanStyles.any { span ->
            index in span.start until span.end && span.item.color == color
        }
        assertTrue("expected color $color at index $index in \"$source\"", hit)
    }

    @Test
    fun `known command name is colored as command`() {
        // "/echo" 的 e 在 index 1
        assertColoredAt("/echo hi", 1, colors.command)
    }

    @Test
    fun `unknown command name is colored as unknown`() {
        assertColoredAt("/nope arg", 1, colors.unknown)
    }

    @Test
    fun `alias resolves as known command`() {
        // /gen 是 /trigger 别名
        assertColoredAt("/gen", 1, colors.command)
    }

    @Test
    fun `hash comment line is colored as comment`() {
        assertColoredAt("# note here", 2, colors.comment)
    }

    @Test
    fun `slash-slash comment line is colored as comment`() {
        assertColoredAt("// note", 3, colors.comment)
    }

    @Test
    fun `variable braces are colored as variable`() {
        val source = "/echo {{mood}}"
        // {{mood}} 从 index 6 开始
        assertColoredAt(source, 6, colors.variable)
    }

    @Test
    fun `variable inside argument keeps command color on command token`() {
        val source = "/setvar x {{y}}"
        assertColoredAt(source, 1, colors.command)
        assertColoredAt(source, source.indexOf("{{"), colors.variable)
    }

    @Test
    fun `multiline script colors each command line independently`() {
        val source = "/echo one\n/bogus two"
        assertColoredAt(source, 1, colors.command)
        // 第二行 /bogus 的 b 在 "\n" 之后
        val secondCmd = source.indexOf("/bogus") + 1
        assertColoredAt(source, secondCmd, colors.unknown)
    }

    @Test
    fun `plain text line without slash has no command span`() {
        val source = "just plain text"
        val annotated = highlightStScript(source, colors)
        // 无命令/注释/变量 → 无 span
        assertEquals(0, annotated.spanStyles.size)
    }

    @Test
    fun `leading whitespace before command still highlights`() {
        val source = "   /echo hi"
        assertColoredAt(source, source.indexOf("/") + 1, colors.command)
    }

    @Test
    fun `highlighted text preserves original characters`() {
        val source = "/echo {{mood}}\n# comment"
        val annotated = highlightStScript(source, colors)
        assertEquals(source, annotated.text)
    }
}
