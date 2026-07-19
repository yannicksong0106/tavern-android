package com.tavern.lite.data.importexport

import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LorebookExporterTest {

    private lateinit var exporter: LorebookExporter
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Before
    fun setup() {
        exporter = LorebookExporter(json)
    }

    @Test
    fun `exportToJson produces valid JSON with entries`() {
        val worldBook = WorldBookEntity(id = 1, name = "Fantasy World")
        val entries = listOf(
            WorldBookEntryEntity(
                worldBookId = 1, uid = 0, comment = "Dragon info",
                keys = """["dragon","wyrm"]""",
                keysSecondary ="""["fire","breath"]""",
                content = "Dragons are ancient creatures.",
                constant = false, selective = true, selectiveLogic = 0
            )
        )

        val result = exporter.exportToJson(worldBook, entries)
        assertTrue(result.contains("dragon"))
        assertTrue(result.contains("Dragons are ancient creatures."))
        assertTrue(result.contains("Fantasy World") || result.contains("entries"))
    }

    @Test
    fun `exportToJson handles empty entries`() {
        val worldBook = WorldBookEntity(id = 1, name = "Empty")
        val result = exporter.exportToJson(worldBook, emptyList())
        assertTrue(result.contains("entries"))
        assertTrue(result.contains("{}") || result.contains(":{}"))
    }

    @Test
    fun `exportToJson handles invalid JSON keys gracefully`() {
        val worldBook = WorldBookEntity(id = 1, name = "Test")
        val entries = listOf(
            WorldBookEntryEntity(
                worldBookId = 1, uid = 0,
                keys = "not valid json",
                keysSecondary = "also invalid",
                content = "Some content"
            )
        )
        val result = exporter.exportToJson(worldBook, entries)
        assertTrue(result.contains("Some content"))
        // Keys should fallback to empty list
        assertTrue(result.contains("\"key\":[]"))
    }

    @Test
    fun `importFromJson parses valid SillyTavern format`() {
        val jsonStr = """
        {
            "entries": {
                "0": {
                    "uid": 0,
                    "key": ["dragon", "wyrm"],
                    "keysecondary": ["fire"],
                    "content": "Dragons breathe fire.",
                    "comment": "Dragon lore",
                    "constant": true,
                    "selective": false,
                    "selectiveLogic": 0,
                    "order": 100,
                    "position": 0,
                    "disable": false,
                    "probability": 100,
                    "depth": 4,
                    "group": "",
                    "groupOverride": false,
                    "groupWeight": 100
                }
            }
        }
        """.trimIndent()

        val entries = exporter.importFromJson(jsonStr, worldBookId = 5)
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        val entry = entries[0]
        assertEquals(5L, entry.worldBookId)
        assertEquals("Dragon lore", entry.comment)
        assertEquals("Dragons breathe fire.", entry.content)
        assertEquals(true, entry.constant)
        assertTrue(entry.keys.contains("dragon"))
        assertTrue(entry.keys.contains("wyrm"))
    }

    @Test
    fun `importFromJson handles empty entries map as success empty list`() {
        val jsonStr = """{"entries": {}}"""
        val entries = exporter.importFromJson(jsonStr, 1)
        assertNotNull(entries)
        assertTrue(entries!!.isEmpty())
    }

    @Test
    fun `importFromJson returns null for malformed json`() {
        val entries = exporter.importFromJson("""{"entries":""", worldBookId = 1)
        assertNull(entries)
    }

    @Test
    fun `importFromJson returns null for deeply nested json instead of crashing`() {
        // StackOverflowError is Error, not Exception; importer must catch both.
        val depth = 100_000
        val payload = "{\"entries\":{\"0\":{\"key\":[],\"content\":\"x\",\"extensions\":" +
            "[".repeat(depth) + "]".repeat(depth) + "}}}"
        val entries = exporter.importFromJson(payload, worldBookId = 1)
        assertNull(entries)
    }

    @Test
    fun `roundtrip export then import preserves data`() {
        val originalEntries = listOf(
            WorldBookEntryEntity(
                worldBookId = 1, uid = 0, comment = "Test entry",
                keys = """["alpha","beta"]""",
                keysSecondary ="""["gamma"]""",
                content = "Test content here",
                constant = true, selective = true, selectiveLogic = 1,
                orderVal = 50, position = 1, probability = 80, depth = 3,
                disabled = false, excludeRecursion = true, preventRecursion = false,
                group = "test_group", groupOverride = true, groupWeight = 75
            )
        )
        val worldBook = WorldBookEntity(id = 1, name = "Roundtrip Test")

        val exported = exporter.exportToJson(worldBook, originalEntries)
        val imported = exporter.importFromJson(exported, worldBookId = 1)

        assertNotNull(imported)
        assertEquals(1, imported!!.size)
        val entry = imported[0]
        assertEquals(originalEntries[0].comment, entry.comment)
        assertEquals(originalEntries[0].content, entry.content)
        assertEquals(originalEntries[0].constant, entry.constant)
        assertEquals(originalEntries[0].selective, entry.selective)
        assertEquals(originalEntries[0].selectiveLogic, entry.selectiveLogic)
        assertEquals(originalEntries[0].orderVal, entry.orderVal)
        assertEquals(originalEntries[0].position, entry.position)
        assertEquals(originalEntries[0].probability, entry.probability)
        assertEquals(originalEntries[0].depth, entry.depth)
        assertEquals(originalEntries[0].excludeRecursion, entry.excludeRecursion)
        assertEquals(originalEntries[0].group, entry.group)
        assertEquals(originalEntries[0].groupOverride, entry.groupOverride)
        assertEquals(originalEntries[0].groupWeight, entry.groupWeight)
    }

    @Test
    fun `roundtrip with multiple entries preserves order`() {
        val entries = listOf(
            WorldBookEntryEntity(worldBookId = 1, uid = 0, keys = """["first"]""", content = "First entry"),
            WorldBookEntryEntity(worldBookId = 1, uid = 1, keys = """["second"]""", content = "Second entry"),
            WorldBookEntryEntity(worldBookId = 1, uid = 2, keys = """["third"]""", content = "Third entry")
        )
        val worldBook = WorldBookEntity(id = 1, name = "Multi")

        val exported = exporter.exportToJson(worldBook, entries)
        val imported = exporter.importFromJson(exported, 1)

        assertNotNull(imported)
        assertEquals(3, imported!!.size)
        assertEquals("First entry", imported[0].content)
        assertEquals("Second entry", imported[1].content)
        assertEquals("Third entry", imported[2].content)
    }

    @Test
    fun `exportToJson preserves all selective logic values`() {
        val entries = listOf(
            WorldBookEntryEntity(worldBookId = 1, uid = 0, keys = """["a"]""", selectiveLogic = 0, selective = true),
            WorldBookEntryEntity(worldBookId = 1, uid = 1, keys = """["b"]""", selectiveLogic = 1, selective = true),
            WorldBookEntryEntity(worldBookId = 1, uid = 2, keys = """["c"]""", selectiveLogic = 2, selective = true)
        )
        val worldBook = WorldBookEntity(id = 1, name = "Logic Test")

        val exported = exporter.exportToJson(worldBook, entries)
        val imported = exporter.importFromJson(exported, 1)

        assertNotNull(imported)
        assertEquals(0, imported!![0].selectiveLogic)
        assertEquals(1, imported[1].selectiveLogic)
        assertEquals(2, imported[2].selectiveLogic)
    }
}
