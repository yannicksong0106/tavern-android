package com.tavern.lite.data.db.entity

import androidx.compose.runtime.Stable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Stable
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
    indices = [
        // chat_id 单列索引被复合 index_messages_chat_active_created 前缀覆盖，FK 用它即可，无需独立索引。
        // parent_id / character_id 无任何 @Query 过滤或 JOIN 使用（全库核验），纯占写入维护成本。
        // 三者删除随 MIGRATION_33_34 DROP INDEX（X3 审计 Med，messages 是最热写路径）。
        Index("branch_id"),
        Index(value = ["chat_id", "is_active", "created_at"], name = "index_messages_chat_active_created"),
        Index(value = ["chat_id", "is_active", "is_pinned"], name = "index_messages_chat_active_pinned")
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "chat_id") val chatId: Long,
    val role: String, // "user" / "assistant" / "system"
    val content: String,
    @ColumnInfo(name = "character_id") val characterId: Long? = null, // null = user message, non-null = which character spoke
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "branch_id") val branchId: Long? = null,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    // Swipe alternatives: JSON array of alternative content strings
    @ColumnInfo(name = "swipe_content", defaultValue = "[]") val swipeContent: String = "[]",
    @ColumnInfo(name = "swipe_index", defaultValue = "0") val swipeIndex: Int = 0,
    @ColumnInfo(name = "reply_to_id") val replyToId: Long? = null,
    @ColumnInfo(name = "is_pinned", defaultValue = "0") val isPinned: Boolean = false,
    // 图片附件路径列表（JSON array of file paths）
    @ColumnInfo(name = "image_paths", defaultValue = "[]") val imagePaths: String = "[]"
)
