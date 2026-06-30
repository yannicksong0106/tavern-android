package com.tavern.lite.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDataSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `BackupData empty round-trip`() {
        val original = BackupData()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BackupData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BackupData full round-trip with all entity types`() {
        val original = BackupData(
            version = 2,
            appVersion = "1.3.1",
            characters = listOf(CharacterBackup(id = 1, name = "Alice", createdAt = 100, updatedAt = 200)),
            chats = listOf(ChatBackup(id = 1, characterId = 1, createdAt = 100, updatedAt = 200, isGroup = true)),
            chatCharacters = listOf(ChatCharacterBackup(id = 1, chatId = 1, characterId = 1, createdAt = 100)),
            messages = listOf(MessageBackup(id = 1, chatId = 1, role = "user", content = "Hello", createdAt = 100)),
            memories = listOf(MemoryBackup(id = 1, characterId = 1, content = "Fact", createdAt = 100, lastAccessed = 200)),
            memoryAtoms = listOf(MemoryAtomBackup(id = 1, characterId = 1, content = "Atom", category = "fact", createdAt = 100, lastAccessed = 200, expiresAt = 300)),
            worldBooks = listOf(WorldBookBackup(id = 1, name = "Lore", createdAt = 100, updatedAt = 200)),
            worldBookEntries = listOf(WorldBookEntryBackup(id = 1, worldBookId = 1, comment = "Entry", constant = true, selective = true, selectiveLogic = 1)),
            scripts = listOf(ScriptBackup(id = 1, characterId = 1, name = "Script", findPattern = "hello", replacePattern = "hi", isRegex = false)),
            personas = listOf(PersonaBackup(id = 1, name = "User", isDefault = true, createdAt = 100)),
            characterPersonas = listOf(CharacterPersonaBackup(characterId = 1, personaId = 1)),
            presets = listOf(PresetBackup(id = 1, name = "Preset", systemPrompt = "Be creative", createdAt = 100, updatedAt = 200)),
            branches = listOf(BranchBackup(id = 1, chatId = 1, name = "Branch 1", isDefault = true, createdAt = 100)),
            summaries = listOf(SummaryBackup(id = 1, chatId = 1, content = "Summary", messageRangeStart = 1, messageRangeEnd = 10, createdAt = 100)),
            sprites = listOf(SpriteBackup(id = 1, characterId = 1, emotion = "happy", imagePath = "/path/sprite.png", createdAt = 100)),
            bgms = listOf(BgmBackup(id = 1, characterId = 1, name = "Battle BGM", audioPath = "/path/bgm.mp3", emotion = "angry", volume = 0.8f, createdAt = 100)),
            authorNotes = listOf(AuthorNoteBackup(id = 1, characterId = 1, content = "Note", position = "before_an", depth = 2, updatedAt = 200)),
            quickReplySets = listOf(QuickReplySetBackup(id = 1, name = "QR Set", scope = "character", characterId = 1, createdAt = 100, updatedAt = 200)),
            quickReplies = listOf(QuickReplyBackup(id = 1, setId = 1, label = "Greet", script = "/send Hi!", automationId = "chat_open", allowAutoRun = true, canSendMessages = true))
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BackupData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `WorldBookEntryBackup all boolean fields round-trip`() {
        val original = WorldBookEntryBackup(
            id = 1, worldBookId = 1,
            constant = true, disabled = true, selective = true,
            excludeRecursion = true, preventRecursion = true, groupOverride = true
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WorldBookEntryBackup>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.constant)
        assertTrue(decoded.disabled)
        assertTrue(decoded.selective)
        assertTrue(decoded.excludeRecursion)
        assertTrue(decoded.preventRecursion)
        assertTrue(decoded.groupOverride)
    }

    @Test
    fun `WorldBookEntryBackup default values round-trip`() {
        val original = WorldBookEntryBackup(id = 1, worldBookId = 1)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WorldBookEntryBackup>(encoded)
        assertEquals(original, decoded)
        assertEquals(100, decoded.orderVal)
        assertEquals(100, decoded.probability)
        assertEquals(4, decoded.depth)
    }

    @Test
    fun `ScriptBackup round-trip with all fields`() {
        val original = ScriptBackup(
            id = 1, characterId = 1, name = "Replace",
            findPattern = "hello", replacePattern = "hi",
            isRegex = false, caseSensitive = true, enabled = false, sortOrder = 5
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ScriptBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `MemoryAtomBackup round-trip with nullable fields`() {
        val original = MemoryAtomBackup(
            id = 1, characterId = 1, content = "fact",
            category = "fact", sourceChatId = 10, sourceMessageId = 20,
            superseded = true, createdAt = 100, lastAccessed = 200, expiresAt = 300
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MemoryAtomBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `MemoryAtomBackup round-trip with null optional fields`() {
        val original = MemoryAtomBackup(
            id = 1, characterId = 1, content = "fact",
            category = "fact", createdAt = 100, lastAccessed = 200,
            sourceChatId = null, sourceMessageId = null, expiresAt = null
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MemoryAtomBackup>(encoded)
        assertEquals(original, decoded)
        assertNull(decoded.sourceChatId)
        assertNull(decoded.expiresAt)
    }

    @Test
    fun `PresetBackup round-trip`() {
        val original = PresetBackup(
            id = 1, name = "Creative", description = "Creative preset",
            systemPrompt = "Be creative", postHistoryInstructions = "Post",
            authorNote = "Note", isDefault = true, createdAt = 100, updatedAt = 200
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PresetBackup>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.isDefault)
    }

    @Test
    fun `QuickReplyBackup round-trip with permissions`() {
        val original = QuickReplyBackup(
            id = 1, setId = 1, label = "Auto", script = "/trigger event",
            automationId = "chat_open", allowAutoRun = true,
            canSendMessages = true, canTriggerGeneration = true,
            requiresConfirmation = false, displayOrder = 3
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<QuickReplyBackup>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.allowAutoRun)
        assertTrue(decoded.canSendMessages)
    }

    @Test
    fun `QuickReplyBackup round-trip with null optional fields`() {
        val original = QuickReplyBackup(
            id = 1, setId = 1, label = "Manual", script = "/send hello",
            icon = null, automationId = null
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<QuickReplyBackup>(encoded)
        assertEquals(original, decoded)
        assertNull(decoded.icon)
        assertNull(decoded.automationId)
    }

    @Test
    fun `BgmBackup round-trip`() {
        val original = BgmBackup(
            id = 1, characterId = 1, name = "Calm BGM",
            audioPath = "/path/calm.mp3", loop = false, volume = 0.3f,
            emotion = "sad", displayOrder = 2, createdAt = 100
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BgmBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `CharacterBackup round-trip with nullable fields`() {
        val original = CharacterBackup(
            id = 1, name = "Alice", description = "AI",
            avatarPath = "/path/avatar.png", systemPrompt = "System",
            worldBookId = 5, presetId = 3, backgroundPath = "/path/bg.png",
            chattiness = 80, createdAt = 100, updatedAt = 200
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CharacterBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `CharacterBackup round-trip with null optional fields`() {
        val original = CharacterBackup(
            id = 1, name = "Bob", createdAt = 100, updatedAt = 200,
            avatarPath = null, systemPrompt = null, worldBookId = null, presetId = null, backgroundPath = null
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CharacterBackup>(encoded)
        assertEquals(original, decoded)
        assertNull(decoded.avatarPath)
        assertNull(decoded.worldBookId)
    }

    @Test
    fun `RestoreResult default values`() {
        val result = RestoreResult(
            charactersRestored = 1, chatsRestored = 2, messagesRestored = 10,
            memoriesRestored = 3, worldBooksRestored = 1, scriptsRestored = 2,
            personasRestored = 1, presetsRestored = 1
        )
        assertEquals(1, result.charactersRestored)
        assertEquals(0, result.branchesRestored)
        assertEquals(0, result.quickReplySetsRestored)
        assertNull(result.backupAppVersion)
    }

    @Test
    fun `RestoreResult with all fields`() {
        val result = RestoreResult(
            charactersRestored = 5, chatsRestored = 10, messagesRestored = 100,
            memoriesRestored = 20, worldBooksRestored = 3, scriptsRestored = 5,
            personasRestored = 2, presetsRestored = 4, chatCharactersRestored = 8,
            characterPersonasRestored = 3, branchesRestored = 6, summariesRestored = 4,
            spritesRestored = 10, bgmsRestored = 5, authorNotesRestored = 3,
            quickReplySetsRestored = 2, quickRepliesRestored = 8, backupAppVersion = "1.2.8"
        )
        assertEquals(5, result.charactersRestored)
        assertEquals(10, result.chatsRestored)
        assertEquals("1.2.8", result.backupAppVersion)
    }

    // ==================== Additional branch coverage ====================

    @Test
    fun `ChatBackup round-trip with group settings`() {
        val original = ChatBackup(
            id = 1, characterId = 1, name = "Group Chat",
            isGroup = true, groupChattiness = 80, schedulingStrategy = "round_robin",
            messageIntervalMs = 3000L, presetId = 5, createdAt = 100, updatedAt = 200
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ChatBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `ChatBackup round-trip with null optional fields`() {
        val original = ChatBackup(
            id = 1, characterId = 1, createdAt = 100, updatedAt = 200,
            name = null, backgroundPath = null, presetId = null
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ChatBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `MessageBackup round-trip with all optional fields`() {
        val original = MessageBackup(
            id = 1, chatId = 1, role = "assistant", content = "Hello",
            characterId = 5, parentId = 3, branchId = 2, isActive = false,
            swipeContent = "[\"alt1\",\"alt2\"]", swipeIndex = 1,
            replyToId = 10, isPinned = true, imagePaths = "[\"/img.png\"]",
            createdAt = 100
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MessageBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `MessageBackup round-trip with null optional fields`() {
        val original = MessageBackup(
            id = 1, chatId = 1, role = "user", content = "Hi",
            characterId = null, parentId = null, branchId = null, replyToId = null,
            createdAt = 100
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MessageBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SpriteBackup round-trip`() {
        val original = SpriteBackup(
            id = 1, characterId = 1, emotion = "surprised",
            imagePath = "/path/sprite.png", displayOrder = 3, createdAt = 100
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SpriteBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `AuthorNoteBackup round-trip with different position`() {
        val original = AuthorNoteBackup(
            id = 1, characterId = 1, content = "Important note",
            position = "before_an", depth = 0, updatedAt = 200
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<AuthorNoteBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BranchBackup round-trip`() {
        val original = BranchBackup(id = 1, chatId = 1, name = "Story Branch", isDefault = false, createdAt = 100)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BranchBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SummaryBackup round-trip with token count`() {
        val original = SummaryBackup(
            id = 1, chatId = 1, content = "Summary text",
            messageRangeStart = 1, messageRangeEnd = 50, tokenCount = 500, createdAt = 100
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SummaryBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `QuickReplySetBackup round-trip with chat scope`() {
        val original = QuickReplySetBackup(
            id = 1, name = "Chat QRs", scope = "chat", chatId = 42,
            enabled = false, displayOrder = 5, createdAt = 100, updatedAt = 200
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<QuickReplySetBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `WorldBookBackup round-trip with description`() {
        val original = WorldBookBackup(id = 1, name = "Fantasy Lore", description = "Fantasy world setting", createdAt = 100, updatedAt = 200)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WorldBookBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `ChatCharacterBackup round-trip`() {
        val original = ChatCharacterBackup(id = 1, chatId = 1, characterId = 2, displayOrder = 3, isActive = false, chattiness = 70, createdAt = 100)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ChatCharacterBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `CharacterPersonaBackup round-trip`() {
        val original = CharacterPersonaBackup(characterId = 5, personaId = 3)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CharacterPersonaBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `PersonaBackup round-trip with avatar`() {
        val original = PersonaBackup(id = 1, name = "My Persona", biography = "Bio text", avatarPath = "/avatar.png", isDefault = true, createdAt = 100)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PersonaBackup>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `MemoryBackup round-trip with all fields`() {
        val original = MemoryBackup(
            id = 1, characterId = 1, content = "Important memory",
            importance = 9, source = "manual", createdAt = 100, lastAccessed = 200, accessCount = 15
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MemoryBackup>(encoded)
        assertEquals(original, decoded)
    }
}
