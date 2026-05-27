package com.tavern.lite.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StringUtilsTest {

    @Test
    fun `cleanCharacterPrefix removes prefix with colon`() {
        val result = "[Alice]: Hello there".cleanCharacterPrefix("Alice")
        assertEquals("Hello there", result)
    }

    @Test
    fun `cleanCharacterPrefix removes prefix with fullwidth colon`() {
        val result = "[Alice]\uFF1AHello there".cleanCharacterPrefix("Alice")
        assertEquals("Hello there", result)
    }

    @Test
    fun `cleanCharacterPrefix removes prefix with colon and spaces`() {
        val result = "[Alice]:   Hello there".cleanCharacterPrefix("Alice")
        assertEquals("Hello there", result)
    }

    @Test
    fun `cleanCharacterPrefix returns original when no prefix`() {
        val result = "Hello there".cleanCharacterPrefix("Alice")
        assertEquals("Hello there", result)
    }

    @Test
    fun `cleanCharacterPrefix handles empty string`() {
        val result = "".cleanCharacterPrefix("Alice")
        assertEquals("", result)
    }

    @Test
    fun `cleanCharacterPrefix handles prefix only`() {
        val result = "[Alice]:".cleanCharacterPrefix("Alice")
        assertEquals("", result)
    }

    @Test
    fun `cleanCharacterPrefix trims whitespace`() {
        val result = "  [Alice]: Hello  ".cleanCharacterPrefix("Alice")
        assertEquals("Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix does not remove wrong name`() {
        val result = "[Bob]: Hello".cleanCharacterPrefix("Alice")
        assertEquals("[Bob]: Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix handles multiline content`() {
        val result = "[Alice]: Line 1\nLine 2".cleanCharacterPrefix("Alice")
        assertEquals("Line 1\nLine 2", result)
    }

    @Test
    fun `cleanCharacterPrefix handles prefix with tabs`() {
        val result = "[Alice]:\tHello".cleanCharacterPrefix("Alice")
        assertEquals("Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix handles content with special characters`() {
        val result = "[Alice]: Hello! How are you? 😊".cleanCharacterPrefix("Alice")
        assertEquals("Hello! How are you? 😊", result)
    }

    @Test
    fun `cleanCharacterPrefix handles prefix without separator`() {
        val result = "[Alice]Hello".cleanCharacterPrefix("Alice")
        assertEquals("Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix handles Japanese name`() {
        val result = "[さくら]: こんにちは".cleanCharacterPrefix("さくら")
        assertEquals("こんにちは", result)
    }

    @Test
    fun `cleanCharacterPrefix handles case sensitive name`() {
        val result = "[alice]: Hello".cleanCharacterPrefix("Alice")
        assertEquals("[alice]: Hello", result)
    }

    @Test
    fun `cleanCharacterPrefix handles multiple colons`() {
        val result = "[Alice]:::Hello".cleanCharacterPrefix("Alice")
        assertEquals("Hello", result)
    }
}
