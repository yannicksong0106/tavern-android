package com.tavern.lite.network

import android.util.Log
import com.tavern.lite.data.model.SearchEngine
import com.tavern.lite.data.model.WebSearchConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebSearchServiceTest {

    private lateinit var client: OkHttpClient
    private lateinit var service: WebSearchService

    private val defaultConfig = WebSearchConfig(
        enabled = true,
        engine = SearchEngine.DUCKDUCKGO,
        maxResults = 5
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        client = mockk(relaxed = true)
        service = WebSearchService(client)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ==================== DuckDuckGo ====================

    @Test
    fun `searchDuckDuckGo returns results from abstract and related topics`() = runTest {
        val json = """
        {
            "Heading": "Test Topic",
            "Abstract": "This is a test abstract about the topic.",
            "AbstractURL": "https://example.com/test",
            "RelatedTopics": [
                {"Text": "Related topic 1", "FirstURL": "https://example.com/related1"},
                {"Text": "Related topic 2", "FirstURL": "https://example.com/related2"}
            ]
        }
        """.trimIndent()

        val call = mockk<Call>()
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.duckduckgo.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody())
            .build()
        every { client.newCall(any()) } returns call

        val results = service.search("test query", defaultConfig)

        assertEquals(3, results.size)
        assertEquals("Test Topic", results[0].title)
        assertTrue(results[0].snippet.contains("test abstract"))
        assertEquals("https://example.com/test", results[0].url)
        assertEquals("Related topic 1", results[1].title)
    }

    @Test
    fun `searchDuckDuckGo returns empty list on blank query`() = runTest {
        val results = service.search("", defaultConfig)
        assertEquals(0, results.size)
    }

    @Test
    fun `searchDuckDuckGo returns empty list on network error`() = runTest {
        val call = mockk<Call>()
        every { call.execute() } throws RuntimeException("Network error")
        every { client.newCall(any()) } returns call

        val results = service.search("test", defaultConfig)

        assertEquals(0, results.size)
    }

    @Test
    fun `searchDuckDuckGo rethrows CancellationException`() = runTest {
        val call = mockk<Call>()
        every { call.execute() } throws CancellationException("Cancelled")
        every { client.newCall(any()) } returns call

        var caught: Throwable? = null
        try {
            service.search("test", defaultConfig)
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(caught is CancellationException)
    }

    @Test
    fun `search respects maxResults limit`() = runTest {
        val topics = (1..10).joinToString(",") { i ->
            """{"Text": "Topic $i", "FirstURL": "https://example.com/$i"}"""
        }
        val json = """
        {
            "Heading": "Test",
            "Abstract": "",
            "AbstractURL": "",
            "RelatedTopics": [$topics]
        }
        """.trimIndent()

        val call = mockk<Call>()
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.duckduckgo.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody())
            .build()
        every { client.newCall(any()) } returns call

        val config = defaultConfig.copy(maxResults = 3)
        val results = service.search("test", config)

        assertEquals(3, results.size)
    }

    // ==================== Bing ====================

    @Test
    fun `searchBing returns empty when api key is blank`() = runTest {
        val config = WebSearchConfig(
            enabled = true,
            engine = SearchEngine.BING,
            apiKey = "",
            maxResults = 5
        )

        val results = service.search("test", config)

        assertEquals(0, results.size)
    }

    @Test
    fun `searchBing returns results from webPages`() = runTest {
        val json = """
        {
            "webPages": {
                "value": [
                    {"name": "Result 1", "snippet": "Snippet 1", "url": "https://example.com/1"},
                    {"name": "Result 2", "snippet": "Snippet 2", "url": "https://example.com/2"}
                ]
            }
        }
        """.trimIndent()

        val call = mockk<Call>()
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.bing.microsoft.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody())
            .build()
        every { client.newCall(any()) } returns call

        val config = WebSearchConfig(
            enabled = true,
            engine = SearchEngine.BING,
            apiKey = "test-key",
            maxResults = 5
        )

        val results = service.search("test", config)

        assertEquals(2, results.size)
        assertEquals("Result 1", results[0].title)
        assertEquals("Snippet 1", results[0].snippet)
        assertEquals("https://example.com/1", results[0].url)
    }

    // ==================== Google ====================

    @Test
    fun `searchGoogle returns empty when api key is blank`() = runTest {
        val config = WebSearchConfig(
            enabled = true,
            engine = SearchEngine.GOOGLE,
            apiKey = "",
            maxResults = 5
        )

        val results = service.search("test", config)

        assertEquals(0, results.size)
    }

    @Test
    fun `searchGoogle returns empty when cx is missing`() = runTest {
        val config = WebSearchConfig(
            enabled = true,
            engine = SearchEngine.GOOGLE,
            apiKey = "only-key-no-cx",
            maxResults = 5
        )

        val results = service.search("test", config)

        assertEquals(0, results.size)
    }

    @Test
    fun `searchGoogle returns results from items`() = runTest {
        val json = """
        {
            "items": [
                {
                    "title": "Google Result 1",
                    "snippet": "Google snippet 1",
                    "link": "https://example.com/g1",
                    "pagemap": {"metatags": [{"og:description": "OG desc"}]}
                },
                {
                    "title": "Google Result 2",
                    "snippet": "Google snippet 2",
                    "link": "https://example.com/g2"
                }
            ]
        }
        """.trimIndent()

        val call = mockk<Call>()
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://googleapis.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody())
            .build()
        every { client.newCall(any()) } returns call

        val config = WebSearchConfig(
            enabled = true,
            engine = SearchEngine.GOOGLE,
            apiKey = "api-key:cx-id",
            maxResults = 5
        )

        val results = service.search("test", config)

        assertEquals(2, results.size)
        assertEquals("Google Result 1", results[0].title)
        assertEquals("OG desc", results[0].snippet)
        assertEquals("https://example.com/g1", results[0].url)
        assertEquals("Google snippet 2", results[1].snippet)
    }

    // ==================== Cache ====================

    @Test
    fun `search returns cached results on second call`() = runTest {
        val json = """
        {
            "Heading": "Cached Topic",
            "Abstract": "Cached abstract",
            "AbstractURL": "https://example.com/cached",
            "RelatedTopics": []
        }
        """.trimIndent()

        val call = mockk<Call>()
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.duckduckgo.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody())
            .build()
        every { client.newCall(any()) } returns call

        // First call
        val results1 = service.search("cached query", defaultConfig)
        // Second call (should use cache)
        val results2 = service.search("cached query", defaultConfig)

        assertEquals(results1.size, results2.size)
        assertEquals("Cached Topic", results2[0].title)
        // Verify network was only called once
        verify(exactly = 1) { client.newCall(any()) }
    }

    @Test
    fun `clearCache invalidates cached results`() = runTest {
        val json = """
        {
            "Heading": "Topic",
            "Abstract": "Abstract",
            "AbstractURL": "https://example.com",
            "RelatedTopics": []
        }
        """.trimIndent()

        val call = mockk<Call>()
        every { call.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.duckduckgo.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody())
            .build()
        every { client.newCall(any()) } returns call

        service.search("query", defaultConfig)
        service.clearCache()
        service.search("query", defaultConfig)

        // Network called twice after cache clear
        verify(exactly = 2) { client.newCall(any()) }
    }
}
