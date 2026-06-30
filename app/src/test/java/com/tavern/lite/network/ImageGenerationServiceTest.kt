package com.tavern.lite.network

import android.content.Context
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64

class ImageGenerationServiceTest {

    private lateinit var client: OkHttpClient
    private lateinit var context: Context
    private lateinit var service: ImageGenerationService

    @Before
    fun setup() {
        client = mockk()
        context = mockk(relaxed = true)
        service = ImageGenerationService(client, context)
    }

    @Test
    fun `generateImage returns null for Claude provider`() = runTest {
        val config = ApiConfig(provider = ApiProvider.Claude(
            baseUrl = "https://api.anthropic.com",
            apiKey = "test-key",
            model = "claude-3"
        ))

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage writes Gemini native inline image to file`() = runTest {
        val config = ApiConfig(provider = ApiProvider.Gemini(
            apiKey = "test-key",
            model = "gemini-3.1-flash-image"
        ))
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_gemini_native_images")
        tempDir.mkdirs()
        every { context.filesDir } returns tempDir

        val imageBytes = ByteArray(12) { (it + 1).toByte() }
        val responseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "inlineData": {
                          "mimeType": "image/png",
                          "data": "${Base64.getEncoder().encodeToString(imageBytes)}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val response = Response.Builder()
            .request(Request.Builder().url("https://generativelanguage.googleapis.com/v1/models/gemini-3.1-flash-image:generateContent").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody.toResponseBody("application/json".toMediaType()))
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns response
        every { client.newCall(any()) } returns call

        val result = service.generateImage("a glowing tavern", config, "1792x1024")

        assertNotNull(result)
        val file = File(result!!)
        assertTrue(file.exists())
        assertEquals(imageBytes.size.toLong(), file.length())
        verify {
            client.newCall(match {
                it.url.toString() == "https://generativelanguage.googleapis.com/v1/models/gemini-3.1-flash-image:generateContent" &&
                    it.header("x-goog-api-key") == "test-key" &&
                    requestBody(it).contains("\"aspectRatio\":\"16:9\"")
            })
        }

        file.delete()
        file.parentFile?.delete()
        tempDir.delete()
    }

    @Test
    fun `generateImage writes Imagen prediction image to file`() = runTest {
        val config = ApiConfig(provider = ApiProvider.Gemini(
            apiKey = "test-key",
            model = "imagen-4.0-generate-preview-06-06"
        ))
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_imagen_images")
        tempDir.mkdirs()
        every { context.filesDir } returns tempDir

        val imageBytes = ByteArray(8) { (it * 2).toByte() }
        val responseBody = """
            {
              "predictions": [
                {
                  "bytesBase64Encoded": "${Base64.getEncoder().encodeToString(imageBytes)}"
                }
              ]
            }
        """.trimIndent()
        val response = Response.Builder()
            .request(Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/imagen-4.0-generate-preview-06-06:predict").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody.toResponseBody("application/json".toMediaType()))
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns response
        every { client.newCall(any()) } returns call

        val result = service.generateImage("a moonlit inn", config, "1024x1792")

        assertNotNull(result)
        val file = File(result!!)
        assertTrue(file.exists())
        assertEquals(imageBytes.size.toLong(), file.length())
        verify {
            client.newCall(match {
                it.url.toString() == "https://generativelanguage.googleapis.com/v1beta/models/imagen-4.0-generate-preview-06-06:predict" &&
                    it.header("x-goog-api-key") == "test-key" &&
                    requestBody(it).contains("\"aspectRatio\":\"9:16\"")
            })
        }

        file.delete()
        file.parentFile?.delete()
        tempDir.delete()
    }

    @Test
    fun `generateImage returns null on API error`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val errorResponse = Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/images/generations").build())
            .protocol(Protocol.HTTP_1_1)
            .code(400)
            .message("Bad Request")
            .body("{\"error\": {\"message\": \"Invalid request\"}}".toResponseBody("application/json".toMediaType()))
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns errorResponse
        every { client.newCall(any()) } returns call

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage returns null on empty response`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val successResponse = Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/images/generations").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(null)
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns successResponse
        every { client.newCall(any()) } returns call

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage returns null when no data in response`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val successResponse = Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/images/generations").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns successResponse
        every { client.newCall(any()) } returns call

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage returns null when data array is empty`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val successResponse = Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/images/generations").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{\"data\": []}".toResponseBody("application/json".toMediaType()))
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns successResponse
        every { client.newCall(any()) } returns call

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage returns null when no url in response`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val successResponse = Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/images/generations").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{\"data\": [{}]}".toResponseBody("application/json".toMediaType()))
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns successResponse
        every { client.newCall(any()) } returns call

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage returns null when download fails`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val successResponse = Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/images/generations").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{\"data\": [{\"url\": \"https://example.com/image.png\"}]}".toResponseBody("application/json".toMediaType()))
            .build()

        val downloadResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/image.png").build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()

        val apiCall = mockk<Call>()
        val downloadCall = mockk<Call>()
        every { apiCall.execute() } returns successResponse
        every { downloadCall.execute() } returns downloadResponse
        every { client.newCall(match { it.url.toString().contains("images/generations") }) } returns apiCall
        every { client.newCall(match { it.url.toString().contains("example.com") }) } returns downloadCall

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage returns file path on success`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_images")
        tempDir.mkdirs()
        every { context.filesDir } returns tempDir.parentFile!!

        val successResponse = Response.Builder()
            .request(Request.Builder().url("https://api.openai.com/v1/images/generations").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{\"data\": [{\"url\": \"https://example.com/image.png\"}]}".toResponseBody("application/json".toMediaType()))
            .build()

        val imageBytes = ByteArray(10) { it.toByte() }
        val downloadResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com/image.png").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(imageBytes.toResponseBody("image/png".toMediaType()))
            .build()

        val apiCall = mockk<Call>()
        val downloadCall = mockk<Call>()
        every { apiCall.execute() } returns successResponse
        every { downloadCall.execute() } returns downloadResponse
        every { client.newCall(match { it.url.toString().contains("images/generations") }) } returns apiCall
        every { client.newCall(match { it.url.toString().contains("example.com") }) } returns downloadCall

        val result = service.generateImage("test prompt", config)

        assertNotNull(result)
        val file = File(result!!)
        assert(file.exists())
        assertEquals(imageBytes.size.toLong(), file.length())

        // Cleanup
        file.delete()
        tempDir.delete()
    }

    @Test
    fun `generateImage handles network exception`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val call = mockk<Call>()
        every { call.execute() } throws RuntimeException("Network error")
        every { client.newCall(any()) } returns call

        val result = service.generateImage("test prompt", config)

        assertNull(result)
    }

    @Test
    fun `generateImage handles CancellationException`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4"
        ))

        val call = mockk<Call>()
        every { call.execute() } throws kotlinx.coroutines.CancellationException()
        every { client.newCall(any()) } returns call

        try {
            service.generateImage("test prompt", config)
            assert(false) { "Should have thrown CancellationException" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected
        }
    }

    @Test
    fun `generateImage returns null for Ollama provider`() = runTest {
        val config = ApiConfig(provider = ApiProvider.Ollama())
        assertNull(service.generateImage("test", config))
    }

    @Test
    fun `generateImage returns null for KoboldAI provider`() = runTest {
        val config = ApiConfig(provider = ApiProvider.KoboldAI())
        assertNull(service.generateImage("test", config))
    }

    @Test
    fun `generateImage returns null for OpenRouter provider`() = runTest {
        val config = ApiConfig(provider = ApiProvider.OpenRouter())
        assertNull(service.generateImage("test", config))
    }

    @Test
    fun `generateImage returns null for Custom provider`() = runTest {
        val config = ApiConfig(provider = ApiProvider.Custom())
        assertNull(service.generateImage("test", config))
    }

    private fun requestBody(request: Request): String {
        val buffer = okio.Buffer()
        request.body!!.writeTo(buffer)
        return buffer.readUtf8()
    }
}
