package com.tavern.lite.network

import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class PromptBuilderTest {

    private fun makeCharacter(
        id: Long = 1,
        name: String = "Alice",
        description: String = "A friendly AI",
        personality: String = "Kind",
        firstMes: String = "Hello!",
        mesExample: String = "",
        systemPrompt: String? = null,
        postHistoryInstructions: String? = null
    ) = CharacterEntity(
        id = id,
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
        content: String = "hi",
        characterId: Long? = null
    ) = MessageEntity(
        id = 1,
        chatId = chatId,
        role = role,
        content = content,
        characterId = characterId
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
    fun `build includes world book entries in dynamic context`() {
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

        // World book is now in dynamic context (second system message), not the first
        val systemMsgs = messages.filter { it.role == "system" }
        val dynamicMsg = systemMsgs.last()
        assertTrue(dynamicMsg.content.contains("[Dragon Lore]"))
        assertTrue(dynamicMsg.content.contains("Dragons are ancient creatures."))
    }

    @Test
    fun `build includes legacy memories in dynamic context`() {
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

        val systemMsgs = messages.filter { it.role == "system" }
        val dynamicMsg = systemMsgs.last()
        assertTrue(dynamicMsg.content.contains("[Memory]"))
        assertTrue(dynamicMsg.content.contains("- User prefers dark humor"))
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

        // Static system prompt + user message
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertTrue(messages[0].content.contains("回复风格"))
        assertEquals("user", messages[1].role)
    }

    @Test
    fun `build includes memory atoms in dynamic context`() {
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
                category = "fact",
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

        // Memory atoms are in dynamic context system messages (may be split across sections)
        val systemMsgs = messages.filter { it.role == "system" }
        val allSystemContent = systemMsgs.joinToString("\n") { it.content }
        assertTrue(allSystemContent.contains("Alice has blue eyes"))
        assertTrue(allSystemContent.contains("User is a student"))
        assertTrue(allSystemContent.contains("核心人设"))
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
                category = "fact",
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

        val systemMsgs = messages.filter { it.role == "system" }
        val dynamicMsg = systemMsgs.last()
        // Character consistency should appear before user fact
        val charIdx = dynamicMsg.content.indexOf("Bob is a knight")
        val userIdx = dynamicMsg.content.indexOf("User likes pizza")
        assertTrue(charIdx < userIdx)
    }

    @Test
    fun `build uses memory atoms over legacy memories when both provided`() {
        val character = makeCharacter()
        val atoms = listOf(
            MemoryAtomEntity(
                id = 1, characterId = 1,
                content = "New memory from atoms",
                category = "fact",
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

        val systemMsgs = messages.filter { it.role == "system" }
        val dynamicMsg = systemMsgs.last()
        assertTrue(dynamicMsg.content.contains("New memory from atoms"))
        assertFalse(dynamicMsg.content.contains("Old legacy memory"))
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

        val systemMsgs = messages.filter { it.role == "system" }
        val dynamicMsg = systemMsgs.last()
        assertTrue(dynamicMsg.content.contains("Legacy memory content"))
    }

    @Test
    fun `build includes event atoms`() {
        val character = makeCharacter(name = "Charlie")
        val atoms = listOf(
            MemoryAtomEntity(
                id = 1, characterId = 1,
                content = "Charlie promised to protect the village",
                category = "event",
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

        val systemMsgs = messages.filter { it.role == "system" }
        val dynamicMsg = systemMsgs.last()
        assertTrue(dynamicMsg.content.contains("重要事件"))
        assertTrue(dynamicMsg.content.contains("Charlie promised to protect the village"))
    }

    @Test
    fun `build supports Handlebars if block in template`() {
        val character = makeCharacter(
            name = "Alice",
            description = "{{#if personality}}Personality is {{personality}}{{/if}}"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hi",
            chatHistory = emptyList()
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Personality is Kind"))
    }

    @Test
    fun `build supports description and personality template variables`() {
        val character = makeCharacter(
            name = "Alice",
            description = "A dragon rider",
            personality = "Brave",
            systemPrompt = "{{char}} is {{personality}} and {{description}}"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hi",
            chatHistory = emptyList()
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Alice is Brave and A dragon rider"))
    }

    // ========== buildGroupChat 测试 ==========

    @Test
    fun `buildGroupChat includes group chat style guide`() {
        val alice = makeCharacter(id = 1, name = "Alice", description = "A friendly AI")
        val bob = makeCharacter(id = 2, name = "Bob", description = "A wise wizard")
        val messages = PromptBuilder.buildGroupChat(
            characters = listOf(alice, bob),
            respondingCharacter = alice,
            userMessage = "Hello everyone",
            chatHistory = emptyList(),
            characterMap = mapOf(1L to alice, 2L to bob)
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("群聊回复风格"))
        assertTrue(systemMsg.content.contains("Alice"))
    }

    @Test
    fun `buildGroupChat includes other characters brief info`() {
        val alice = makeCharacter(id = 1, name = "Alice", description = "A friendly AI who loves chatting")
        val bob = makeCharacter(id = 2, name = "Bob", description = "A wise wizard who studies magic")
        val messages = PromptBuilder.buildGroupChat(
            characters = listOf(alice, bob),
            respondingCharacter = alice,
            userMessage = "Hello",
            chatHistory = emptyList(),
            characterMap = mapOf(1L to alice, 2L to bob)
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Bob"))
        assertTrue(systemMsg.content.contains("wise wizard"))
    }

    @Test
    fun `buildGroupChat formats chat history with character attribution`() {
        val alice = makeCharacter(id = 1, name = "Alice")
        val bob = makeCharacter(id = 2, name = "Bob")
        val history = listOf(
            makeMessage(role = "user", content = "Hi all"),
            makeMessage(role = "assistant", content = "Hello!", characterId = 1),
            makeMessage(role = "assistant", content = "Hey there!", characterId = 2)
        )
        val messages = PromptBuilder.buildGroupChat(
            characters = listOf(alice, bob),
            respondingCharacter = alice,
            userMessage = "What's up?",
            chatHistory = history,
            characterMap = mapOf(1L to alice, 2L to bob)
        )

        val assistantMsgs = messages.filter { it.role == "assistant" }
        assertTrue(assistantMsgs.any { it.content.contains("[Alice]: Hello!") })
        assertTrue(assistantMsgs.any { it.content.contains("[Bob]: Hey there!") })
    }

    @Test
    fun `buildGroupChat includes opening messages from all characters`() {
        val alice = makeCharacter(id = 1, name = "Alice", firstMes = "Welcome!")
        val bob = makeCharacter(id = 2, name = "Bob", firstMes = "Greetings!")
        val messages = PromptBuilder.buildGroupChat(
            characters = listOf(alice, bob),
            respondingCharacter = alice,
            userMessage = "Hi",
            chatHistory = emptyList(),
            characterMap = mapOf(1L to alice, 2L to bob)
        )

        val assistantMsgs = messages.filter { it.role == "assistant" }
        assertTrue(assistantMsgs.any { it.content.contains("[Alice]: Welcome!") })
        assertTrue(assistantMsgs.any { it.content.contains("[Bob]: Greetings!") })
    }

    // ========== buildProactive 测试 ==========

    @Test
    fun `buildProactive includes proactive dialogue instruction`() {
        val character = makeCharacter()
        val messages = PromptBuilder.buildProactive(
            character = character,
            chatHistory = listOf(makeMessage(role = "user", content = "Bye!"))
        )

        val systemMsgs = messages.filter { it.role == "system" }
        assertTrue(systemMsgs.any { it.content.contains("主动对话指令") })
        assertTrue(systemMsgs.any { it.content.contains("延伸话题") })
    }

    @Test
    fun `buildProactive ends with empty user message`() {
        val character = makeCharacter()
        val messages = PromptBuilder.buildProactive(
            character = character,
            chatHistory = emptyList()
        )

        assertEquals("user", messages.last().role)
        assertEquals("...", messages.last().content)
    }

    @Test
    fun `buildGroupProactive includes proactive group instruction`() {
        val alice = makeCharacter(id = 1, name = "Alice")
        val bob = makeCharacter(id = 2, name = "Bob")
        val messages = PromptBuilder.buildGroupProactive(
            characters = listOf(alice, bob),
            respondingCharacter = alice,
            chatHistory = listOf(makeMessage(role = "user", content = "Hi")),
            characterMap = mapOf(1L to alice, 2L to bob)
        )

        val systemMsgs = messages.filter { it.role == "system" }
        assertTrue(systemMsgs.any { it.content.contains("主动发言指令") })
        assertTrue(systemMsgs.any { it.content.contains("插话") })
    }

    // ========== Author Note 测试 ==========

    @Test
    fun `build injects author note at specified depth`() {
        val character = makeCharacter(firstMes = "")
        val authorNote = AuthorNoteEntity(
            id = 1, characterId = 1,
            content = "Remember: stay in character",
            depth = 2
        )
        val history = listOf(
            makeMessage(role = "user", content = "msg1"),
            makeMessage(role = "assistant", content = "reply1"),
            makeMessage(role = "user", content = "msg2")
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "current",
            chatHistory = history,
            authorNote = authorNote
        )

        // Author note should be injected at depth 2 from end
        val noteMsg = messages.find { it.content.contains("Remember: stay in character") }
        assertTrue(noteMsg != null)
        assertEquals("system", noteMsg!!.role)
    }

    @Test
    fun `build injects author note safely when depth is negative`() {
        val character = makeCharacter(firstMes = "")
        val authorNote = AuthorNoteEntity(
            id = 1, characterId = 1,
            content = "Remember: stay in character",
            depth = -5
        )
        val history = listOf(
            makeMessage(role = "user", content = "msg1"),
            makeMessage(role = "assistant", content = "reply1"),
            makeMessage(role = "user", content = "msg2")
        )
        // 负 depth 曾使 insertIndex > size 触发 IndexOutOfBoundsException；双向 clamp 后应安全注入。
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "current",
            chatHistory = history,
            authorNote = authorNote
        )

        val noteMsg = messages.find { it.content.contains("Remember: stay in character") }
        assertTrue(noteMsg != null)
        assertEquals("system", noteMsg!!.role)
    }

    @Test
    fun `build includes preset author note`() {
        val character = makeCharacter(firstMes = "")
        val preset = PresetEntity(
            id = 1, name = "Test Preset",
            authorNote = "Preset author note content"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            preset = preset
        )

        val noteMsg = messages.find { it.content.contains("Preset author note content") }
        assertTrue(noteMsg != null)
        assertEquals("system", noteMsg!!.role)
    }

    // ========== 预设合并逻辑测试 ==========

    @Test
    fun `build uses preset systemPrompt when provided`() {
        val character = makeCharacter()
        val preset = PresetEntity(
            id = 1, name = "Test Preset",
            systemPrompt = "Custom system prompt from preset"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            preset = preset
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Custom system prompt from preset"))
    }

    @Test
    fun `build uses preset postHistoryInstructions over character built-in`() {
        val character = makeCharacter(
            firstMes = "",
            postHistoryInstructions = "Character's instruction"
        )
        val preset = PresetEntity(
            id = 1, name = "Test Preset",
            postHistoryInstructions = "Preset's instruction"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            preset = preset
        )

        val systemMsgs = messages.filter { it.role == "system" }
        assertTrue(systemMsgs.any { it.content.contains("Preset's instruction") })
        assertFalse(systemMsgs.any { it.content.contains("Character's instruction") })
    }

    // ========== 搜索结果注入测试 ==========

    @Test
    fun `build includes search results`() {
        val character = makeCharacter(firstMes = "")
        val searchResults = listOf(
            WebSearchResult(
                title = "Dragon Facts",
                snippet = "Dragons breathe fire",
                url = "https://example.com/dragons"
            )
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Tell me about dragons",
            chatHistory = emptyList(),
            searchResults = searchResults
        )

        val systemMsgs = messages.filter { it.role == "system" }
        val searchMsg = systemMsgs.find { it.content.contains("Web Search Results") }
        assertTrue(searchMsg != null)
        assertTrue(searchMsg!!.content.contains("Dragon Facts"))
        assertTrue(searchMsg.content.contains("Dragons breathe fire"))
        assertTrue(searchMsg.content.contains("https://example.com/dragons"))
    }

    // ========== 摘要注入测试 ==========

    @Test
    fun `build includes summary`() {
        val character = makeCharacter(firstMes = "")
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Continue",
            chatHistory = emptyList(),
            summary = "Previously: user asked about dragons"
        )

        val systemMsgs = messages.filter { it.role == "system" }
        val summaryMsg = systemMsgs.find { it.content.contains("对话摘要") }
        assertTrue(summaryMsg != null)
        assertTrue(summaryMsg!!.content.contains("Previously: user asked about dragons"))
    }

    // ========== 用户人格测试 ==========

    @Test
    fun `build includes persona biography in dynamic context`() {
        val character = makeCharacter(firstMes = "")
        val persona = PersonaEntity(
            id = 1, name = "Bob",
            biography = "A software engineer who loves gaming"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            persona = persona
        )

        val systemMsgs = messages.filter { it.role == "system" }
        val dynamicMsg = systemMsgs.last()
        assertTrue(dynamicMsg.content.contains("[User Persona: Bob]"))
        assertTrue(dynamicMsg.content.contains("A software engineer who loves gaming"))
    }

    @Test
    fun `build uses persona name as effectiveUserName`() {
        val character = makeCharacter(
            description = "{{user}} is my friend",
            firstMes = ""
        )
        val persona = PersonaEntity(
            id = 1, name = "Bob",
            biography = "A gamer"
        )
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            persona = persona
        )

        val systemMsg = messages.first { it.role == "system" }
        assertTrue(systemMsg.content.contains("Bob is my friend"))
    }

    // ========== 图片 URL 测试 ==========

    @Test
    fun `build includes imageUrls in user message`() {
        val character = makeCharacter(firstMes = "")
        val imageUrls = listOf("https://example.com/image1.jpg", "https://example.com/image2.png")
        val messages = PromptBuilder.build(
            character = character,
            userMessage = "Look at these",
            chatHistory = emptyList(),
            imageUrls = imageUrls
        )

        val userMsg = messages.last()
        assertEquals("user", userMsg.role)
        assertEquals("Look at these", userMsg.content)
        assertEquals(imageUrls, userMsg.imageUrls)
    }

    @Test
    fun `build handles long chat history within performance budget`() {
        PromptBuilder.invalidateCache()
        TemplateEngine.clearCache()

        val character = makeCharacter(
            id = 42,
            name = "Alice",
            description = "{{char}} is a careful archivist speaking with {{user}}.",
            personality = "Observant and concise",
            firstMes = "I will keep track of the details.",
            mesExample = "<START>\n{{user}}: Remember this?\n{{char}}: I remember the important parts.",
            systemPrompt = "Keep continuity with {{personaDescription}}.",
            postHistoryInstructions = "Answer as {{char}} while preserving prior facts."
        )
        val history = (1..LONG_HISTORY_COUNT).map { index ->
            makeMessage(
                role = if (index % 2 == 0) "assistant" else "user",
                content = "History message $index about a long-running scene.",
                characterId = if (index % 2 == 0) character.id else null
            ).copy(id = index.toLong(), createdAt = 1_000L + index)
        }
        val atoms = listOf(
            makeAtom(1, "character_consistency", 10, "Alice never contradicts established facts."),
            makeAtom(2, "fact", 8, "User is tracking a multi-session mystery."),
            makeAtom(3, "temporary", 6, "The current scene is inside the archive.")
        )
        val entries = listOf(
            WorldBookEntryEntity(
                id = 1,
                worldBookId = 1,
                comment = "Archive",
                content = "The archive stores long-term clues.",
                keys = "[\"archive\"]"
            )
        )
        val persona = PersonaEntity(
            id = 1,
            name = "Morgan",
            biography = "Morgan prefers compact answers and continuity."
        )

        var messages: List<ChatMessage> = emptyList()
        val elapsedMs = measureTimeMillis {
            messages = PromptBuilder.build(
                character = character,
                userMessage = "What do we know now?",
                chatHistory = history,
                worldBookEntries = entries,
                memoryAtoms = atoms,
                persona = persona,
                summary = "The mystery has three open clues.",
                searchResults = listOf(
                    WebSearchResult(
                        title = "Archive note",
                        snippet = "External context for a clue.",
                        url = "https://example.com/archive"
                    )
                )
            )
        }

        assertTrue("Prompt build took ${elapsedMs}ms", elapsedMs < LONG_HISTORY_BUDGET_MS)
        assertEquals("user", messages.last().role)
        assertEquals("What do we know now?", messages.last().content)
        assertTrue("Expected at least ${LONG_HISTORY_COUNT + 1} messages, got ${messages.size}", messages.size >= LONG_HISTORY_COUNT + 1)
        assertTrue(messages.any { it.content == "History message 1 about a long-running scene." })
        assertTrue(messages.any { it.content == "History message $LONG_HISTORY_COUNT about a long-running scene." })
        assertTrue(messages.any { it.content.contains("The archive stores long-term clues.") })
        assertTrue(messages.any { it.content.contains("Alice never contradicts established facts.") })
        assertTrue(messages.any { it.content.contains("The mystery has three open clues.") })
        assertTrue(messages.any { it.content.contains("Archive note") })
    }

    // ========== buildWithSections 测试 ==========

    @Test
    fun `buildWithSections returns messages and sections`() {
        val character = makeCharacter()
        val config = PromptConfig(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList()
        )
        val (messages, sections) = PromptBuilder.buildWithSections(config)

        assertTrue(messages.isNotEmpty())
        assertTrue(sections.isNotEmpty())
        assertEquals(messages.size, messages.filter { it.role in listOf("system", "user", "assistant") }.size)
    }

    @Test
    fun `buildWithSections tracks system prompt section`() {
        val character = makeCharacter(description = "A dragon rider")
        val config = PromptConfig(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList()
        )
        val (_, sections) = PromptBuilder.buildWithSections(config)

        val systemSection = sections.find { it.source == PromptSource.SYSTEM }
        assertTrue(systemSection != null)
        assertTrue(systemSection!!.content.contains("A dragon rider"))
    }

    @Test
    fun `buildWithSections tracks world book section`() {
        val character = makeCharacter()
        val entries = listOf(
            WorldBookEntryEntity(
                id = 1, worldBookId = 1,
                comment = "Dragon Lore",
                content = "Dragons are ancient.",
                keys = "[\"dragon\"]"
            )
        )
        val config = PromptConfig(
            character = character,
            userMessage = "Tell me about dragons",
            chatHistory = emptyList(),
            worldBookEntries = entries
        )
        val (_, sections) = PromptBuilder.buildWithSections(config)

        val wbSection = sections.find { it.source == PromptSource.WORLD_BOOK }
        assertTrue(wbSection != null)
        assertTrue(wbSection!!.content.contains("Dragons are ancient."))
    }

    @Test
    fun `buildWithSections tracks memory atoms sections`() {
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
                content = "User likes cats",
                category = "fact",
                importance = 7, source = "llm",
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis()
            )
        )
        val config = PromptConfig(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList(),
            memoryAtoms = atoms
        )
        val (_, sections) = PromptBuilder.buildWithSections(config)

        val consistencySection = sections.find { it.source == PromptSource.CHARACTER_CONSISTENCY }
        val memorySection = sections.find { it.source == PromptSource.MEMORY }
        assertTrue(consistencySection != null)
        assertTrue(memorySection != null)
        assertTrue(consistencySection!!.content.contains("Alice has blue eyes"))
        assertTrue(memorySection!!.content.contains("User likes cats"))
    }

    @Test
    fun `buildWithSections token estimates are positive`() {
        val character = makeCharacter()
        val config = PromptConfig(
            character = character,
            userMessage = "Hello",
            chatHistory = emptyList()
        )
        val (_, sections) = PromptBuilder.buildWithSections(config)

        assertTrue(sections.all { it.tokenEstimate > 0 })
        assertTrue(sections.all { it.tokenEstimate == it.content.length / 4 })
    }

    private fun makeAtom(
        id: Long,
        category: String,
        importance: Int,
        content: String
    ) = MemoryAtomEntity(
        id = id,
        characterId = 42,
        content = content,
        category = category,
        importance = importance,
        source = "test",
        createdAt = System.currentTimeMillis(),
        lastAccessed = System.currentTimeMillis()
    )

    private companion object {
        const val LONG_HISTORY_COUNT = 150
        const val LONG_HISTORY_EXTRA_MESSAGES = 9
        const val LONG_HISTORY_BUDGET_MS = 1_000L
    }
}
