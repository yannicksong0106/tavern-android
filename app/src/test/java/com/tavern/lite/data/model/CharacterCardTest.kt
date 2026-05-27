package com.tavern.lite.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `CharacterCard serializes with default spec`() {
        val card = CharacterCard(
            data = CharacterData(name = "TestChar")
        )
        assertEquals("chara_card_v2", card.spec)
        assertEquals("TestChar", card.data.name)
    }

    @Test
    fun `CharacterData has correct defaults`() {
        val data = CharacterData()
        assertEquals("", data.name)
        assertEquals("", data.description)
        assertEquals("", data.personality)
        assertEquals("", data.mesExample)
        assertEquals("", data.firstMes)
        assertEquals("avatar.png", data.avatar)
        assertEquals(emptyList<String>(), data.tags)
        assertEquals("", data.creator)
        assertEquals("1.0", data.characterVersion)
        assertEquals(null, data.systemPrompt)
        assertEquals(null, data.postHistoryInstructions)
        assertEquals(emptyList<String>(), data.alternateGreetings)
        assertEquals(emptyList<String>(), data.groupOnlyGreetings)
        assertTrue(data.extensions.isEmpty())
    }

    @Test
    fun `CharacterCard serializes and deserializes v2`() {
        val card = CharacterCard(
            spec = "chara_card_v2",
            data = CharacterData(
                name = "Alice",
                description = "A friendly character",
                personality = "cheerful",
                firstMes = "Hello!",
                tags = listOf("friendly", "anime")
            )
        )

        val jsonStr = json.encodeToString(CharacterCard.serializer(), card)
        val decoded = json.decodeFromString(CharacterCard.serializer(), jsonStr)

        assertEquals("Alice", decoded.data.name)
        assertEquals("A friendly character", decoded.data.description)
        assertEquals("cheerful", decoded.data.personality)
        assertEquals("Hello!", decoded.data.firstMes)
        assertEquals(listOf("friendly", "anime"), decoded.data.tags)
    }

    @Test
    fun `CharacterCard serializes and deserializes v3 fields`() {
        val card = CharacterCard(
            spec = "chara_card_v3",
            data = CharacterData(
                name = "Bob",
                systemPrompt = "You are Bob",
                postHistoryInstructions = "Stay in character",
                alternateGreetings = listOf("Hi!", "Hey there!"),
                groupOnlyGreetings = listOf("Hello everyone!")
            )
        )

        val jsonStr = json.encodeToString(CharacterCard.serializer(), card)
        val decoded = json.decodeFromString(CharacterCard.serializer(), jsonStr)

        assertEquals("chara_card_v3", decoded.spec)
        assertEquals("Bob", decoded.data.name)
        assertEquals("You are Bob", decoded.data.systemPrompt)
        assertEquals("Stay in character", decoded.data.postHistoryInstructions)
        assertEquals(listOf("Hi!", "Hey there!"), decoded.data.alternateGreetings)
        assertEquals(listOf("Hello everyone!"), decoded.data.groupOnlyGreetings)
    }

    @Test
    fun `CharacterCard handles unknown fields gracefully`() {
        val jsonStr = """{
            "spec": "chara_card_v2",
            "data": {
                "name": "Charlie",
                "unknown_field": "should be ignored",
                "another_unknown": 42
            }
        }"""

        val card = json.decodeFromString(CharacterCard.serializer(), jsonStr)
        assertEquals("Charlie", card.data.name)
    }

    @Test
    fun `CharacterData serializes mesExample correctly`() {
        val data = CharacterData(
            name = "Test",
            mesExample = "{{user}}: Hi\n{{char}}: Hello!"
        )

        val jsonStr = json.encodeToString(CharacterData.serializer(), data)
        val decoded = json.decodeFromString(CharacterData.serializer(), jsonStr)

        assertEquals("{{user}}: Hi\n{{char}}: Hello!", decoded.mesExample)
    }
}
