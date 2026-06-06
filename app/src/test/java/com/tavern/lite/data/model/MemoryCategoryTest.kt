package com.tavern.lite.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCategoryTest {

    // ==================== fromKey ====================

    @Test
    fun `fromKey returns FACT for fact key`() {
        assertEquals(MemoryCategory.FACT, MemoryCategory.fromKey("fact"))
    }

    @Test
    fun `fromKey returns EMOTION for emotion key`() {
        assertEquals(MemoryCategory.EMOTION, MemoryCategory.fromKey("emotion"))
    }

    @Test
    fun `fromKey returns PREFERENCE for preference key`() {
        assertEquals(MemoryCategory.PREFERENCE, MemoryCategory.fromKey("preference"))
    }

    @Test
    fun `fromKey returns EVENT for event key`() {
        assertEquals(MemoryCategory.EVENT, MemoryCategory.fromKey("event"))
    }

    @Test
    fun `fromKey returns HABIT for habit key`() {
        assertEquals(MemoryCategory.HABIT, MemoryCategory.fromKey("habit"))
    }

    @Test
    fun `fromKey returns CHARACTER_CONSISTENCY for character_consistency key`() {
        assertEquals(MemoryCategory.CHARACTER_CONSISTENCY, MemoryCategory.fromKey("character_consistency"))
    }

    @Test
    fun `fromKey returns TEMPORARY for temporary key`() {
        assertEquals(MemoryCategory.TEMPORARY, MemoryCategory.fromKey("temporary"))
    }

    @Test
    fun `fromKey defaults to FACT for unknown key`() {
        assertEquals(MemoryCategory.FACT, MemoryCategory.fromKey("unknown"))
    }

    @Test
    fun `fromKey defaults to FACT for empty key`() {
        assertEquals(MemoryCategory.FACT, MemoryCategory.fromKey(""))
    }

    // ==================== migrateLegacy ====================

    @Test
    fun `migrateLegacy maps user_info to fact`() {
        assertEquals("fact", MemoryCategory.migrateLegacy("user_info"))
    }

    @Test
    fun `migrateLegacy maps relationship to fact`() {
        assertEquals("fact", MemoryCategory.migrateLegacy("relationship"))
    }

    @Test
    fun `migrateLegacy maps commitment to event`() {
        assertEquals("event", MemoryCategory.migrateLegacy("commitment"))
    }

    @Test
    fun `migrateLegacy passes through unknown keys`() {
        assertEquals("preference", MemoryCategory.migrateLegacy("preference"))
        assertEquals("habit", MemoryCategory.migrateLegacy("habit"))
        assertEquals("unknown", MemoryCategory.migrateLegacy("unknown"))
    }

    // ==================== coreCategories / temporaryCategories ====================

    @Test
    fun `coreCategories contains all core entries`() {
        val core = MemoryCategory.coreCategories
        assertTrue(core.contains(MemoryCategory.FACT))
        assertTrue(core.contains(MemoryCategory.EMOTION))
        assertTrue(core.contains(MemoryCategory.PREFERENCE))
        assertTrue(core.contains(MemoryCategory.EVENT))
        assertTrue(core.contains(MemoryCategory.HABIT))
        assertTrue(core.contains(MemoryCategory.CHARACTER_CONSISTENCY))
        assertEquals(6, core.size)
    }

    @Test
    fun `temporaryCategories contains only TEMPORARY`() {
        val temp = MemoryCategory.temporaryCategories
        assertTrue(temp.contains(MemoryCategory.TEMPORARY))
        assertFalse(temp.contains(MemoryCategory.FACT))
        assertEquals(1, temp.size)
    }

    @Test
    fun `all entries have non-blank key and label`() {
        for (cat in MemoryCategory.entries) {
            assertTrue("key blank for $cat", cat.key.isNotBlank())
            assertTrue("label blank for $cat", cat.label.isNotBlank())
        }
    }
}
