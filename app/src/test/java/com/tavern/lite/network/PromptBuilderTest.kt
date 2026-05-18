package com.tavern.lite.network

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private fun makeCharacter(
        name: String = "Alice",
        description: String = "A friendly AI",
        personality: String = "Kind",
        firstMes: String = "Hello!",
        mesExample: String = "",
        systemPrompt: String? = null,
        postHistoryInstructions: String? = null
    ) = CharacterEntity(
        id = 1,
        name = name,
        description = description,
        personality = personality,
        firstMes = firstMes,
        mesExample = mesExample,
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions
    )

    private fun makeMessage(
        chatId: Long = 1,
        role: String = "user",
        content: String = "hi"
    ) = MessageEntity(
        id = 1,
        chatId = chatId,
        role = role,
        content = content
    )

    @Test
    fun `build returns system prompt with character description and personality`() {
        val character = makeCharacter(
            description = "A dragon rider",
            personality = "Brave"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList()
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("A dragon rider"))
        assertTrue(systemMsg.content.contains("Personality: Brave"))
    }

    @Test
    fun `build replaces placeholders in description`() {
        val character = makeCharacter(
            description = "{{char}} greets {{user}}",
            name = "Alice"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hi",
            chatHistory = emptyList(),
            userName = "Bob"
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Alice greets Bob"))
    }

    @Test
    fun `build includes first message as assistant`() {
        val character = makeCharacter(firstMes = "Welcome, traveler!")
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hi",
            chatHistory = emptyList()
        )

        val assistantMsgs = messages.filter { it.role == "assistant" }
        assertEquals(1, assistantMsgs.size)
        assertEquals("Welcome, traveler!", assistantMsgs[0].content)
    }

    @Test
    fun `build includes chat history`() {
        val character = makeCharacter(firstMes = "")
        val history = listOf(
            makeMessage(role = "user", content = "What's your name?"),
            makeMessage(role = "assistant", content = "I'm Alice.")
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Nice to meet you",
            chatHistory = history
        )

        val userMsgs = messages.filter { it.role == "user" }
        assertTrue(userMsgs.any { it.content == "What's your name?" })
        assertTrue(userMsgs.any { it.content == "Nice to meet you" })

        val assistantMsgs = messages.filter { it.role == "assistant" }
        assertTrue(assistantMsgs.any { it.content == "I'm Alice." })
    }

    @Test
    fun `build includes world book entries in system prompt`() {
        val character = makeCharacter()
        val entries = listOf(
            WorldBookEntryEntity(
                id = 1,
                worldBookId = 1,
                comment = "Dragon Lore",
                content = "Dragons are ancient creatures.",
                keys = "[\"dragon\"]"
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Tell me about dragons",
            chatHistory = emptyList(),
            worldBookEntries = entries
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("[Dragon Lore]"))
        assertTrue(systemMsg.content.contains("Dragons are ancient creatures."))
    }

    @Test
    fun `build includes memories in system prompt`() {
        val character = makeCharacter()
        val memories = listOf(
            MemoryEntity(
                id = 1,
                characterId = 1,
                content = "User prefers dark humor",
                importance = 8,
                source = "manual",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Tell me a joke",
            chatHistory = emptyList(),
            memories = memories
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("[Memory]"))
        assertTrue(systemMsg.content.contains("- User prefers dark humor"))
    }

    @Test
    fun `build parses example dialog`() {
        val character = makeCharacter(
            firstMes = "",
            mesExample = "<START>\n{{user}}: Hello\n{{char}}: Hi there!"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Test",
            chatHistory = emptyList(),
            userName = "Bob"
        )

        val exampleUser = messages.filter { it.role == "user" && it.content == "Hello" }
        val exampleAssistant = messages.filter { it.role == "assistant" && it.content == "Hi there!" }
        assertEquals(1, exampleUser.size)
        assertEquals(1, exampleAssistant.size)
    }

    @Test
    fun `build includes post history instructions`() {
        val character = makeCharacter(
            firstMes = "",
            postHistoryInstructions = "Always respond as {{char}}"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Test",
            chatHistory = emptyList(),
            userName = "Bob"
        )

        val systemMsgs = messages.filter { it.role == "system" }
        assertTrue(systemMsgs.any { it.content.contains("Always respond as Alice") })
    }

    @Test
    fun `build order is correct - system first, user message last`() {
        val character = makeCharacter(
            mesExample = "<START>\n{{user}}: ex\n{{char}}: ex reply"
        )
        val history = listOf(makeMessage(role = "user", content = "past msg"))
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "current msg",
            chatHistory = history
        )

        assertEquals("system", messages.first().role)
        assertEquals("current msg", messages.last().content)
        assertEquals("user", messages.last().role)
    }

    @Test
    fun `build handles empty character fields gracefully`() {
        val character = makeCharacter(
            description = "",
            personality = "",
            firstMes = "",
            mesExample = "",
            systemPrompt = null,
            postHistoryInstructions = null
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList()
        )

        // Should have at least the user message
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].role)
    }

    @Test
    fun `build includes memory atoms grouped by category`() {
        val character = makeCharacter(name = "Alice")
        val atoms = listOf(
            MemoryAtomEntity(
                id = 1, characterId = 1,
                content = "Alice has blue eyes",
                category = "character_consistency",
                importance = 9, source = "llm",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            ),
            MemoryAtomEntity(
                id = 2, characterId = 1,
                content = "User is a student",
                category = "user_info",
                importance = 7, source = "llm",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            memoryAtoms = atoms
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Alice has blue eyes"))
        assertTrue(systemMsg.content.contains("User is a student"))
        assertTrue(systemMsg.content.contains("核心人设"))
    }

    @Test
    fun `build prioritizes character consistency atoms`() {
        val character = makeCharacter(name = "Bob")
        val atoms = listOf(
            MemoryAtomEntity(
                id = 1, characterId = 1,
                content = "Bob is a knight",
                category = "character_consistency",
                importance = 10, source = "llm",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            ),
            MemoryAtomEntity(
                id = 2, characterId = 1,
                content = "User likes pizza",
                category = "user_info",
                importance = 5, source = "llm",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            memoryAtoms = atoms
        )

        val systemMsg = messages.first { it.role == "system" }
        // Character consistency should appear before user info
        val charIdx = systemMsg.content.indexOf("Bob is a knight")
        val userIdx = systemMsg.content.indexOf("User likes pizza")
        assertTrue(charIdx < userIdx)
    }

    @Test
    fun `build uses memory atoms over legacy memories when both provided`() {
        val character = makeCharacter()
        val atoms = listOf(
            MemoryAtomEntity(
                id = 1, characterId = 1,
                content = "New memory from atoms",
                category = "user_info",
                importance = 5, source = "llm",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val legacyMemories = listOf(
            MemoryEntity(
                id = 1, characterId = 1,
                content = "Old legacy memory",
                importance = 5, source = "manual",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            memories = legacyMemories,
            memoryAtoms = atoms
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("New memory from atoms"))
        assertFalse(systemMsg.content.contains("Old legacy memory"))
    }

    @Test
    fun `build falls back to legacy memories when no atoms`() {
        val character = makeCharacter()
        val legacyMemories = listOf(
            MemoryEntity(
                id = 1, characterId = 1,
                content = "Legacy memory content",
                importance = 5, source = "manual",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            memories = legacyMemories,
            memoryAtoms = emptyList()
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Legacy memory content"))
    }

    @Test
    fun `build includes commitment atoms`() {
        val character = makeCharacter(name = "Charlie")
        val atoms = listOf(
            MemoryAtomEntity(
                id = 1, characterId = 1,
                content = "Charlie promised to protect the village",
                category = "commitment",
                importance = 9, source = "regex",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            memoryAtoms = atoms
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("承诺"))
        assertTrue(systemMsg.content.contains("Charlie promised to protect the village"))
    }
}
