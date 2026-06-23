package com.tavern.lite.network

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSectionBuilderTest {

    @Test
    fun `buildDynamicContextSections returns sections with world book`() {
        val character = createTestCharacter()
        val worldBookEntries = listOf(
            createTestWorldBookEntry("World Info", "Test world info content")
        )

        val sections = PromptSectionBuilder.buildDynamicContextSections(
            character = character,
            worldBookEntries = worldBookEntries,
            userName = "User"
        )

        assertEquals(1, sections.size)
        assertEquals(PromptSource.WORLD_BOOK, sections[0].source)
        assertTrue(sections[0].content.contains("Test world info content"))
    }

    @Test
    fun `buildDynamicContextSections returns sections with memory atoms`() {
        val character = createTestCharacter()
        val memoryAtoms = listOf(
            createTestMemoryAtom("fact", "User likes cats"),
            createTestMemoryAtom("character_consistency", "Character is friendly")
        )

        val sections = PromptSectionBuilder.buildDynamicContextSections(
            character = character,
            worldBookEntries = emptyList(),
            userName = "User",
            memoryAtoms = memoryAtoms
        )

        assertEquals(2, sections.size)
        val consistencySection = sections.find { it.source == PromptSource.CHARACTER_CONSISTENCY }
        val memorySection = sections.find { it.source == PromptSource.MEMORY }
        assertNotNull(consistencySection)
        assertNotNull(memorySection)
        assertTrue(consistencySection!!.content.contains("Character is friendly"))
        assertTrue(memorySection!!.content.contains("User likes cats"))
    }

    @Test
    fun `buildDynamicContextSections returns sections with persona`() {
        val character = createTestCharacter()
        val persona = PersonaEntity(
            id = 1,
            name = "Test Persona",
            biography = "Test biography"
        )

        val sections = PromptSectionBuilder.buildDynamicContextSections(
            character = character,
            worldBookEntries = emptyList(),
            userName = "User",
            persona = persona
        )

        assertEquals(1, sections.size)
        assertEquals(PromptSource.PERSONA, sections[0].source)
        assertTrue(sections[0].content.contains("Test Persona"))
        assertTrue(sections[0].content.contains("Test biography"))
    }

    @Test
    fun `buildDynamicContextSections estimates token count`() {
        val character = createTestCharacter()
        val worldBookEntries = listOf(
            createTestWorldBookEntry("World Info", "Test content with some length")
        )

        val sections = PromptSectionBuilder.buildDynamicContextSections(
            character = character,
            worldBookEntries = worldBookEntries,
            userName = "User"
        )

        assertEquals(1, sections.size)
        assertTrue(sections[0].tokenEstimate > 0)
        assertEquals(sections[0].content.length / 4, sections[0].tokenEstimate)
    }

    @Test
    fun `buildDynamicContextSections handles empty inputs`() {
        val character = createTestCharacter()

        val sections = PromptSectionBuilder.buildDynamicContextSections(
            character = character,
            worldBookEntries = emptyList(),
            userName = "User"
        )

        assertEquals(0, sections.size)
    }

    private fun createTestCharacter(): CharacterEntity {
        return CharacterEntity(
            id = 1,
            name = "Test Character",
            description = "Test description",
            personality = "Test personality",
            systemPrompt = null,
            firstMes = null,
            mesExample = null,
            postHistoryInstructions = null,
            worldBookId = null,
            avatarPath = null,
            backgroundPath = null,
            chattiness = 50
        )
    }

    private fun createTestWorldBookEntry(comment: String, content: String): WorldBookEntryEntity {
        return WorldBookEntryEntity(
            id = 1,
            worldBookId = 1,
            comment = comment,
            content = content,
            keys = "[]",
            keysSecondary = "[]",
            constant = false,
            selective = false,
            selectiveLogic = 0,
            excludeRecursion = false,
            preventRecursion = false,
            depth = 4,
            group = "",
            groupOverride = false,
            groupWeight = 100,
            probability = 100,
            disabled = false,
            automationId = ""
        )
    }

    private fun createTestMemoryAtom(category: String, content: String): MemoryAtomEntity {
        return MemoryAtomEntity(
            id = 1,
            characterId = 1,
            content = content,
            category = category,
            importance = 5,
            source = "test",
            sourceChatId = null,
            sourceMessageId = null,
            superseded = false,
            createdAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis(),
            accessCount = 0,
            expiresAt = null
        )
    }
}
