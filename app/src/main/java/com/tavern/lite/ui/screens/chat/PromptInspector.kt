package com.tavern.lite.ui.screens.chat

import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.port.PromptSectionInfo
import com.tavern.lite.util.TokenEstimator

data class PromptInspectorState(
    val messages: List<ChatMessage> = emptyList(),
    val sections: List<PromptSectionInfo> = emptyList(),
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

    /**
     * 获取按来源分组的 token 分布
     */
    val tokenDistribution: Map<String, Int>
        get() = sections.groupBy { it.source }
            .mapValues { (_, sections) -> sections.sumOf { it.tokenEstimate } }

    /**
     * 获取总 token 数（基于 sections）
     */
    val totalTokensFromSections: Int
        get() = sections.sumOf { it.tokenEstimate }
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
    val sections: List<PromptSectionInfo> = emptyList(),
) {
    fun toState(messages: List<ChatMessage>, summary: String?, respondingCharacterName: String) =
        PromptInspectorState(
            messages = messages,
            sections = sections,
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
