package com.tavern.lite.network

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptConfigTest {

    private val character = CharacterEntity(name = "TestChar")

    // ==================== effectiveUserName ====================

    @Test
    fun `effectiveUserName returns persona name when persona set`() {
        val config = PromptConfig(
            character = character,
            userName = "User",
            persona = PersonaEntity(name = "Alice")
        )
        assertEquals("Alice", config.effectiveUserName)
    }

    @Test
    fun `effectiveUserName falls back to userName when persona is null`() {
        val config = PromptConfig(
            character = character,
            userName = "Bob",
            persona = null
        )
        assertEquals("Bob", config.effectiveUserName)
    }

    @Test
    fun `effectiveUserName falls back to userName when persona name is blank`() {
        val config = PromptConfig(
            character = character,
            userName = "Charlie",
            persona = PersonaEntity(name = "  ")
        )
        assertEquals("Charlie", config.effectiveUserName)
    }

    @Test
    fun `effectiveUserName falls back to userName when persona name is empty`() {
        val config = PromptConfig(
            character = character,
            userName = "Dave",
            persona = PersonaEntity(name = "")
        )
        assertEquals("Dave", config.effectiveUserName)
    }

    @Test
    fun `effectiveUserName defaults to User when no userName set`() {
        val config = PromptConfig(character = character)
        assertEquals("User", config.effectiveUserName)
    }
}
