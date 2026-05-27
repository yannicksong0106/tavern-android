package com.tavern.lite.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActiveTrackerTest {

    @After
    fun cleanup() {
        // Clear state between tests since ChatActiveTracker is a singleton
        ChatActiveTracker.clearActive(1L)
        ChatActiveTracker.clearActive(2L)
        ChatActiveTracker.clearActive(999L)
    }

    @Test
    fun `isActive returns false for unknown chat`() {
        assertFalse(ChatActiveTracker.isActive(999L))
    }

    @Test
    fun `setActive makes chat active`() {
        ChatActiveTracker.setActive(1L)
        assertTrue(ChatActiveTracker.isActive(1L))
    }

    @Test
    fun `clearActive makes chat inactive`() {
        ChatActiveTracker.setActive(1L)
        assertTrue(ChatActiveTracker.isActive(1L))

        ChatActiveTracker.clearActive(1L)
        assertFalse(ChatActiveTracker.isActive(1L))
    }

    @Test
    fun `multiple chats can be active simultaneously`() {
        ChatActiveTracker.setActive(1L)
        ChatActiveTracker.setActive(2L)

        assertTrue(ChatActiveTracker.isActive(1L))
        assertTrue(ChatActiveTracker.isActive(2L))
    }

    @Test
    fun `clearing one chat does not affect another`() {
        ChatActiveTracker.setActive(1L)
        ChatActiveTracker.setActive(2L)

        ChatActiveTracker.clearActive(1L)

        assertFalse(ChatActiveTracker.isActive(1L))
        assertTrue(ChatActiveTracker.isActive(2L))
    }

    @Test
    fun `setActive is idempotent`() {
        ChatActiveTracker.setActive(1L)
        ChatActiveTracker.setActive(1L)

        assertTrue(ChatActiveTracker.isActive(1L))
    }

    @Test
    fun `clearActive on inactive chat does not throw`() {
        ChatActiveTracker.clearActive(999L)
        assertFalse(ChatActiveTracker.isActive(999L))
    }
}
