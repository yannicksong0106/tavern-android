package com.tavern.lite.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * API 配置档案实体
 * 支持多套 API 配置，可按角色/聊天绑定
 */
@Entity(tableName = "api_config_profiles")
data class ApiConfigProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 档案名称 */
    val name: String,

    /** 档案描述 */
    @ColumnInfo(defaultValue = "") val description: String = "",

    /** 配置 JSON（加密存储） */
    @ColumnInfo(name = "config_json") val configJson: String,

    /** 是否为默认档案 */
    @ColumnInfo(name = "is_default", defaultValue = "0") val isDefault: Boolean = false,

    /** 绑定的角色 ID（可选，null 表示不绑定特定角色） */
    @ColumnInfo(name = "bound_character_id") val boundCharacterId: Long? = null,

    /** 绑定的聊天 ID（可选，null 表示不绑定特定聊天） */
    @ColumnInfo(name = "bound_chat_id") val boundChatId: Long? = null,

    /** 档案优先级（数值越小优先级越高，用于角色/聊天绑定冲突解决） */
    @ColumnInfo(defaultValue = "100") val priority: Int = 100,

    /** 创建时间 */
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),

    /** 更新时间 */
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
