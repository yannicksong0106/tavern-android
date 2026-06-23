package com.tavern.lite.domain.port

import com.tavern.lite.data.model.WebSearchConfig
import com.tavern.lite.domain.model.WebSearchResult

/**
 * Web 搜索服务接口 — Domain 层端口
 * 由 network 层实现
 */
interface WebSearchPort {
    suspend fun search(query: String, config: WebSearchConfig): List<WebSearchResult>
}
