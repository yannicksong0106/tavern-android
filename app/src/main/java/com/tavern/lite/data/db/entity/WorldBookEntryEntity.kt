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
    indices = [Index("world_book_id")]
)
data class WorldBookEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "world_book_id") val worldBookId: Long,
    val uid: Int = 0,
    val comment: String = "",
    val keys: String = "[]",           // JSON array
    @ColumnInfo(name = "keys_secondary") val keysSecondary: String = "[]",
    val content: String = "",
    val constant: Boolean = false,
    val position: Int = 0,
    @ColumnInfo(name = "order_val") val orderVal: Int = 100,
    val probability: Int = 100,
    val depth: Int = 4,
    val disabled: Boolean = false,
    // v3 高级逻辑
    val selective: Boolean = false,
    @ColumnInfo(name = "selective_logic") val selectiveLogic: Int = 0, // 0=AND, 1=OR, 2=NOT
    @ColumnInfo(name = "exclude_recursion") val excludeRecursion: Boolean = false,
    @ColumnInfo(name = "prevent_recursion") val preventRecursion: Boolean = false,
    val group: String = "",
    @ColumnInfo(name = "group_override") val groupOverride: Boolean = false,
    @ColumnInfo(name = "group_weight") val groupWeight: Int = 100
)
