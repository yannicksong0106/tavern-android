package com.tavern.lite.network

import com.tavern.lite.data.model.WebSearchConfig
import com.tavern.lite.domain.model.WebSearchResult
import com.tavern.lite.domain.port.WebSearchPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSearchService 适配器 — 将 domain 层 WebSearchPort 调用委托给 network 层 WebSearchService
 */
@Singleton
class WebSearchServiceAdapter @Inject constructor(
    private val delegate: WebSearchService
) : WebSearchPort {

    override suspend fun search(query: String, config: WebSearchConfig): List<WebSearchResult> {
        return delegate.search(query, config)
    }
}
