package com.tavern.lite.data.model

import kotlinx.serialization.Serializable

/**
 * 搜索引擎类型
 */
@Serializable
enum class SearchEngine { DUCKDUCKGO, BING, GOOGLE }

/**
 * 搜索设置
 */
@Serializable
data class WebSearchConfig(
    val enabled: Boolean = false,
    val engine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val apiKey: String = "",       // Bing/Google 需要
    val maxResults: Int = 5,
    val autoSearch: Boolean = false // 自动检测是否需要搜索
)
