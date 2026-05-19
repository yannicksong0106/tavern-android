package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val personality: String = "",
    @ColumnInfo(name = "first_mes") val firstMes: String = "",
    @ColumnInfo(name = "mes_example") val mesExample: String = "",
    @ColumnInfo(name = "avatar_path") val avatarPath: String? = null,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String? = null,
    @ColumnInfo(name = "post_history_instructions") val postHistoryInstructions: String? = null,
    val tags: String = "[]", // JSON array
    @ColumnInfo(name = "world_book_id") val worldBookId: Long? = null,
    @ColumnInfo(name = "background_path") val backgroundPath: String? = null,
    @ColumnInfo(name = "chattiness") val chattiness: Int = 50,  // 0-100, 健谈度
    val creator: String = "",
    val version: String = "1.0",
    val spec: String = "chara_card_v2",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
