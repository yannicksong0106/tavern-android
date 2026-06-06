package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.MessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchManagerTest {

    private lateinit var manager: SearchManager
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    private val messages = listOf(
        MessageEntity(id = 1, chatId = 1, role = "user", content = "Hello World"),
        MessageEntity(id = 2, chatId = 1, role = "assistant", content = "Hi there!"),
        MessageEntity(id = 3, chatId = 1, role = "user", content = "How are you?"),
        MessageEntity(id = 4, chatId = 1, role = "assistant", content = "I am doing well, hello!"),
        MessageEntity(id = 5, chatId = 1, role = "user", content = "Goodbye")
    )

    @Before
    fun setup() {
        testScope = TestScope(testDispatcher)
        manager = SearchManager(testScope)
        manager.messagesProvider = { messages }
    }

    // ==================== searchMessages ====================

    @Test
    fun `searchMessages finds case-insensitive matches`() {
        manager.searchMessages("hello")
        assertEquals(listOf(0, 3), manager.searchResults.value)
        assertEquals(0, manager.currentSearchIndex.value)
    }

    @Test
    fun `searchMessages finds exact case match`() {
        manager.searchMessages("Hello")
        assertEquals(listOf(0, 3), manager.searchResults.value)
    }

    @Test
    fun `searchMessages returns empty for no matches`() {
        manager.searchMessages("xyz")
        assertTrue(manager.searchResults.value.isEmpty())
        assertEquals(-1, manager.currentSearchIndex.value)
    }

    @Test
    fun `searchMessages clears results for blank query`() {
        manager.searchMessages("hello")
        manager.searchMessages("")
        assertTrue(manager.searchResults.value.isEmpty())
        assertEquals(-1, manager.currentSearchIndex.value)
    }

    @Test
    fun `searchMessages clears results for whitespace query`() {
        manager.searchMessages("hello")
        manager.searchMessages("   ")
        assertTrue(manager.searchResults.value.isEmpty())
        assertEquals(-1, manager.currentSearchIndex.value)
    }

    @Test
    fun `searchMessages sets query`() {
        manager.searchMessages("test")
        assertEquals("test", manager.searchQuery.value)
    }

    @Test
    fun `searchMessages with empty message list`() {
        manager.messagesProvider = { emptyList() }
        manager.searchMessages("hello")
        assertTrue(manager.searchResults.value.isEmpty())
        assertEquals(-1, manager.currentSearchIndex.value)
    }

    // ==================== Navigation ====================

    @Test
    fun `nextSearchResult wraps around`() {
        manager.searchMessages("hello")
        assertEquals(0, manager.currentSearchIndex.value)
        manager.nextSearchResult()
        assertEquals(1, manager.currentSearchIndex.value)
        manager.nextSearchResult()
        assertEquals(0, manager.currentSearchIndex.value)
    }

    @Test
    fun `previousSearchResult wraps around`() {
        manager.searchMessages("hello")
        assertEquals(0, manager.currentSearchIndex.value)
        manager.previousSearchResult()
        assertEquals(1, manager.currentSearchIndex.value)
        manager.previousSearchResult()
        assertEquals(0, manager.currentSearchIndex.value)
    }

    @Test
    fun `nextSearchResult does nothing when no results`() {
        manager.searchMessages("xyz")
        manager.nextSearchResult()
        assertEquals(-1, manager.currentSearchIndex.value)
    }

    @Test
    fun `previousSearchResult does nothing when no results`() {
        manager.searchMessages("xyz")
        manager.previousSearchResult()
        assertEquals(-1, manager.currentSearchIndex.value)
    }

    // ==================== clearSearch ====================

    @Test
    fun `clearSearch resets all state`() {
        manager.searchMessages("hello")
        manager.nextSearchResult()
        manager.clearSearch()
        assertEquals("", manager.searchQuery.value)
        assertTrue(manager.searchResults.value.isEmpty())
        assertEquals(-1, manager.currentSearchIndex.value)
    }

    // ==================== Cache ====================

    @Test
    fun `searchMessages caches results for same query`() {
        manager.searchMessages("hello")
        val firstResults = manager.searchResults.value
        manager.searchMessages("hello")
        val secondResults = manager.searchResults.value
        assertEquals(firstResults, secondResults)
    }

    @Test
    fun `incrementCacheVersion invalidates cache`() {
        manager.searchMessages("hello")
        val oldResults = manager.searchResults.value
        manager.messagesProvider = {
            listOf(MessageEntity(id = 10, chatId = 1, role = "user", content = "hello world hello"))
        }
        // Same query, cached result
        manager.searchMessages("hello")
        assertEquals(oldResults, manager.searchResults.value)

        // Invalidate cache
        manager.incrementCacheVersion()
        manager.searchMessages("hello")
        assertEquals(listOf(0), manager.searchResults.value)
    }

    @Test
    fun `searchMessages finds single match`() {
        manager.searchMessages("goodbye")
        assertEquals(listOf(4), manager.searchResults.value)
        assertEquals(0, manager.currentSearchIndex.value)
    }
}
