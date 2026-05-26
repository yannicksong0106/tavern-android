package com.tavern.lite.worker

import com.tavern.lite.data.db.entity.CharacterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for BackgroundProactiveWorker companion logic.
 * doWork() 依赖 Hilt @AssistedInject，需 instrumented test。
 * 这里测试纯函数 selectByChattiness。
 */
class BackgroundProactiveWorkerTest {

    // ==================== selectByChattiness ====================

    @Test
    fun `selectByChattiness returns null for empty list`() {
        val result = BackgroundProactiveWorker.selectByChattiness(emptyList())
        assertNull(result)
    }

    @Test
    fun `selectByChattiness returns single character`() {
        val chars = listOf(CharacterEntity(id = 1, name = "Alice", chattiness = 50))
        val result = BackgroundProactiveWorker.selectByChattiness(chars)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `selectByChattiness always selects high chattiness character with deterministic random`() {
        val high = CharacterEntity(id = 1, name = "Chatty", chattiness = 100)
        val low = CharacterEntity(id = 2, name = "Quiet", chattiness = 1)
        val chars = listOf(high, low)

        // With a fixed seed random that returns 0.0, the first character is always selected
        val fixedRandom = kotlin.random.Random(42)
        // Run multiple times to check distribution
        val results = (1..100).map {
            BackgroundProactiveWorker.selectByChattiness(chars, fixedRandom)
        }
        // Both characters should be selected (weighted), but high chattiness more often
        val highCount = results.count { it?.id == 1L }
        val lowCount = results.count { it?.id == 2L }
        assert(highCount > lowCount) { "High chattiness ($highCount) should be selected more than low ($lowCount)" }
    }

    @Test
    fun `selectByChattiness handles all zero chattiness`() {
        val chars = listOf(
            CharacterEntity(id = 1, name = "A", chattiness = 0),
            CharacterEntity(id = 2, name = "B", chattiness = 0)
        )
        // With totalWeight=0, falls back to random()
        val result = BackgroundProactiveWorker.selectByChattiness(chars)
        assertNotNull(result)
    }

    @Test
    fun `selectByChattiness coerces negative chattiness to zero`() {
        val chars = listOf(
            CharacterEntity(id = 1, name = "Negative", chattiness = -10),
            CharacterEntity(id = 2, name = "Normal", chattiness = 50)
        )
        // Negative chattiness is coerced to 0, so only character 2 has weight
        val fixedRandom = kotlin.random.Random(42)
        val results = (1..20).map {
            BackgroundProactiveWorker.selectByChattiness(chars, fixedRandom)
        }
        // Character 2 should always be selected (weight 50 vs 0)
        assert(results.all { it?.id == 2L }) { "Character with negative chattiness should never be selected" }
    }

    @Test
    fun `selectByChattiness coerces chattiness over 100 to 100`() {
        val chars = listOf(
            CharacterEntity(id = 1, name = "Over100", chattiness = 200),
            CharacterEntity(id = 2, name = "Normal", chattiness = 50)
        )
        val fixedRandom = kotlin.random.Random(42)
        val results = (1..100).map {
            BackgroundProactiveWorker.selectByChattiness(chars, fixedRandom)
        }
        // Character 1 (weight 100) should be selected about 2x more than character 2 (weight 50)
        val char1Count = results.count { it?.id == 1L }
        val char2Count = results.count { it?.id == 2L }
        assert(char1Count > char2Count) { "Higher chattiness ($char1Count) should be selected more ($char2Count)" }
    }

    @Test
    fun `selectByChattiness distributes proportionally`() {
        val chars = listOf(
            CharacterEntity(id = 1, name = "High", chattiness = 75),
            CharacterEntity(id = 2, name = "Mid", chattiness = 50),
            CharacterEntity(id = 3, name = "Low", chattiness = 25)
        )
        val fixedRandom = kotlin.random.Random(123)
        val results = (1..300).map {
            BackgroundProactiveWorker.selectByChattiness(chars, fixedRandom)
        }
        val count1 = results.count { it?.id == 1L }
        val count2 = results.count { it?.id == 2L }
        val count3 = results.count { it?.id == 3L }
        // Proportional: 75:50:25 = 3:2:1
        assert(count1 > count2) { "Highest chattiness should be selected most: $count1 vs $count2" }
        assert(count2 > count3) { "Mid chattiness should be selected more than low: $count2 vs $count3" }
    }

    @Test
    fun `selectByChattiness handles single character with zero chattiness`() {
        val chars = listOf(CharacterEntity(id = 1, name = "Zero", chattiness = 0))
        // totalWeight=0, falls back to random() — should still return the character
        val result = BackgroundProactiveWorker.selectByChattiness(chars)
        assertEquals(1L, result?.id)
    }
}
