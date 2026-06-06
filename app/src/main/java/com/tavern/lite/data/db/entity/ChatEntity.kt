package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["character_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("character_id"),
        Index("is_group"),
        Index("updated_at"),
        Index(value = ["character_id", "updated_at"], orders = [Index.Order.ASC, Index.Order.DESC]),
        Index(value = ["is_group", "updated_at"], orders = [Index.Order.ASC, Index.Order.DESC])
    ]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "character_id") val characterId: Long,
    val name: String? = null,
    @ColumnInfo(name = "is_group", defaultValue = "0") val isGroup: Boolean = false,
    @ColumnInfo(name = "group_chattiness", defaultValue = "50") val groupChattiness: Int = 50,
    @ColumnInfo(name = "background_path") val backgroundPath: String? = null,
    @ColumnInfo(name = "preset_id") val presetId: Long? = null,
    @ColumnInfo(name = "scheduling_strategy", defaultValue = "natural") val schedulingStrategy: String = "natural",
    @ColumnInfo(name = "message_interval_ms", defaultValue = "1500") val messageIntervalMs: Long = 1500L,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
