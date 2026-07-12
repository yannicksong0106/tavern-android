package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SearchManager(
    private val scope: CoroutineScope
) {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    // 使用线程安全容器：incrementCacheVersion 可能由 messages Flow 在后台线程触发，
    // 而 searchMessages 由 UI 输入触发，两者会并发访问缓存。
    private val searchCacheVersion = AtomicInteger(0)
    private val _searchCache = ConcurrentHashMap<Pair<String, Int>, List<Int>>()

    lateinit var messagesProvider: () -> List<MessageEntity>

    fun incrementCacheVersion() {
        searchCacheVersion.incrementAndGet()
        _searchCache.clear()
    }

    fun searchMessages(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentSearchIndex.value = -1
            return
        }
        val lowerQuery = query.lowercase()
        val cacheKey = lowerQuery to searchCacheVersion.get()
        val results = _searchCache.getOrPut(cacheKey) {
            messagesProvider().mapIndexedNotNull { index, msg ->
                if (msg.content.lowercase().contains(lowerQuery)) index else null
            }
        }
        _searchResults.value = results
        _currentSearchIndex.value = if (results.isNotEmpty()) 0 else -1
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        _currentSearchIndex.value = (_currentSearchIndex.value + 1) % results.size
    }

    fun previousSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        _currentSearchIndex.value = (_currentSearchIndex.value - 1 + results.size) % results.size
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentSearchIndex.value = -1
    }
}
