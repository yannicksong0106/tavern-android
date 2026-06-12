package com.tavern.lite.util

import android.content.Context
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class ChatExporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var chatRepository: ChatRepository
    private lateinit var characterRepository: CharacterRepository
    private lateinit var exporter: ChatExporter

    @Before
    fun setup() {
        context = mockk()
        chatRepository = mockk()
        characterRepository = mockk()
        every { context.cacheDir } returns temp.root
        exporter = ChatExporter(context, chatRepository, characterRepository, Json)
    }

    @Test
    fun `exportChat writes markdown with sanitized filename and chronological messages`() = runTest {
        coEvery { chatRepository.getChatById(1) } returns chat(name = "Bad/Name:?")
        coEvery { chatRepository.getAllMessagesForChat(1) } returns listOf(
            message(role = "assistant", content = "Hi", createdAt = 2_000),
            message(role = "user", content = "Hello", createdAt = 1_000)
        )

        val result = exporter.exportChat(1, ExportFormat.MARKDOWN, characterName = "Alice", userName = "Bob")

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertEquals("Bad_Name__.md", file.name)
        val content = file.readText()
        assertTrue(content.startsWith("# Bad/Name:?"))
        assertTrue(content.indexOf("Hello") < content.indexOf("Hi"))
        assertTrue(content.contains("**Bob**"))
        assertTrue(content.contains("**Alice**"))
    }

    @Test
    fun `exportChat html escapes title speaker and message content`() = runTest {
        coEvery { chatRepository.getChatById(1) } returns chat(name = "<Chat & Co>")
        coEvery { chatRepository.getAllMessagesForChat(1) } returns listOf(
            message(role = "assistant", content = "<script>alert(\"x\")</script>\nnext")
        )

        val file = exporter.exportChat(
            chatId = 1,
            format = ExportFormat.HTML,
            characterName = "Alice & <Bot>",
            userName = "Bob"
        ).getOrThrow()

        val html = file.readText()
        assertTrue(html.contains("&lt;Chat &amp; Co&gt;"))
        assertTrue(html.contains("Alice &amp; &lt;Bot&gt;"))
        assertTrue(html.contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;<br>next"))
        assertFalse(html.contains("<script>alert"))
    }

    @Test
    fun `exportChat json contains speaker names and message count`() = runTest {
        coEvery { chatRepository.getChatById(1) } returns chat(name = "JSON Chat")
        coEvery { chatRepository.getAllMessagesForChat(1) } returns listOf(
            message(role = "assistant", content = "Answer", createdAt = 2_000),
            message(role = "user", content = "Question", createdAt = 1_000)
        )

        val file = exporter.exportChat(1, ExportFormat.JSON, characterName = "Alice", userName = "Bob")
            .getOrThrow()

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals("JSON Chat", root.getValue("chatName").jsonPrimitive.content)
        assertEquals("Alice", root.getValue("characterName").jsonPrimitive.content)
        assertEquals("2", root.getValue("messageCount").jsonPrimitive.content)
        val messages = root.getValue("messages").jsonArray
        assertEquals("Question", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("Bob", messages[0].jsonObject.getValue("speaker").jsonPrimitive.content)
        assertEquals("Alice", messages[1].jsonObject.getValue("speaker").jsonPrimitive.content)
    }

    @Test
    fun `exportChat returns failure when chat is missing`() = runTest {
        coEvery { chatRepository.getChatById(404) } returns null

        val result = exporter.exportChat(404, ExportFormat.PLAINTEXT, characterName = "Alice")

        assertTrue(result.isFailure)
    }

    @Test
    fun `exportAllChats writes zip entries with sanitized chat names`() = runTest {
        coEvery { characterRepository.getCharacterById(10) } returns CharacterEntity(id = 10, name = "Alice")
        coEvery { chatRepository.getAllChatsForCharacter(10) } returns listOf(
            chat(id = 1, name = "Main/Chat"),
            chat(id = 2, name = null)
        )
        coEvery { chatRepository.getAllMessagesForChat(1) } returns listOf(message(chatId = 1, content = "First"))
        coEvery { chatRepository.getAllMessagesForChat(2) } returns listOf(message(chatId = 2, content = "Second"))

        val zip = exporter.exportAllChats(10, ExportFormat.PLAINTEXT, userName = "Bob").getOrThrow()

        ZipFile(zip).use { archive ->
            assertTrue(archive.getEntry("Main_Chat.txt") != null)
            assertTrue(archive.getEntry("chat_2.txt") != null)
            val main = archive.getInputStream(archive.getEntry("Main_Chat.txt")).reader().readText()
            val fallback = archive.getInputStream(archive.getEntry("chat_2.txt")).reader().readText()
            assertTrue(main.contains("First"))
            assertTrue(fallback.contains("Second"))
        }
    }

    @Test
    fun `exportAllChats returns failure when character is missing`() = runTest {
        coEvery { characterRepository.getCharacterById(404) } returns null

        val result = exporter.exportAllChats(404, ExportFormat.JSON)

        assertTrue(result.isFailure)
    }

    private fun chat(
        id: Long = 1,
        name: String? = "Test Chat",
        createdAt: Long = 1_000
    ): ChatEntity = ChatEntity(id = id, characterId = 10, name = name, createdAt = createdAt, updatedAt = createdAt)

    private fun message(
        chatId: Long = 1,
        role: String = "user",
        content: String,
        createdAt: Long = 1_000
    ): MessageEntity = MessageEntity(chatId = chatId, role = role, content = content, createdAt = createdAt)
}
