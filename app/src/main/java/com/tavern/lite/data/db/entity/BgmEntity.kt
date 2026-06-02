package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 角色背景音乐实体
 * 存储角色配置的背景音乐信息
 */
@Entity(
    tableName = "bgms",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["character_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("character_id")]
)
data class BgmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "character_id") val characterId: Long,
    @ColumnInfo(defaultValue = "") val name: String = "",
    @ColumnInfo(name = "audio_path") val audioPath: String,
    @ColumnInfo(name = "loop", defaultValue = "1") val loop: Boolean = true,
    @ColumnInfo(name = "volume", defaultValue = "0.5") val volume: Float = 0.5f,
    @ColumnInfo(name = "display_order", defaultValue = "0") val displayOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
