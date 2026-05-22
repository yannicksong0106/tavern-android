package com.tavern.lite.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeUtilsTest {

    @Test
    fun `parseSwipeContent returns empty list for empty json array`() {
        assertEquals(emptyList<String>(), SwipeUtils.parseSwipeContent("[]"))
    }

    @Test
    fun `parseSwipeContent returns empty list for blank string`() {
        assertEquals(emptyList<String>(), SwipeUtils.parseSwipeContent(""))
    }

    @Test
    fun `parseSwipeContent parses single item`() {
        val result = SwipeUtils.parseSwipeContent("""["hello"]""")
        assertEquals(listOf("hello"), result)
    }

    @Test
    fun `parseSwipeContent parses multiple items`() {
        val result = SwipeUtils.parseSwipeContent("""["first","second","third"]""")
        assertEquals(listOf("first", "second", "third"), result)
    }

    @Test
    fun `parseSwipeContent handles invalid JSON gracefully`() {
        assertEquals(emptyList<String>(), SwipeUtils.parseSwipeContent("not json"))
    }

    @Test
    fun `parseSwipeContent handles malformed array`() {
        assertEquals(emptyList<String>(), SwipeUtils.parseSwipeContent("[invalid"))
    }

    @Test
    fun `toJsonArray produces valid JSON array`() {
        val result = SwipeUtils.toJsonArray(listOf("a", "b", "c"))
        assertEquals("""["a","b","c"]""", result)
    }

    @Test
    fun `toJsonArray handles empty list`() {
        assertEquals("[]", SwipeUtils.toJsonArray(emptyList()))
    }

    @Test
    fun `toJsonArray handles single item`() {
        assertEquals("""["hello"]""", SwipeUtils.toJsonArray(listOf("hello")))
    }

    @Test
    fun `toJsonArray handles special characters`() {
        val result = SwipeUtils.toJsonArray(listOf("hello \"world\"", "new\nline"))
        // Should contain escaped quotes and newlines
        val parsed = SwipeUtils.parseSwipeContent(result)
        assertEquals("""hello "world"""", parsed[0])
        assertEquals("new\nline", parsed[1])
    }

    @Test
    fun `toJsonArray and parseSwipeContent are inverses`() {
        val original = listOf("first", "second", "third", "with spaces", "with \"quotes\"")
        val json = SwipeUtils.toJsonArray(original)
        val parsed = SwipeUtils.parseSwipeContent(json)
        assertEquals(original, parsed)
    }

    @Test
    fun `parseSwipeContent handles unicode characters`() {
        val result = SwipeUtils.parseSwipeContent("""["你好","こんにちは","🎉"]""")
        assertEquals(listOf("你好", "こんにちは", "🎉"), result)
    }

    @Test
    fun `toJsonArray handles unicode characters`() {
        val result = SwipeUtils.toJsonArray(listOf("你好", "🎉"))
        val parsed = SwipeUtils.parseSwipeContent(result)
        assertEquals(listOf("你好", "🎉"), parsed)
    }
}
