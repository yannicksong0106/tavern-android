package com.tavern.lite.domain.model

/**
 * 网络搜索结果 — Domain 层数据模型
 */
data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String
)
