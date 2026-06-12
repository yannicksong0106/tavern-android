package com.tavern.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class QuickReplySet(
    val id: Long = 0,
    val name: String,
    val scope: QuickReplyScope = QuickReplyScope.Global,
    val characterId: Long? = null,
    val chatId: Long? = null,
    val enabled: Boolean = true,
    val displayOrder: Int = 0,
    val replies: List<QuickReply> = emptyList()
) {
    fun activeReplies(): List<QuickReply> =
        replies.filter { it.enabled }.sortedBy { it.displayOrder }
}

@Serializable
data class QuickReply(
    val id: Long = 0,
    val label: String,
    val script: String,
    val icon: String? = null,
    val automationId: String? = null,
    val enabled: Boolean = true,
    val requiresConfirmation: Boolean = false,
    val displayOrder: Int = 0
) {
    val isAutomationTrigger: Boolean
        get() = !automationId.isNullOrBlank()
}

@Serializable
enum class QuickReplyScope {
    Global,
    Character,
    Chat
}
