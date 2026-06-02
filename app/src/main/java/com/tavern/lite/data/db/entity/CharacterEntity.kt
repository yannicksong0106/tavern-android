package com.tavern.lite.data.db.entity

import androidx.compose.runtime.Stable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Stable
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "") val personality: String = "",
    @ColumnInfo(name = "first_mes", defaultValue = "") val firstMes: String = "",
    @ColumnInfo(name = "mes_example", defaultValue = "") val mesExample: String = "",
    @ColumnInfo(name = "avatar_path") val avatarPath: String? = null,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String? = null,
    @ColumnInfo(name = "post_history_instructions") val postHistoryInstructions: String? = null,
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
    @ColumnInfo(name = "world_book_id") val worldBookId: Long? = null,
    @ColumnInfo(name = "preset_id") val presetId: Long? = null,
    @ColumnInfo(name = "background_path") val backgroundPath: String? = null,
    @ColumnInfo(name = "chattiness", defaultValue = "50") val chattiness: Int = 50,
    @ColumnInfo(defaultValue = "") val creator: String = "",
    @ColumnInfo(defaultValue = "1.0") val version: String = "1.0",
    @ColumnInfo(defaultValue = "chara_card_v2") val spec: String = "chara_card_v2",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
