package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "author_notes",
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
data class AuthorNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "character_id") val characterId: Long,
    @ColumnInfo(defaultValue = "") val content: String = "",
    // Position: "before_an" = before author's note, "after_an" = after author's note
    @ColumnInfo(name = "position", defaultValue = "after_an") val position: String = "after_an",
    // Insertion depth: 0 = at the very end, N = N messages from the end
    @ColumnInfo(name = "depth", defaultValue = "4") val depth: Int = 4,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
