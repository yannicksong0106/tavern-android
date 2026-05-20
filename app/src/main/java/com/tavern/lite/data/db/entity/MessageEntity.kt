package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chat_id"), Index("parent_id"), Index("character_id")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "chat_id") val chatId: Long,
    val role: String, // "user" / "assistant" / "system"
    val content: String,
    @ColumnInfo(name = "character_id") val characterId: Long? = null, // null = user message, non-null = which character spoke
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "branch_id") val branchId: Long? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    // Swipe alternatives: JSON array of alternative content strings
    @ColumnInfo(name = "swipe_content", defaultValue = "[]") val swipeContent: String = "[]",
    @ColumnInfo(name = "swipe_index", defaultValue = "0") val swipeIndex: Int = 0,
    @ColumnInfo(name = "reply_to_id") val replyToId: Long? = null
)
