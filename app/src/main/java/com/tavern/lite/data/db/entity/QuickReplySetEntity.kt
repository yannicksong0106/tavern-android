package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quick_reply_sets",
    indices = [
        Index(value = ["scope", "character_id", "chat_id", "enabled", "display_order"])
    ]
)
data class QuickReplySetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "global") val scope: String = "global",
    @ColumnInfo(name = "character_id") val characterId: Long? = null,
    @ColumnInfo(name = "chat_id") val chatId: Long? = null,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(name = "display_order", defaultValue = "0") val displayOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
