package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchManager(
    private val scope: CoroutineScope
) {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    private var searchCacheVersion = 0
    private val _searchCache = mutableMapOf<Pair<String, Int>, List<Int>>()

    lateinit var messagesProvider: () -> List<MessageEntity>

    fun incrementCacheVersion() {
        searchCacheVersion++
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
        val cacheKey = lowerQuery to searchCacheVersion
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
