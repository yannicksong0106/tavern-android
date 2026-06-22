package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "world_book_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorldBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["world_book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("world_book_id"), Index(value = ["world_book_id", "disabled"], name = "index_world_book_entries_active")]
)
data class WorldBookEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "world_book_id") val worldBookId: Long,
    @ColumnInfo(defaultValue = "0") val uid: Int = 0,
    @ColumnInfo(defaultValue = "") val comment: String = "",
    @ColumnInfo(defaultValue = "[]") val keys: String = "[]",
    @ColumnInfo(name = "keys_secondary", defaultValue = "[]") val keysSecondary: String = "[]",
    @ColumnInfo(defaultValue = "") val content: String = "",
    @ColumnInfo(defaultValue = "0") val constant: Boolean = false,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    @ColumnInfo(name = "order_val", defaultValue = "100") val orderVal: Int = 100,
    @ColumnInfo(defaultValue = "100") val probability: Int = 100,
    @ColumnInfo(defaultValue = "4") val depth: Int = 4,
    @ColumnInfo(defaultValue = "0") val disabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val selective: Boolean = false,
    @ColumnInfo(name = "selective_logic", defaultValue = "0") val selectiveLogic: Int = 0,
    @ColumnInfo(name = "exclude_recursion", defaultValue = "0") val excludeRecursion: Boolean = false,
    @ColumnInfo(name = "prevent_recursion", defaultValue = "0") val preventRecursion: Boolean = false,
    @ColumnInfo(defaultValue = "") val group: String = "",
    @ColumnInfo(name = "group_override", defaultValue = "0") val groupOverride: Boolean = false,
    @ColumnInfo(name = "group_weight", defaultValue = "100") val groupWeight: Int = 100
)
