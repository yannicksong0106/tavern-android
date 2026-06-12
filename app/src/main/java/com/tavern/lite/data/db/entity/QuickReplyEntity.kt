package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quick_replies",
    foreignKeys = [
        ForeignKey(
            entity = QuickReplySetEntity::class,
            parentColumns = ["id"],
            childColumns = ["set_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("set_id"),
        Index(value = ["set_id", "enabled", "display_order"])
    ]
)
data class QuickReplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "set_id") val setId: Long,
    val label: String,
    val script: String,
    val icon: String? = null,
    @ColumnInfo(name = "automation_id") val automationId: String? = null,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(name = "requires_confirmation", defaultValue = "0") val requiresConfirmation: Boolean = false,
    @ColumnInfo(name = "allow_auto_run", defaultValue = "0") val allowAutoRun: Boolean = false,
    @ColumnInfo(name = "can_send_messages", defaultValue = "0") val canSendMessages: Boolean = false,
    @ColumnInfo(name = "can_trigger_generation", defaultValue = "0") val canTriggerGeneration: Boolean = false,
    @ColumnInfo(name = "display_order", defaultValue = "0") val displayOrder: Int = 0
)
