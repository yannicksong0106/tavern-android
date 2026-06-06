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
    fun `backup models include critical entity fields`() {
        val messageFields = MessageBackup::class.java.declaredFields.map { it.name }.toSet()
        val bgmFields = BgmBackup::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(messageFields.contains("imagePaths"))
        assertTrue(bgmFields.contains("emotion"))
    }
}
