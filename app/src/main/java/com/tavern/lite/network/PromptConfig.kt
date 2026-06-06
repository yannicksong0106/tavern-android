package com.tavern.lite.network

import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity

/**
 * PromptBuilder 统一配置参数
 */
data class PromptConfig(
    val character: CharacterEntity,
    val userMessage: String = "",
    val chatHistory: List<MessageEntity> = emptyList(),
    val worldBookEntries: List<WorldBookEntryEntity> = emptyList(),
    val userName: String = "User",
    val memories: List<MemoryEntity> = emptyList(),
    val memoryAtoms: List<MemoryAtomEntity> = emptyList(),
    val authorNote: AuthorNoteEntity? = null,
    val persona: PersonaEntity? = null,
    val preset: PresetEntity? = null,
    val imageUrls: List<String> = emptyList(),
    val summary: String? = null,
    val searchResults: List<WebSearchResult> = emptyList(),
    // 群聊专用字段
    val characters: List<CharacterEntity> = emptyList(),
    val characterMap: Map<Long, CharacterEntity> = emptyMap(),
    val isGroupChat: Boolean = false,
    val isProactive: Boolean = false
) {
    val effectiveUserName: String
        get() = persona?.name?.takeIf { it.isNotBlank() } ?: userName
}
