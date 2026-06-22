package com.tavern.lite.data.model

import com.tavern.lite.data.db.entity.*
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.2.8",
    val characters: List<CharacterBackup> = emptyList(),
    val chats: List<ChatBackup> = emptyList(),
    val chatCharacters: List<ChatCharacterBackup> = emptyList(),
    val messages: List<MessageBackup> = emptyList(),
    val memories: List<MemoryBackup> = emptyList(),
    val memoryAtoms: List<MemoryAtomBackup> = emptyList(),
    val worldBooks: List<WorldBookBackup> = emptyList(),
    val worldBookEntries: List<WorldBookEntryBackup> = emptyList(),
    val scripts: List<ScriptBackup> = emptyList(),
    val personas: List<PersonaBackup> = emptyList(),
    val characterPersonas: List<CharacterPersonaBackup> = emptyList(),
    val presets: List<PresetBackup> = emptyList(),
    val branches: List<BranchBackup> = emptyList(),
    val summaries: List<SummaryBackup> = emptyList(),
    val sprites: List<SpriteBackup> = emptyList(),
    val bgms: List<BgmBackup> = emptyList(),
    val authorNotes: List<AuthorNoteBackup> = emptyList(),
    val quickReplySets: List<QuickReplySetBackup> = emptyList(),
    val quickReplies: List<QuickReplyBackup> = emptyList()
)

@Serializable
data class CharacterBackup(
    val id: Long,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val firstMessage: String = "",
    val mesExample: String = "",
    val avatarPath: String? = null,
    val systemPrompt: String? = null,
    val postHistoryInstructions: String? = null,
    val tags: String = "[]",
    val worldBookId: Long? = null,
    val presetId: Long? = null,
    val backgroundPath: String? = null,
    val creator: String = "",
    val version: String = "1.0",
    val spec: String = "chara_card_v2",
    val chattiness: Int = 50,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ChatBackup(
    val id: Long,
    val characterId: Long,
    val name: String? = null,
    val backgroundPath: String? = null,
    val presetId: Long? = null,
    val isGroup: Boolean = false,
    val groupChattiness: Int = 50,
    val schedulingStrategy: String = "natural",
    val messageIntervalMs: Long = 1500L,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ChatCharacterBackup(
    val id: Long,
    val chatId: Long,
    val characterId: Long,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val chattiness: Int = 50,
    val createdAt: Long
)

@Serializable
data class MessageBackup(
    val id: Long,
    val chatId: Long,
    val role: String,
    val content: String,
    val characterId: Long? = null,
    val parentId: Long? = null,
    val branchId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val swipeContent: String = "[]",
    val swipeIndex: Int = 0,
    val replyToId: Long? = null,
    val isPinned: Boolean = false,
    val imagePaths: String = "[]"
)

@Serializable
data class MemoryBackup(
    val id: Long,
    val characterId: Long,
    val content: String,
    val importance: Int = 5,
    val source: String = "manual",
    val createdAt: Long,
    val lastAccessed: Long,
    val accessCount: Int = 0
)

@Serializable
data class MemoryAtomBackup(
    val id: Long,
    val characterId: Long,
    val content: String,
    val category: String,
    val importance: Int = 5,
    val source: String = "llm",
    val sourceChatId: Long? = null,
    val sourceMessageId: Long? = null,
    val superseded: Boolean = false,
    val createdAt: Long,
    val lastAccessed: Long,
    val accessCount: Int = 0,
    val expiresAt: Long? = null
)

@Serializable
data class WorldBookBackup(
    val id: Long,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class WorldBookEntryBackup(
    val id: Long,
    val worldBookId: Long,
    val uid: Int = 0,
    val comment: String = "",
    val keys: String = "[]",
    val keysSecondary: String = "[]",
    val content: String = "",
    val constant: Boolean = false,
    val position: Int = 0,
    val orderVal: Int = 100,
    val probability: Int = 100,
    val depth: Int = 4,
    val disabled: Boolean = false,
    val selective: Boolean = false,
    val selectiveLogic: Int = 0,
    val excludeRecursion: Boolean = false,
    val preventRecursion: Boolean = false,
    val group: String = "",
    val groupOverride: Boolean = false,
    val groupWeight: Int = 100
)

@Serializable
data class ScriptBackup(
    val id: Long,
    val characterId: Long,
    val name: String = "",
    val comment: String = "",
    val scriptType: Int = 0,
    val findPattern: String = "",
    val replacePattern: String = "",
    val isRegex: Boolean = true,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

@Serializable
data class PersonaBackup(
    val id: Long,
    val name: String,
    val biography: String = "",
    val avatarPath: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long
)

@Serializable
data class CharacterPersonaBackup(
    val characterId: Long,
    val personaId: Long
)

@Serializable
data class PresetBackup(
    val id: Long,
    val name: String,
    val description: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val authorNote: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BranchBackup(
    val id: Long,
    val chatId: Long,
    val name: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long
)

@Serializable
data class SummaryBackup(
    val id: Long,
    val chatId: Long,
    val content: String,
    val messageRangeStart: Long,
    val messageRangeEnd: Long,
    val tokenCount: Int = 0,
    val createdAt: Long
)

@Serializable
data class SpriteBackup(
    val id: Long,
    val characterId: Long,
    val emotion: String = "neutral",
    val imagePath: String,
    val displayOrder: Int = 0,
    val createdAt: Long
)

@Serializable
data class BgmBackup(
    val id: Long,
    val characterId: Long,
    val name: String = "",
    val audioPath: String,
    val loop: Boolean = true,
    val volume: Float = 0.5f,
    val emotion: String = "",
    val displayOrder: Int = 0,
    val createdAt: Long
)

@Serializable
data class AuthorNoteBackup(
    val id: Long,
    val characterId: Long,
    val content: String = "",
    val position: String = "after_an",
    val depth: Int = 4,
    val updatedAt: Long
)

@Serializable
data class QuickReplySetBackup(
    val id: Long,
    val name: String,
    val scope: String = "global",
    val characterId: Long? = null,
    val chatId: Long? = null,
    val enabled: Boolean = true,
    val displayOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class QuickReplyBackup(
    val id: Long,
    val setId: Long,
    val label: String,
    val script: String,
    val icon: String? = null,
    val automationId: String? = null,
    val enabled: Boolean = true,
    val requiresConfirmation: Boolean = false,
    val allowAutoRun: Boolean = false,
    val canSendMessages: Boolean = false,
    val canTriggerGeneration: Boolean = false,
    val displayOrder: Int = 0
)

data class RestoreResult(
    val charactersRestored: Int,
    val chatsRestored: Int,
    val messagesRestored: Int,
    val memoriesRestored: Int,
    val worldBooksRestored: Int,
    val scriptsRestored: Int,
    val personasRestored: Int,
    val presetsRestored: Int,
    val chatCharactersRestored: Int = 0,
    val characterPersonasRestored: Int = 0,
    val branchesRestored: Int = 0,
    val summariesRestored: Int = 0,
    val spritesRestored: Int = 0,
    val bgmsRestored: Int = 0,
    val authorNotesRestored: Int = 0,
    val quickReplySetsRestored: Int = 0,
    val quickRepliesRestored: Int = 0,
    val backupAppVersion: String? = null
)
