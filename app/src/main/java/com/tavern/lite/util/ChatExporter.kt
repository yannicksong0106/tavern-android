package com.tavern.lite.util

import android.content.Context
import android.util.Log
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat {
    MARKDOWN, HTML, PLAINTEXT, JSON
}

@Singleton
class ChatExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val characterRepository: CharacterRepository,
    private val json: Json
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    private companion object {
        // 文件名非法字符清理正则；提到常量避免每次导出/每 chat 循环内重编译（X 审计 Low）。
        private val UNSAFE_FILENAME = Regex("[\\\\/:*?\"<>|]")
    }

    /**
     * 导出单个对话
     */
    suspend fun exportChat(
        chatId: Long,
        format: ExportFormat,
        characterName: String,
        userName: String = "User"
    ): Result<File> = try {
        val chat = chatRepository.getChatById(chatId)
            ?: return Result.failure(Exception("对话不存在"))
        val messages = chatRepository.getAllMessagesForChat(chatId).reversed()
        val chatName = chat.name ?: "对话"

        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()

        val ext = when (format) {
            ExportFormat.MARKDOWN -> "md"
            ExportFormat.HTML -> "html"
            ExportFormat.PLAINTEXT -> "txt"
            ExportFormat.JSON -> "json"
        }
        val safeName = chatName.replace(UNSAFE_FILENAME, "_")
        val outputFile = File(exportDir, "${safeName}.$ext")

        val content = when (format) {
            ExportFormat.MARKDOWN -> toMarkdown(chat, messages, characterName, userName)
            ExportFormat.HTML -> toHtml(chat, messages, characterName, userName)
            ExportFormat.PLAINTEXT -> toPlainText(chat, messages, characterName, userName)
            ExportFormat.JSON -> toJson(chat, messages, characterName, userName)
        }

        outputFile.writeText(content, Charsets.UTF_8)
        Result.success(outputFile)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w("ChatExporter", "导出聊天失败", e)
        Result.failure(e)
    }

    /**
     * 导出角色的所有对话为 ZIP
     */
    suspend fun exportAllChats(
        characterId: Long,
        format: ExportFormat,
        userName: String = "User"
    ): Result<File> = try {
        val character = characterRepository.getCharacterById(characterId)
            ?: return Result.failure(Exception("角色不存在"))
        val chats = chatRepository.getAllChatsForCharacter(characterId)

        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val zipFile = File(exportDir, "${character.name}_chats.zip")

        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            for (chat in chats) {
                val messages = chatRepository.getAllMessagesForChat(chat.id).reversed()
                val ext = when (format) {
                    ExportFormat.MARKDOWN -> "md"
                    ExportFormat.HTML -> "html"
                    ExportFormat.PLAINTEXT -> "txt"
                    ExportFormat.JSON -> "json"
                }
                val chatName = (chat.name ?: "chat_${chat.id}").replace(UNSAFE_FILENAME, "_")
                val entry = ZipEntry("$chatName.$ext")

                val content = when (format) {
                    ExportFormat.MARKDOWN -> toMarkdown(chat, messages, character.name, userName)
                    ExportFormat.HTML -> toHtml(chat, messages, character.name, userName)
                    ExportFormat.PLAINTEXT -> toPlainText(chat, messages, character.name, userName)
                    ExportFormat.JSON -> toJson(chat, messages, character.name, userName)
                }

                zip.putNextEntry(entry)
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        Result.success(zipFile)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w("ChatExporter", "批量导出失败", e)
        Result.failure(e)
    }

    private fun formatDate(timestamp: Long): String = dateTimeFormatter.format(Instant.ofEpochMilli(timestamp))

    // --- Markdown ---

    private fun toMarkdown(
        chat: ChatEntity,
        messages: List<MessageEntity>,
        charName: String,
        userName: String
    ): String = buildString {
        appendLine("# ${chat.name ?: charName}")
        appendLine()
        appendLine("_创建于 ${formatDate(chat.createdAt)}_")
        appendLine()
        appendLine("---")
        appendLine()

        for (msg in messages) {
            val speaker = when (msg.role) {
                "user" -> "**$userName**"
                "assistant" -> "**$charName**"
                else -> "_System_"
            }
            appendLine("$speaker  _${formatDate(msg.createdAt)}_")
            appendLine()
            appendLine(msg.content)
            appendLine()
        }
    }

    // --- HTML ---

    private fun toHtml(
        chat: ChatEntity,
        messages: List<MessageEntity>,
        charName: String,
        userName: String
    ): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"zh\">")
        appendLine("<head>")
        appendLine("<meta charset=\"UTF-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        appendLine("<title>${escapeHtml(chat.name ?: charName)}</title>")
        appendLine("<style>")
        appendLine("""
            body { font-family: -apple-system, sans-serif; max-width: 720px; margin: 0 auto; padding: 16px; background: #1a1a2e; color: #e0e0e0; }
            h1 { color: #e94560; border-bottom: 1px solid #333; padding-bottom: 8px; }
            .meta { color: #888; font-size: 0.85em; margin-bottom: 16px; }
            .msg { margin: 12px 0; padding: 12px 16px; border-radius: 12px; }
            .msg-user { background: #16213e; margin-left: 20%; }
            .msg-assistant { background: #0f3460; margin-right: 20%; }
            .msg-system { background: #1a1a2e; border: 1px solid #333; text-align: center; font-style: italic; }
            .speaker { font-weight: bold; margin-bottom: 4px; }
            .speaker-user { color: #e94560; }
            .speaker-assistant { color: #53a8b6; }
            .time { color: #666; font-size: 0.8em; }
            .content { white-space: pre-wrap; line-height: 1.6; }
        """.trimIndent())
        appendLine("</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("<h1>${escapeHtml(chat.name ?: charName)}</h1>")
        appendLine("<div class='meta'>创建于 ${formatDate(chat.createdAt)}</div>")

        for (msg in messages) {
            val roleClass = when (msg.role) {
                "user" -> "msg-user"
                "assistant" -> "msg-assistant"
                else -> "msg-system"
            }
            val speakerClass = when (msg.role) {
                "user" -> "speaker-user"
                "assistant" -> "speaker-assistant"
                else -> ""
            }
            val speaker = when (msg.role) {
                "user" -> escapeHtml(userName)
                "assistant" -> escapeHtml(charName)
                else -> "System"
            }
            appendLine("<div class='msg $roleClass'>")
            appendLine("<div class='speaker $speakerClass'>$speaker <span class='time'>${formatDate(msg.createdAt)}</span></div>")
            appendLine("<div class='content'>${escapeHtml(msg.content)}</div>")
            appendLine("</div>")
        }

        appendLine("</body>")
        appendLine("</html>")
    }

    // --- Plain text ---

    private fun toPlainText(
        chat: ChatEntity,
        messages: List<MessageEntity>,
        charName: String,
        userName: String
    ): String = buildString {
        appendLine(chat.name ?: charName)
        appendLine("创建于 ${formatDate(chat.createdAt)}")
        appendLine("=".repeat(40))
        appendLine()

        for (msg in messages) {
            val speaker = when (msg.role) {
                "user" -> userName
                "assistant" -> charName
                else -> "System"
            }
            appendLine("[$speaker] ${formatDate(msg.createdAt)}")
            appendLine(msg.content)
            appendLine()
        }
    }

    // --- JSON (SillyTavern compatible) ---

    @kotlinx.serialization.Serializable
    private data class ExportMessage(
        val role: String,
        val content: String,
        val timestamp: Long,
        val speaker: String
    )

    @kotlinx.serialization.Serializable
    private data class ChatExport(
        val chatName: String?,
        val characterName: String,
        val userName: String,
        val createdAt: Long,
        val messageCount: Int,
        val messages: List<ExportMessage>
    )

    private fun toJson(
        chat: ChatEntity,
        messages: List<MessageEntity>,
        charName: String,
        userName: String
    ): String {
        val export = ChatExport(
            chatName = chat.name,
            characterName = charName,
            userName = userName,
            createdAt = chat.createdAt,
            messageCount = messages.size,
            messages = messages.map { msg ->
                ExportMessage(
                    role = msg.role,
                    content = msg.content,
                    timestamp = msg.createdAt,
                    speaker = when (msg.role) {
                        "user" -> userName
                        "assistant" -> charName
                        else -> "System"
                    }
                )
            }
        )

        return json.encodeToString(export)
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br>")
}
