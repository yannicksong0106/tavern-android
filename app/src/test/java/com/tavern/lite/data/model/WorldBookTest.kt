package com.tavern.lite.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `WorldBookEntryData has correct defaults`() {
        val entry = WorldBookEntryData()
        assertEquals(0, entry.uid)
        assertEquals(emptyList<String>(), entry.key)
        assertEquals(emptyList<String>(), entry.keysecondary)
        assertEquals("", entry.content)
        assertEquals("", entry.comment)
        assertFalse(entry.constant)
        assertFalse(entry.vectorized)
        assertFalse(entry.selective)
        assertEquals(0, entry.selectiveLogic)
        assertTrue(entry.addMemo)
        assertEquals(100, entry.order)
        assertEquals(0, entry.position)
        assertFalse(entry.disable)
        assertFalse(entry.excludeRecursion)
        assertFalse(entry.preventRecursion)
        assertFalse(entry.delayUntilRecursion)
        assertEquals(100, entry.probability)
        assertTrue(entry.useProbability)
        assertEquals(4, entry.depth)
        assertEquals("", entry.group)
        assertFalse(entry.groupOverride)
        assertEquals(100, entry.groupWeight)
        assertTrue(entry.extensions.isEmpty())
    }

    @Test
    fun `WorldBook serializes and deserializes`() {
        val book = WorldBook(
            entries = mapOf(
                "0" to WorldBookEntryData(
                    uid = 0,
                    key = listOf("hello", "hi"),
                    content = "Greetings response",
                    constant = true,
                    order = 50
                ),
                "1" to WorldBookEntryData(
                    uid = 1,
                    key = listOf("weather"),
                    content = "Weather info",
                    selective = true,
                    keysecondary = listOf("sunny", "rainy")
                )
            )
        )

        val jsonStr = json.encodeToString(WorldBook.serializer(), book)
        val decoded = json.decodeFromString(WorldBook.serializer(), jsonStr)

        assertEquals(2, decoded.entries.size)
        assertEquals(listOf("hello", "hi"), decoded.entries["0"]?.key)
        assertEquals("Greetings response", decoded.entries["0"]?.content)
        assertTrue(decoded.entries["0"]?.constant == true)
        assertEquals(50, decoded.entries["0"]?.order)
        assertTrue(decoded.entries["1"]?.selective == true)
        assertEquals(listOf("sunny", "rainy"), decoded.entries["1"]?.keysecondary)
    }

    @Test
    fun `WorldBook handles empty entries`() {
        val book = WorldBook()
        assertTrue(book.entries.isEmpty())

        val jsonStr = json.encodeToString(WorldBook.serializer(), book)
        val decoded = json.decodeFromString(WorldBook.serializer(), jsonStr)
        assertTrue(decoded.entries.isEmpty())
    }

    @Test
    fun `WorldBookEntryData selective logic values`() {
        // selectiveLogic: 0 = AND, 1 = OR, 2 = NOT
        val entryAnd = WorldBookEntryData(selectiveLogic = 0)
        val entryOr = WorldBookEntryData(selectiveLogic = 1)
        val entryNot = WorldBookEntryData(selectiveLogic = 2)

        assertEquals(0, entryAnd.selectiveLogic)
        assertEquals(1, entryOr.selectiveLogic)
        assertEquals(2, entryNot.selectiveLogic)
    }

    @Test
    fun `WorldBookEntryData group fields`() {
        val entry = WorldBookEntryData(
            group = "combat",
            groupOverride = true,
            groupWeight = 80
        )

        assertEquals("combat", entry.group)
        assertTrue(entry.groupOverride)
        assertEquals(80, entry.groupWeight)
    }

    @Test
    fun `WorldBookEntryData recursion flags`() {
        val entry = WorldBookEntryData(
            excludeRecursion = true,
            preventRecursion = true,
            delayUntilRecursion = true
        )

        assertTrue(entry.excludeRecursion)
        assertTrue(entry.preventRecursion)
        assertTrue(entry.delayUntilRecursion)
    }

    @Test
    fun `WorldBookEntryData probability fields`() {
        val entry = WorldBookEntryData(
            probability = 50,
            useProbability = false
        )

        assertEquals(50, entry.probability)
        assertFalse(entry.useProbability)
    }
}
