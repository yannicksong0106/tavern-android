package com.tavern.lite.util

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

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var importer: ChatImporter
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)
        importer = ChatImporter(chatRepository, json)
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
        val report = result.getOrNull()!!
        assertEquals(10L, report.chatId)
        assertEquals(2, report.importedMessages)
        assertEquals(0, report.skippedMessages)
        assertEquals("Tavern JSON", report.format)
        coVerify { chatRepository.createChat(1L, "My Chat") }
        coVerify { chatRepository.sendMessage(10L, "Hello", "user") }
        coVerify { chatRepository.sendMessage(10L, "Hi there", "assistant") }
    }

    @Test
    fun `importChat imports pretty printed tavern JSON object format`() = runTest {
        coEvery { chatRepository.createChat(1L, "My Chat") } returns 11L
        coEvery { chatRepository.sendMessage(11L, any(), any()) } returns 1L

        val jsonStr = """
            {
              "chatName": "My Chat",
              "messages": [
                { "role": "user", "content": "Hello" },
                { "role": "assistant", "content": "Hi there" }
              ]
            }
        """.trimIndent()
        val file = writeFile("tavern_pretty.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertEquals(11L, report.chatId)
        assertEquals(2, report.importedMessages)
        assertEquals("Tavern JSON", report.format)
        coVerify { chatRepository.createChat(1L, "My Chat") }
        coVerify { chatRepository.sendMessage(11L, "Hello", "user") }
        coVerify { chatRepository.sendMessage(11L, "Hi there", "assistant") }
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
        val report = result.getOrNull()!!
        assertEquals(1, report.importedMessages)
        assertEquals(2, report.skippedMessages)
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
        val report = result.getOrNull()!!
        assertEquals(2, report.importedMessages)
        assertEquals("SillyTavern JSON Array", report.format)
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
        val report = result.getOrNull()!!
        assertEquals(2, report.importedMessages)
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
        val report = result.getOrNull()!!
        assertEquals(2, report.importedMessages)
        assertEquals("SillyTavern JSONL", report.format)
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
        val report = result.getOrNull()!!
        assertEquals(2, report.importedMessages)
        assertTrue(report.warnings.any { it.contains("解析失败") })
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
        val report = result.getOrNull()!!
        assertEquals(2, report.importedMessages)
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
        val report = result.getOrNull()!!
        assertEquals(0, report.importedMessages)
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `importChat returns failure on exception`() = runTest {
        val file = writeFile("bad.json", """{"messages": [""")
        val result = importer.importChat(1L, file)
        assertTrue(result.isFailure)
    }

    @Test
    fun `importChat fails on truncated JSON object without creating orphan chat`() = runTest {
        // 截断的 Tavern JSON — `{` 起始但解析失败，不应 fall through 到 JSONL 建空 chat
        val jsonStr = "{\n  \"chatName\": \"vacation\",\n  \"messages\": [\n"
        val file = writeFile("truncated.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { chatRepository.createChat(any(), any()) }
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `importChat treats JsonNull chatName and message fields as missing`() = runTest {
        coEvery { chatRepository.createChat(1L, null) } returns 40L
        coEvery { chatRepository.sendMessage(40L, any(), any()) } returns 1L

        // chatName=null → chat 名应为 null 而非字面 "null"
        // role=null 或 content=null 的消息应被跳过（continue），不写入字面 "null"
        val jsonStr = """{
          "chatName": null,
          "messages": [
            {"role": null, "content": "orphan"},
            {"role": "user", "content": null},
            {"role": "user", "content": "real"}
          ]
        }""".trimIndent()
        val file = writeFile("null_fields.json", jsonStr)

        val result = importer.importChat(1L, file)
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        coVerify { chatRepository.createChat(1L, null) }
        coVerify(exactly = 1) { chatRepository.sendMessage(40L, "real", "user") }
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), "null", any()) }
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any(), "null") }
    }

    @Test
    fun `importChat rejects oversized files before reading`() = runTest {
        val file = File(tempDir, "huge.json")
        java.io.RandomAccessFile(file, "rw").use { it.setLength(10L * 1024L * 1024L + 1L) }

        val result = importer.importChat(1L, file)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("too large"))
        coVerify(exactly = 0) { chatRepository.createChat(any(), any()) }
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any(), any()) }
    }
}
