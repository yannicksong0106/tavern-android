package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val biography: String = "",
    @ColumnInfo(name = "avatar_path") val avatarPath: String? = null,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
