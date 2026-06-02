package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_atoms",
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
        Index("category"),
        Index("character_id", "category"),
        Index("character_id", "superseded"),
        Index("character_id", "superseded", "category"),
        Index("character_id", "superseded", "source"),
        Index(value = ["character_id", "superseded", "category", "importance"], orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.ASC, Index.Order.DESC])
    ]
)
data class MemoryAtomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "character_id") val characterId: Long,
    val content: String,
    val category: String,         // fact, emotion, preference, event, habit, character_consistency, temporary
    @ColumnInfo(defaultValue = "5") val importance: Int = 5,
    @ColumnInfo(defaultValue = "llm") val source: String = "llm",
    @ColumnInfo(name = "source_chat_id") val sourceChatId: Long? = null,
    @ColumnInfo(name = "source_message_id") val sourceMessageId: Long? = null,
    @ColumnInfo(defaultValue = "0") val superseded: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed") val lastAccessed: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "access_count", defaultValue = "0") val accessCount: Int = 0,
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null
)
