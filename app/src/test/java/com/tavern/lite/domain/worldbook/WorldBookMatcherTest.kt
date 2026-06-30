package com.tavern.lite.domain.worldbook

import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookMatcherTest {

    private val matcher = WorldBookMatcher()

    private fun entry(
        id: Long = 1,
        constant: Boolean = false,
        selective: Boolean = false,
        selectiveLogic: Int = 0,
        probability: Int = 100,
        excludeRecursion: Boolean = false,
        preventRecursion: Boolean = false,
        depth: Int = 4,
        disabled: Boolean = false,
        content: String = "content $id"
    ) = WorldBookEntryEntity(
        id = id, worldBookId = 1, uid = 0, comment = "entry $id",
        keys = "[]", keysSecondary = "[]", content = content,
        constant = constant, position = 0, orderVal = 100,
        probability = probability, depth = depth, disabled = disabled,
        selective = selective, selectiveLogic = selectiveLogic,
        excludeRecursion = excludeRecursion, preventRecursion = preventRecursion,
        group = "", groupOverride = false, groupWeight = 100
    )

    private fun keysFor(primary: List<String>, secondary: List<String> = emptyList()): (WorldBookEntryEntity) -> Pair<List<String>, List<String>> =
        { _ -> primary to secondary }

    // ==================== matchEntries (non-recursive) ====================

    @Test
    fun `matchEntries returns constant entries regardless of text`() {
        val e = entry(id = 1, constant = true)
        val result = matcher.matchEntries(listOf(e), "unrelated text", keysFor(listOf("key")))
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
    }

    @Test
    fun `matchEntries matches primary key`() {
        val e = entry(id = 1)
        val result = matcher.matchEntries(listOf(e), "hello world", keysFor(listOf("hello")))
        assertEquals(1, result.size)
    }

    @Test
    fun `matchEntries does not match when key absent`() {
        val e = entry(id = 1)
        val result = matcher.matchEntries(listOf(e), "hello world", keysFor(listOf("absent")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchEntries empty keys returns nothing`() {
        val e = entry(id = 1)
        val result = matcher.matchEntries(listOf(e), "hello", keysFor(emptyList(), emptyList()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchEntries selective AND requires both primary and secondary`() {
        val e = entry(id = 1, selective = true, selectiveLogic = 0)
        val keys = keysFor(listOf("hello"), listOf("world"))
        assertTrue(matcher.matchEntries(listOf(e), "hello world", keys).isNotEmpty())
        assertTrue(matcher.matchEntries(listOf(e), "hello only", keys).isEmpty())
        assertTrue(matcher.matchEntries(listOf(e), "world only", keys).isEmpty())
    }

    @Test
    fun `matchEntries selective OR matches either primary or secondary`() {
        val e = entry(id = 1, selective = true, selectiveLogic = 1)
        val keys = keysFor(listOf("hello"), listOf("world"))
        assertTrue(matcher.matchEntries(listOf(e), "hello only", keys).isNotEmpty())
        assertTrue(matcher.matchEntries(listOf(e), "world only", keys).isNotEmpty())
        assertTrue(matcher.matchEntries(listOf(e), "absent", keys).isEmpty())
    }

    @Test
    fun `matchEntries selective NOT excludes when secondary present`() {
        val e = entry(id = 1, selective = true, selectiveLogic = 2)
        val keys = keysFor(listOf("hello"), listOf("world"))
        assertTrue(matcher.matchEntries(listOf(e), "hello only", keys).isNotEmpty())
        assertTrue(matcher.matchEntries(listOf(e), "hello world", keys).isEmpty())
    }

    @Test
    fun `matchEntries selective with unknown logic falls back to primary only`() {
        val e = entry(id = 1, selective = true, selectiveLogic = 99)
        val keys = keysFor(listOf("hello"), listOf("world"))
        assertTrue(matcher.matchEntries(listOf(e), "hello only", keys).isNotEmpty())
        assertTrue(matcher.matchEntries(listOf(e), "world only", keys).isEmpty())
    }

    // ==================== matchEntriesWithTrace ====================

    @Test
    fun `matchEntriesWithTrace returns CONSTANT trace for constant entries`() {
        val e = entry(id = 1, constant = true)
        val result = matcher.matchEntriesWithTrace(listOf(e), "text", keysFor(listOf("key")))
        assertEquals(1, result.entries.size)
        assertEquals(1, result.traces.size)
        assertEquals(WorldBookMatchTrace.MatchType.CONSTANT, result.traces[0].matchType)
    }

    @Test
    fun `matchEntriesWithTrace returns PRIMARY_KEY trace for keyword match`() {
        val e = entry(id = 1)
        val result = matcher.matchEntriesWithTrace(listOf(e), "hello", keysFor(listOf("hello")))
        assertEquals(WorldBookMatchTrace.MatchType.PRIMARY_KEY, result.traces[0].matchType)
        assertEquals(listOf("hello"), result.traces[0].matchedKeywords)
    }

    @Test
    fun `matchEntriesWithTrace returns SELECTIVE_AND trace`() {
        val e = entry(id = 1, selective = true, selectiveLogic = 0)
        val result = matcher.matchEntriesWithTrace(listOf(e), "hello world", keysFor(listOf("hello"), listOf("world")))
        assertEquals(WorldBookMatchTrace.MatchType.SELECTIVE_AND, result.traces[0].matchType)
    }

    @Test
    fun `matchEntriesWithTrace returns SELECTIVE_OR trace`() {
        val e = entry(id = 1, selective = true, selectiveLogic = 1)
        val result = matcher.matchEntriesWithTrace(listOf(e), "hello", keysFor(listOf("hello"), listOf("world")))
        assertEquals(WorldBookMatchTrace.MatchType.SELECTIVE_OR, result.traces[0].matchType)
    }

    @Test
    fun `matchEntriesWithTrace returns SELECTIVE_NOT trace`() {
        val e = entry(id = 1, selective = true, selectiveLogic = 2)
        val result = matcher.matchEntriesWithTrace(listOf(e), "hello", keysFor(listOf("hello"), listOf("world")))
        assertEquals(WorldBookMatchTrace.MatchType.SELECTIVE_NOT, result.traces[0].matchType)
    }

    // ==================== matchEntriesRecursive ====================

    @Test
    fun `matchEntriesRecursive matches constant and keyword entries in first round`() {
        val constant = entry(id = 1, constant = true, content = "constant content")
        val keyword = entry(id = 2, content = "keyword content")
        val entries = listOf(constant, keyword)
        val result = matcher.matchEntriesRecursive(entries, "trigger", keysForEntry = keysFor(listOf("trigger")))
        assertEquals(2, result.size)
    }

    @Test
    fun `matchEntriesRecursive recurses into matched content`() {
        val e1 = entry(id = 1, content = "dragon appears")
        val e2 = entry(id = 2, content = "dragon lair found", depth = 1)
        val entries = listOf(e1, e2)
        val result = matcher.matchEntriesRecursive(entries, "dragon", keysForEntry = keysFor(listOf("dragon")))
        assertTrue(result.any { it.id == 1L })
    }

    @Test
    fun `matchEntriesRecursive skips excludeRecursion entries in recursion`() {
        val e1 = entry(id = 1, content = "trigger word")
        val e2 = entry(id = 2, excludeRecursion = true, content = "result")
        val entries = listOf(e1, e2)
        val result = matcher.matchEntriesRecursive(entries, "trigger", keysForEntry = keysFor(listOf("trigger"), listOf("result")))
        assertTrue(result.any { it.id == 1L })
    }

    @Test
    fun `matchEntriesRecursive skips preventRecursion entries in recursion`() {
        val e1 = entry(id = 1, content = "trigger word")
        val e2 = entry(id = 2, preventRecursion = true, content = "result")
        val entries = listOf(e1, e2)
        val result = matcher.matchEntriesRecursive(entries, "trigger", keysForEntry = keysFor(listOf("trigger"), listOf("result")))
        assertTrue(result.any { it.id == 1L })
    }

    @Test
    fun `matchEntriesRecursive respects maxDepth`() {
        val e1 = entry(id = 1, content = "level1 trigger")
        val e2 = entry(id = 2, content = "level2 trigger", depth = 4)
        val entries = listOf(e1, e2)
        val result = matcher.matchEntriesRecursive(entries, "level1", maxDepth = 1, keysForEntry = keysFor(listOf("level1", "level2")))
        assertTrue(result.any { it.id == 1L })
    }

    @Test
    fun `matchEntriesRecursive probability filter passes when random below threshold`() {
        val e = entry(id = 1, probability = 50)
        val result = matcher.matchEntriesRecursive(listOf(e), "trigger", keysForEntry = keysFor(listOf("trigger")), randomPercent = { 30.0 })
        assertEquals(1, result.size)
    }

    @Test
    fun `matchEntriesRecursive probability filter fails when random above threshold`() {
        val e = entry(id = 1, probability = 50)
        val result = matcher.matchEntriesRecursive(listOf(e), "trigger", keysForEntry = keysFor(listOf("trigger")), randomPercent = { 80.0 })
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchEntriesRecursive probability 100 always passes`() {
        val e = entry(id = 1, probability = 100)
        val result = matcher.matchEntriesRecursive(listOf(e), "trigger", keysForEntry = keysFor(listOf("trigger")), randomPercent = { 99.9 })
        assertEquals(1, result.size)
    }

    @Test
    fun `matchEntriesRecursiveWithTrace returns RECURSIVE trace for recursion matches`() {
        val e1 = entry(id = 1, content = "trigger word")
        val e2 = entry(id = 2, content = "found trigger", depth = 1)
        val result = matcher.matchEntriesRecursiveWithTrace(listOf(e1, e2), "trigger", keysForEntry = keysFor(listOf("trigger")))
        val keywordTrace = result.traces.find { it.matchType == WorldBookMatchTrace.MatchType.PRIMARY_KEY }
        assertTrue(keywordTrace != null)
    }

    // ==================== matchesEntry edge cases ====================

    @Test
    fun `matchesEntry returns false when both primary and secondary empty`() {
        val e = entry(id = 1)
        assertFalse(matcher.matchesEntry(e, "text", emptyList<String>() to emptyList<String>()))
    }

    @Test
    fun `matchesEntry non-selective with secondary keys uses primary only`() {
        val e = entry(id = 1, selective = false)
        val keys = listOf("hello") to listOf("world")
        assertTrue(matcher.matchesEntry(e, "hello", keys))
        assertFalse(matcher.matchesEntry(e, "world", keys))
    }
}
