package com.tavern.lite.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDataTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `message backup preserves image paths`() {
        val backup = BackupData(
            messages = listOf(
                MessageBackup(
                    id = 1,
                    chatId = 2,
                    role = "user",
                    content = "image",
                    createdAt = 100,
                    imagePaths = """["/files/a.png","/files/b.png"]"""
                )
            )
        )

        val decoded = json.decodeFromString<BackupData>(json.encodeToString(BackupData.serializer(), backup))

        assertEquals("""["/files/a.png","/files/b.png"]""", decoded.messages.single().imagePaths)
    }

    @Test
    fun `message backup defaults missing image paths for old backups`() {
        val decoded = json.decodeFromString<BackupData>(
            """
            {
              "messages": [
                {
                  "id": 1,
                  "chatId": 2,
                  "role": "user",
                  "content": "legacy",
                  "createdAt": 100
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("[]", decoded.messages.single().imagePaths)
    }

    @Test
    fun `bgm backup preserves emotion mapping`() {
        val backup = BackupData(
            bgms = listOf(
                BgmBackup(
                    id = 1,
                    characterId = 2,
                    name = "Happy theme",
                    audioPath = "/files/happy.mp3",
                    emotion = "happy",
                    createdAt = 100
                )
            )
        )

        val decoded = json.decodeFromString<BackupData>(json.encodeToString(BackupData.serializer(), backup))

        assertEquals("happy", decoded.bgms.single().emotion)
    }

    @Test
    fun `bgm backup defaults missing emotion for old backups`() {
        val decoded = json.decodeFromString<BackupData>(
            """
            {
              "bgms": [
                {
                  "id": 1,
                  "characterId": 2,
                  "name": "Legacy theme",
                  "audioPath": "/files/theme.mp3",
                  "createdAt": 100
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("", decoded.bgms.single().emotion)
    }

    @Test
    fun `character and chat backups preserve preset bindings`() {
        val backup = BackupData(
            characters = listOf(
                CharacterBackup(
                    id = 1,
                    name = "Alice",
                    presetId = 10,
                    createdAt = 100,
                    updatedAt = 100
                )
            ),
            chats = listOf(
                ChatBackup(
                    id = 2,
                    characterId = 1,
                    presetId = 20,
                    createdAt = 110,
                    updatedAt = 120
                )
            )
        )

        val decoded = json.decodeFromString<BackupData>(json.encodeToString(BackupData.serializer(), backup))

        assertEquals(10L, decoded.characters.single().presetId)
        assertEquals(20L, decoded.chats.single().presetId)
    }

    @Test
    fun `message backup preserves reply and pinned state`() {
        val backup = BackupData(
            messages = listOf(
                MessageBackup(
                    id = 2,
                    chatId = 1,
                    role = "assistant",
                    content = "reply",
                    createdAt = 100,
                    replyToId = 1,
                    isPinned = true
                )
            )
        )

        val decoded = json.decodeFromString<BackupData>(json.encodeToString(BackupData.serializer(), backup))

        assertEquals(1L, decoded.messages.single().replyToId)
        assertEquals(true, decoded.messages.single().isPinned)
    }

    @Test
    fun `new backup fields default for old backups`() {
        val decoded = json.decodeFromString<BackupData>(
            """
            {
              "characters": [
                {
                  "id": 1,
                  "name": "Legacy",
                  "createdAt": 100,
                  "updatedAt": 100
                }
              ],
              "chats": [
                {
                  "id": 2,
                  "characterId": 1,
                  "createdAt": 110,
                  "updatedAt": 120
                }
              ],
              "messages": [
                {
                  "id": 3,
                  "chatId": 2,
                  "role": "assistant",
                  "content": "legacy",
                  "createdAt": 130
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(null, decoded.characters.single().presetId)
        assertEquals(null, decoded.chats.single().presetId)
        assertEquals(null, decoded.messages.single().replyToId)
        assertEquals(false, decoded.messages.single().isPinned)
    }

    @Test
    fun `backup models include critical entity fields`() {
        val characterFields = CharacterBackup::class.java.declaredFields.map { it.name }.toSet()
        val chatFields = ChatBackup::class.java.declaredFields.map { it.name }.toSet()
        val messageFields = MessageBackup::class.java.declaredFields.map { it.name }.toSet()
        val bgmFields = BgmBackup::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(characterFields.contains("presetId"))
        assertTrue(chatFields.contains("presetId"))
        assertTrue(messageFields.contains("imagePaths"))
        assertTrue(messageFields.contains("replyToId"))
        assertTrue(messageFields.contains("isPinned"))
        assertTrue(bgmFields.contains("emotion"))
    }
}
