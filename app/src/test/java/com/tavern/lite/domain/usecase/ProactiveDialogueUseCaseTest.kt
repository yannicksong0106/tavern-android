package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.CharacterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProactiveDialogueUseCaseTest {

    private lateinit var useCase: ProactiveDialogueUseCase

    @Before
    fun setup() {
        useCase = ProactiveDialogueUseCase()
    }

    @Test
    fun `shouldScheduleProactive returns null when chattiness is 0`() {
        assertNull(useCase.shouldScheduleProactive(0))
    }

    @Test
    fun `shouldScheduleProactive returns null when chattiness is negative`() {
        assertNull(useCase.shouldScheduleProactive(-1))
    }

    @Test
    fun `shouldScheduleProactive returns delay when chattiness is 100`() {
        // With chattiness=100, probability is 100%, so should always return a delay
        val delay = useCase.shouldScheduleProactive(100)
        assertNotNull(delay)
        assertTrue(delay!! >= 2000L)
        assertTrue(delay <= 4000L)
    }

    @Test
    fun `shouldScheduleGroupProactive returns null for empty characters`() {
        assertNull(useCase.shouldScheduleGroupProactive(emptyList()))
    }

    @Test
    fun `shouldScheduleGroupProactive returns null when all characters have 0 chattiness`() {
        val characters = listOf(
            makeCharacter(1, "Alice", chattiness = 0),
            makeCharacter(2, "Bob", chattiness = 0)
        )
        assertNull(useCase.shouldScheduleGroupProactive(characters))
    }

    @Test
    fun `shouldScheduleGroupProactive returns valid delay or null when max chattiness is 100`() {
        val characters = listOf(
            makeCharacter(1, "Alice", chattiness = 100),
            makeCharacter(2, "Bob", chattiness = 50)
        )
        // Probability is 50%, so may return null. Run multiple times to verify range.
        var triggered = 0
        repeat(100) {
            val delay = useCase.shouldScheduleGroupProactive(characters)
            if (delay != null) {
                triggered++
                assertTrue(delay >= 1000L)
                assertTrue(delay <= 3000L)
            }
        }
        // With 50% probability over 100 runs, should trigger roughly 50 times
        assertTrue("Expected at least 10 triggers out of 100, got $triggered", triggered >= 10)
    }

    @Test
    fun `selectNextProactiveCharacter returns null for empty list`() {
        assertNull(useCase.selectNextProactiveCharacter(emptyList()))
    }

    @Test
    fun `selectNextProactiveCharacter returns single character`() {
        val char = makeCharacter(1, "Alice", chattiness = 50)
        val result = useCase.selectNextProactiveCharacter(listOf(char))
        assertEquals(char, result)
    }

    @Test
    fun `selectNextProactiveCharacter respects cooldown`() {
        val char1 = makeCharacter(1, "Alice", chattiness = 50)
        val char2 = makeCharacter(2, "Bob", chattiness = 50)
        val characters = listOf(char1, char2)

        // First call should select one
        val first = useCase.selectNextProactiveCharacter(characters)
        assertNotNull(first)

        // Second call immediately should select the other (due to cooldown)
        val second = useCase.selectNextProactiveCharacter(characters)
        assertNotNull(second)
        assertEquals(first!!.id == char1.id, second!!.id == char2.id)
    }

    @Test
    fun `parseAtMention returns null for non-mention content`() {
        val characters = listOf(makeCharacter(1, "Alice"))
        assertNull(useCase.parseAtMention("Hello world", characters))
    }

    @Test
    fun `parseAtMention extracts mentioned character`() {
        val alice = makeCharacter(1, "Alice")
        val bob = makeCharacter(2, "Bob")
        val characters = listOf(alice, bob)

        val result = useCase.parseAtMention("@Alice tell me a joke", characters)
        assertNotNull(result)
        assertEquals(alice, result!!.first)
        assertEquals("tell me a joke", result.second)
    }

    @Test
    fun `parseAtMention is case insensitive`() {
        val alice = makeCharacter(1, "Alice")
        val result = useCase.parseAtMention("@alice hi", listOf(alice))
        assertNotNull(result)
        assertEquals(alice, result!!.first)
    }

    @Test
    fun `parseAtMention returns null for unknown character`() {
        val result = useCase.parseAtMention("@Unknown hi", listOf(makeCharacter(1, "Alice")))
        assertNull(result)
    }

    private fun makeCharacter(id: Long, name: String, chattiness: Int = 50) = CharacterEntity(
        id = id,
        name = name,
        chattiness = chattiness,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
