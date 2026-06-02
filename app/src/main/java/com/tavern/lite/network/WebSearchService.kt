package com.tavern.lite.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索引擎类型
 */
@Serializable
enum class SearchEngine { DUCKDUCKGO, BING, GOOGLE }

/**
 * 搜索结果条目
 */
data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String
)

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

/**
 * Web 搜索服务，支持 DuckDuckGo (免费无需 API key)、Bing、Google
 * 内置 LRU 缓存避免重复搜索
 */
@Singleton
class WebSearchService @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "WebSearchService"
        private const val MAX_CACHE_SIZE = 100
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    }

    // LRU 缓存: query -> Pair(timestamp, results)
    private val cache = object : ConcurrentHashMap<String, Pair<Long, List<WebSearchResult>>>() {
        // evict oldest when over capacity
        fun evictIfNeeded() {
            if (size > MAX_CACHE_SIZE) {
                val oldest = entries.minByOrNull { it.value.first }?.key
                oldest?.let { remove(it) }
            }
        }
    }

    /**
     * 执行搜索
     */
    suspend fun search(query: String, config: WebSearchConfig): List<WebSearchResult> {
        if (query.isBlank()) return emptyList()

        // Check cache
        val cached = cache[query]
        if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
            Log.d(TAG, "Cache hit for: $query")
            return cached.second
        }

        val results = when (config.engine) {
            SearchEngine.DUCKDUCKGO -> searchDuckDuckGo(query, config.maxResults)
            SearchEngine.BING -> searchBing(query, config.apiKey, config.maxResults)
            SearchEngine.GOOGLE -> searchGoogle(query, config.apiKey, config.maxResults)
        }

        // Update cache
        if (results.isNotEmpty()) {
            cache[query] = System.currentTimeMillis() to results
            cache.evictIfNeeded()
        }

        return results
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * DuckDuckGo Instant Answer API (免费，无需 API key)
     */
    private fun searchDuckDuckGo(query: String, maxResults: Int): List<WebSearchResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder().url(url).header("User-Agent", "TavernAndroid/1.0").build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)

            val results = mutableListOf<WebSearchResult>()

            // Abstract (main answer)
            val abstract = json.optString("Abstract", "")
            val abstractUrl = json.optString("AbstractURL", "")
            if (abstract.isNotBlank()) {
                results.add(WebSearchResult(
                    title = json.optString("Heading", query),
                    snippet = abstract.take(500),
                    url = abstractUrl
                ))
            }

            // Related topics
            val topics = json.optJSONArray("RelatedTopics") ?: return results.take(maxResults)
            for (i in 0 until topics.length()) {
                if (results.size >= maxResults) break
                val topic = topics.optJSONObject(i) ?: continue
                val text = topic.optString("Text", "")
                val firstUrl = topic.optJSONObject("FirstURL")?.optString("URL", "")
                    ?: topic.optString("FirstURL", "")
                if (text.isNotBlank() && firstUrl.isNotBlank()) {
                    results.add(WebSearchResult(
                        title = text.take(100),
                        snippet = text.take(500),
                        url = firstUrl
                    ))
                }
            }

            results.take(maxResults)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "DuckDuckGo search failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Bing Web Search API (需要 API key)
     */
    private fun searchBing(query: String, apiKey: String, maxResults: Int): List<WebSearchResult> {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Bing API key not configured")
            return emptyList()
        }
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.bing.microsoft.com/v7.0/search?q=$encoded&count=$maxResults&mkt=zh-CN"
            val request = Request.Builder()
                .url(url)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("User-Agent", "TavernAndroid/1.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val webPages = json.optJSONObject("webPages")
                ?.optJSONArray("value") ?: return emptyList()

            (0 until webPages.length()).take(maxResults).mapNotNull { i ->
                val item = webPages.optJSONObject(i) ?: return@mapNotNull null
                WebSearchResult(
                    title = item.optString("name", ""),
                    snippet = item.optString("snippet", "").take(500),
                    url = item.optString("url", "")
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Bing search failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Google Custom Search API (需要 API key + CX)
     * apiKey 格式: "API_KEY:CX_ID"
     */
    private fun searchGoogle(query: String, apiKeyCx: String, maxResults: Int): List<WebSearchResult> {
        if (apiKeyCx.isBlank()) {
            Log.w(TAG, "Google API key not configured")
            return emptyList()
        }
        return try {
            val parts = apiKeyCx.split(":", limit = 2)
            val key = parts[0]
            val cx = parts.getOrElse(1) { "" }
            if (cx.isBlank()) {
                Log.w(TAG, "Google CX not configured (format: API_KEY:CX_ID)")
                return emptyList()
            }

            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/customsearch/v1?key=$key&cx=$cx&q=$encoded&num=$maxResults"
            val request = Request.Builder().url(url).header("User-Agent", "TavernAndroid/1.0").build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: return emptyList()

            (0 until items.length()).take(maxResults).mapNotNull { i ->
                val item = items.optJSONObject(i) ?: return@mapNotNull null
                WebSearchResult(
                    title = item.optString("title", ""),
                    snippet = item.optJSONObject("pagemap")
                        ?.optJSONArray("metatags")
                        ?.optJSONObject(0)
                        ?.optString("og:description", "")
                        ?: item.optString("snippet", "").take(500),
                    url = item.optString("link", "")
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Google search failed: ${e.message}")
            emptyList()
        }
    }
}
