package com.tavern.lite.ui.screens.quickreply

import org.junit.Assert.assertEquals
import org.junit.Test

class StScriptInsertionTest {

    @Test
    fun `insert into empty script places template at start`() {
        val result = insertStScriptCommand("", 0, 0, "/echo ")

        assertEquals("/echo ", result.text)
        assertEquals(6, result.selection)
    }

    @Test
    fun `insert at line start does not add newline`() {
        val current = "/setvar a 1\n"
        val result = insertStScriptCommand(current, current.length, current.length, "/echo ")

        assertEquals("/setvar a 1\n/echo ", result.text)
        assertEquals(current.length + 6, result.selection)
    }

    @Test
    fun `insert mid line prepends newline so command owns its line`() {
        val current = "/setvar a 1"
        val result = insertStScriptCommand(current, current.length, current.length, "/echo ")

        assertEquals("/setvar a 1\n/echo ", result.text)
        assertEquals("/setvar a 1\n/echo ".length, result.selection)
    }

    @Test
    fun `insert replaces active selection`() {
        val current = "/send hello"
        // 选中 "/send hello" 全部
        val result = insertStScriptCommand(current, 0, current.length, "/echo ")

        assertEquals("/echo ", result.text)
        assertEquals(6, result.selection)
    }

    @Test
    fun `insert in middle splits text and prepends newline`() {
        val current = "abcdef"
        val result = insertStScriptCommand(current, 3, 3, "/echo ")

        assertEquals("abc\n/echo def", result.text)
        assertEquals("abc\n/echo ".length, result.selection)
    }

    @Test
    fun `out of range selection is clamped`() {
        val current = "hi"
        val result = insertStScriptCommand(current, 99, 99, "/echo ")

        assertEquals("hi\n/echo ", result.text)
        assertEquals("hi\n/echo ".length, result.selection)
    }

    @Test
    fun `appendParam inserts fragment at cursor without newline`() {
        // /if 命令后点操作符 chip：应接在同一行，不补换行。
        val current = "/if {{mood}} "
        val result = appendStScriptParam(current, current.length, current.length, "== ")

        assertEquals("/if {{mood}} == ", result.text)
        assertEquals("/if {{mood}} == ".length, result.selection)
    }

    @Test
    fun `appendParam replaces active selection`() {
        val current = "/delay 999"
        // 选中 "999"
        val result = appendStScriptParam(current, 7, current.length, "1000")

        assertEquals("/delay 1000", result.text)
        assertEquals("/delay 1000".length, result.selection)
    }

    @Test
    fun `appendParam mid line does not prepend newline`() {
        // 与 insertStScriptCommand 的区别：参数片段永远接在光标处，不独占行。
        val current = "/if x > "
        val result = appendStScriptParam(current, current.length, current.length, "5")

        assertEquals("/if x > 5", result.text)
        assertEquals("/if x > 5".length, result.selection)
    }

    @Test
    fun `appendParam clamps out of range selection`() {
        val current = "/delay "
        val result = appendStScriptParam(current, 99, 99, "1000")

        assertEquals("/delay 1000", result.text)
        assertEquals("/delay 1000".length, result.selection)
    }
}
