package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sprites",
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
        Index(value = ["character_id", "display_order", "created_at"]),
        Index(value = ["character_id", "emotion"])
    ]
)
data class SpriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "character_id") val characterId: Long,
    @ColumnInfo(defaultValue = "neutral") val emotion: String = "neutral",
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "display_order", defaultValue = "0") val displayOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
