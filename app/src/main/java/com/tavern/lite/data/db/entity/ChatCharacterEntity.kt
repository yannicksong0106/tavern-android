package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_characters",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["character_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("chat_id"),
        Index("character_id"),
        Index("chat_id", "character_id", unique = true),
        Index(value = ["chat_id", "is_active", "display_order"])
    ]
)
data class ChatCharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "character_id") val characterId: Long,
    @ColumnInfo(name = "display_order", defaultValue = "0") val displayOrder: Int = 0,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "chattiness", defaultValue = "50") val chattiness: Int = 50,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
