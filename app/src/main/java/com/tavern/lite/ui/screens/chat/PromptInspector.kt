package com.tavern.lite.ui.screens.chat

import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.network.ChatMessage
import com.tavern.lite.util.TokenEstimator

data class PromptInspectorState(
    val messages: List<ChatMessage> = emptyList(),
    val tokenEstimate: Int = 0,
    val worldBookCount: Int = 0,
    val memoryCount: Int = 0,
    val hasAuthorNote: Boolean = false,
    val hasPersona: Boolean = false,
    val hasPreset: Boolean = false,
    val summaryInjected: Boolean = false,
    val respondingCharacterName: String? = null,
    val error: String? = null,
) {
    val messageCount: Int get() = messages.size
}

object PromptInspectorFormatter {
    fun format(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return ""
        return messages.mapIndexed { index, message ->
            val imageInfo = if (message.imageUrls.isNotEmpty()) {
                "\n[images: ${message.imageUrls.size}]"
            } else {
                ""
            }
            val role = message.role.uppercase()
            "### ${index + 1}. $role\n${message.content}$imageInfo"
        }.joinToString("\n\n")
    }

    fun estimate(messages: List<ChatMessage>): Int = TokenEstimator.estimateMessages(messages)
}

data class PromptInspectorData(
    val worldBookEntries: List<WorldBookEntryEntity> = emptyList(),
    val memoryAtoms: List<MemoryAtomEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val authorNote: AuthorNoteEntity? = null,
    val persona: PersonaEntity? = null,
    val preset: PresetEntity? = null,
) {
    fun toState(messages: List<ChatMessage>, summary: String?, respondingCharacterName: String) =
        PromptInspectorState(
            messages = messages,
            tokenEstimate = PromptInspectorFormatter.estimate(messages),
            worldBookCount = worldBookEntries.size,
            memoryCount = memoryAtoms.size + memories.size,
            hasAuthorNote = authorNote?.content?.isNotBlank() == true,
            hasPersona = persona?.biography?.isNotBlank() == true,
            hasPreset = preset != null,
            summaryInjected = !summary.isNullOrBlank(),
            respondingCharacterName = respondingCharacterName
        )
}
