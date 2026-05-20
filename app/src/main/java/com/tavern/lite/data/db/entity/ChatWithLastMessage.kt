package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo

data class ChatWithLastMessage(
    val id: Long,
    @ColumnInfo(name = "character_id") val characterId: Long,
    val name: String?,
    @ColumnInfo(name = "is_group") val isGroup: Boolean,
    @ColumnInfo(name = "group_chattiness") val groupChattiness: Int,
    @ColumnInfo(name = "background_path") val backgroundPath: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    // Last message fields
    @ColumnInfo(name = "last_message_role") val lastMessageRole: String?,
    @ColumnInfo(name = "last_message_content") val lastMessageContent: String?
) {
    fun toChatEntity() = ChatEntity(
        id = id,
        characterId = characterId,
        name = name,
        isGroup = isGroup,
        groupChattiness = groupChattiness,
        backgroundPath = backgroundPath,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
