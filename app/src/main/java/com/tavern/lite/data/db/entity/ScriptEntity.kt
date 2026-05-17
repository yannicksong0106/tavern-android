package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scripts",
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
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "character_id") val characterId: Long,
    val name: String = "",
    val comment: String = "",
    // 0 = 在用户消息上执行, 1 = 在 AI 回复上执行, 2 = 在两者上执行
    @ColumnInfo(name = "script_type") val scriptType: Int = 0,
    @ColumnInfo(name = "find_pattern") val findPattern: String = "",
    @ColumnInfo(name = "replace_pattern") val replacePattern: String = "",
    // 是否使用正则表达式（false = 字面量替换）
    @ColumnInfo(name = "is_regex") val isRegex: Boolean = true,
    // 是否大小写敏感
    @ColumnInfo(name = "case_sensitive") val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)
