package com.tavern.lite.util

import android.content.Context
import com.tavern.lite.data.repository.ChatRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatImporterTest {

    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var context: Context

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var importer: ChatImporter
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)
        importer = ChatImporter(context, chatRepository, json)
        tempDir = createTempDir("chat_importer_test")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun writeFile(name: String, content: String): File {
        return File(tempDir, name).apply { writeText(content) }
    }

    // ==================== Format detection ====================

    @Test
    fun `importChat rejects unsupported format`() = runTest {
        val file = writeFile("bad.txt", "not json at all")
        val result = importer.importChat(1L, file)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("无法识别"))
    }

    // ==================== Tavern JSON object format ====================

    @Test
    fun `importChat imports tavern JSON object format`() = runTest {
        coEvery { chatRepository.createChat(1L, "My Chat") } returns 10L
        coEvery { chatRepository.sendMessage(10L, any(), any()) } returns 1L

        val jsonStr = """{"chatName": "My Chat", "messages": [{"role": "user", "content": "Hello"}, {"role": "assistant", "content": "Hi there"}]}"""
        val file = writeFile("tavern.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify { chatRepository.createChat(1L, "My Chat") }
        coVerify { chatRepository.sendMessage(10L, "Hello", "user") }
        coVerify { chatRepository.sendMessage(10L, "Hi there", "assistant") }
    }

    @Test
    fun `importChat skips blank messages in tavern format`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 10L
        coEvery { chatRepository.sendMessage(10L, any(), any()) } returns 1L

        val jsonStr = """{"messages": [{"role": "user", "content": "Hello"}, {"role": "assistant", "content": ""}, {"role": "user", "content": "  "}]}"""
        val file = writeFile("tavern.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { chatRepository.sendMessage(10L, "Hello", "user") }
    }

    // ==================== SillyTavern JSON array format ====================

    @Test
    fun `importChat imports SillyTavern array format with is_user field`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 20L
        coEvery { chatRepository.sendMessage(20L, any(), any()) } returns 1L

        val jsonStr = """
            [
                {"name": "User", "is_user": true, "mes": "Hello", "send_date": 123},
                {"name": "Alice", "is_user": false, "mes": "Hi!", "send_date": 124}
            ]
        """.trimIndent()
        val file = writeFile("st_array.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify { chatRepository.sendMessage(20L, "Hello", "user") }
        coVerify { chatRepository.sendMessage(20L, "Hi!", "assistant") }
    }

    @Test
    fun `importChat imports SillyTavern array format with role field`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 20L
        coEvery { chatRepository.sendMessage(20L, any(), any()) } returns 1L

        val jsonStr = """
            [
                {"role": "user", "content": "Hey"},
                {"role": "assistant", "content": "Hello!"}
            ]
        """.trimIndent()
        val file = writeFile("st_role.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify { chatRepository.sendMessage(20L, "Hey", "user") }
        coVerify { chatRepository.sendMessage(20L, "Hello!", "assistant") }
    }

    // ==================== SillyTavern JSONL format ====================

    @Test
    fun `importChat imports SillyTavern jsonl format`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 30L
        coEvery { chatRepository.sendMessage(30L, any(), any()) } returns 1L

        val jsonl = """{"name": "User", "is_user": true, "mes": "Line 1", "send_date": 1}
{"name": "Bot", "is_user": false, "mes": "Reply 1", "send_date": 2}"""
        val file = writeFile("chat.jsonl", jsonl)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify { chatRepository.sendMessage(30L, "Line 1", "user") }
        coVerify { chatRepository.sendMessage(30L, "Reply 1", "assistant") }
    }

    @Test
    fun `importChat handles malformed jsonl lines gracefully`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 30L
        coEvery { chatRepository.sendMessage(30L, any(), any()) } returns 1L

        val jsonl = """{"role": "user", "content": "Good line"}
not valid json
{"role": "assistant", "content": "Another good line"}"""
        val file = writeFile("mixed.jsonl", jsonl)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify { chatRepository.sendMessage(30L, "Good line", "user") }
        coVerify { chatRepository.sendMessage(30L, "Another good line", "assistant") }
    }

    @Test
    fun `importChat skips blank lines in jsonl format`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 30L
        coEvery { chatRepository.sendMessage(30L, any(), any()) } returns 1L

        val jsonl = """{"role": "user", "content": "Hello"}

{"role": "assistant", "content": "Hi"}"""
        val file = writeFile("blank_lines.jsonl", jsonl)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { chatRepository.sendMessage(30L, "Hello", "user") }
        coVerify(exactly = 1) { chatRepository.sendMessage(30L, "Hi", "assistant") }
    }

    // ==================== Edge cases ====================

    @Test
    fun `importChat handles empty messages array`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 10L

        val jsonStr = """{"messages": []}"""
        val file = writeFile("empty.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `importChat returns failure on exception`() = runTest {
        val file = writeFile("bad.json", """{"messages": [""")
        val result = importer.importChat(1L, file)
        assertTrue(result.isFailure)
    }
}
