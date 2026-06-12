package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.GroupSchedulingStrategy
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRespondingCharacterSelectorTest {

    private val alice = CharacterEntity(id = 1L, name = "Alice")
    private val bob = CharacterEntity(id = 2L, name = "Bob")
    private val clara = CharacterEntity(id = 3L, name = "Clara")

    @Test
    fun `list order returns all characters in original order`() {
        val selector = GroupRespondingCharacterSelector(Random(1))
        val characters = listOf(alice, bob, clara)

        val selected = selector.select(
            characters = characters,
            schedulingStrategy = GroupSchedulingStrategy.LIST_ORDER,
            chattinessByCharacterId = emptyMap()
        )

        assertEquals(characters, selected)
    }

    @Test
    fun `round robin rotates one character at a time`() {
        val selector = GroupRespondingCharacterSelector(Random(1))
        val characters = listOf(alice, bob, clara)

        val first = selector.select(characters, GroupSchedulingStrategy.ROUND_ROBIN, emptyMap())
        val second = selector.select(characters, GroupSchedulingStrategy.ROUND_ROBIN, emptyMap())
        val third = selector.select(characters, GroupSchedulingStrategy.ROUND_ROBIN, emptyMap())
        val fourth = selector.select(characters, GroupSchedulingStrategy.ROUND_ROBIN, emptyMap())

        assertEquals(listOf(alice), first)
        assertEquals(listOf(bob), second)
        assertEquals(listOf(clara), third)
        assertEquals(listOf(alice), fourth)
    }

    @Test
    fun `natural strategy always returns at least one character`() {
        val selector = GroupRespondingCharacterSelector(Random(4))

        val selected = selector.select(
            characters = listOf(alice, bob),
            schedulingStrategy = GroupSchedulingStrategy.NATURAL,
            chattinessByCharacterId = mapOf(alice.id to 0, bob.id to 0)
        )

        assertTrue(selected.isNotEmpty())
        assertTrue(selected.all { it in listOf(alice, bob) })
    }

    @Test
    fun `empty characters returns empty selection`() {
        val selector = GroupRespondingCharacterSelector(Random(1))

        val selected = selector.select(
            characters = emptyList(),
            schedulingStrategy = GroupSchedulingStrategy.ROUND_ROBIN,
            chattinessByCharacterId = emptyMap()
        )

        assertEquals(emptyList<CharacterEntity>(), selected)
    }
}
