package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "character_personas",
    primaryKeys = ["character_id", "persona_id"],
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["character_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["persona_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("character_id"), Index("persona_id")]
)
data class CharacterPersonaEntity(
    @ColumnInfo(name = "character_id") val characterId: Long,
    @ColumnInfo(name = "persona_id") val personaId: Long
)
