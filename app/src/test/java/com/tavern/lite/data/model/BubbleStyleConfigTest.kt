package com.tavern.lite.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BubbleStyleConfigTest {

    // ==================== Default values ====================

    @Test
    fun `default userBubbleColor is 0`() {
        assertEquals(0L, BubbleStyleConfig().userBubbleColor)
    }

    @Test
    fun `default assistantBubbleColor is 0`() {
        assertEquals(0L, BubbleStyleConfig().assistantBubbleColor)
    }

    @Test
    fun `default cornerRadius is 16`() {
        assertEquals(16, BubbleStyleConfig().cornerRadius)
    }

    @Test
    fun `default fontSize is 15`() {
        assertEquals(15, BubbleStyleConfig().fontSize)
    }

    @Test
    fun `default dynamicColor is false`() {
        assertEquals(false, BubbleStyleConfig().dynamicColor)
    }

    // ==================== Data class contract ====================

    @Test
    fun `equal configs are equal`() {
        val a = BubbleStyleConfig(userBubbleColor = 0xFF0000, cornerRadius = 12)
        val b = BubbleStyleConfig(userBubbleColor = 0xFF0000, cornerRadius = 12)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different configs are not equal`() {
        val a = BubbleStyleConfig(userBubbleColor = 0xFF0000)
        val b = BubbleStyleConfig(userBubbleColor = 0x00FF00)
        assertNotEquals(a, b)
    }

    @Test
    fun `copy modifies only specified fields`() {
        val original = BubbleStyleConfig()
        val copied = original.copy(fontSize = 20)
        assertEquals(20, copied.fontSize)
        assertEquals(original.userBubbleColor, copied.userBubbleColor)
        assertEquals(original.assistantBubbleColor, copied.assistantBubbleColor)
        assertEquals(original.cornerRadius, copied.cornerRadius)
        assertEquals(original.dynamicColor, copied.dynamicColor)
    }
}
