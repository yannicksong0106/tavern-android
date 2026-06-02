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
    @ColumnInfo(defaultValue = "") val name: String = "",
    @ColumnInfo(defaultValue = "") val comment: String = "",
    @ColumnInfo(name = "script_type", defaultValue = "0") val scriptType: Int = 0,
    @ColumnInfo(name = "find_pattern", defaultValue = "") val findPattern: String = "",
    @ColumnInfo(name = "replace_pattern", defaultValue = "") val replacePattern: String = "",
    @ColumnInfo(name = "is_regex", defaultValue = "1") val isRegex: Boolean = true,
    @ColumnInfo(name = "case_sensitive", defaultValue = "0") val caseSensitive: Boolean = false,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0
)
