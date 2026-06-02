package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(name = "system_prompt", defaultValue = "")
    val systemPrompt: String = "",
    @ColumnInfo(name = "post_history_instructions", defaultValue = "")
    val postHistoryInstructions: String = "",
    @ColumnInfo(name = "author_note", defaultValue = "")
    val authorNote: String = "",
    @ColumnInfo(name = "is_default", defaultValue = "0")
    val isDefault: Boolean = false,
    @ColumnInfo(defaultValue = "global") val scope: String = "global",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
