package com.tavern.lite.domain.port

import com.tavern.lite.data.model.ApiConfig

/**
 * 旧版 API 配置读取端口 — 用于迁移场景
 * 由 network 层 ApiConfigStore 实现
 */
interface LegacyConfigReaderPort {
    suspend fun readConfig(): ApiConfig
}
