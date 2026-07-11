package com.tavern.lite.util

import android.util.Log
import com.tavern.lite.data.repository.ChatRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_IMPORT_BYTES = 10L * 1024L * 1024L

@Singleton
class ChatImporter @Inject constructor(
    private val chatRepository: ChatRepository,
    private val json: Json
) {
    suspend fun importChat(characterId: Long, file: File): Result<ImportReport> {
        return try {
            if (file.length() > MAX_IMPORT_BYTES) {
                return Result.failure(IllegalArgumentException("Import file is too large (max 10MB)"))
            }

            val content = file.readText(Charsets.UTF_8).trim()
            val skippedFields = mutableSetOf<String>()
            val warnings = mutableListOf<String>()

            val result = when {
                content.startsWith("[") -> importFromJsonArray(characterId, content, skippedFields)
                content.startsWith("{") -> importFromJsonObjectOrLines(
                    characterId = characterId,
                    content = content,
                    skippedFields = skippedFields,
                    warnings = warnings
                ) ?: return Result.failure(IllegalArgumentException("无法解析 JSON 文件"))
                else -> return Result.failure(Exception("无法识别的文件格式"))
            }

            Result.success(
                ImportReport(
                    chatId = result.chatId,
                    importedMessages = result.imported,
                    skippedMessages = result.skipped,
                    format = result.format,
                    skippedFields = skippedFields.toList(),
                    warnings = warnings
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private data class ImportResult(
        val chatId: Long,
        val imported: Int,
        val skipped: Int,
        val format: String
    )

    private suspend fun importFromJsonObjectOrLines(
        characterId: Long,
        content: String,
        skippedFields: MutableSet<String>,
        warnings: MutableList<String>
    ): ImportResult? {
        val parsedObject = parseJsonObjectOrNull(content)
        if (parsedObject != null) {
            return importFromJsonObject(characterId, parsedObject, skippedFields)
        }

        // 单个 `{` 起始且解析失败通常是截断/损坏的 JSON；此时按 JSONL 兜底会先建 chat 再全部解析失败，产生孤儿空 chat。
        // 仅在明确的多行 JSONL（每一行都能独立解析为 JSON 对象）时才走 line 路径。
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.size <= 1) return null

        // 至少有一行能独立解析为 JSON 对象，才当作 JSONL。全都失败通常是截断/损坏的单个 JSON。
        val hasAnyValidJsonLine = lines.any { parseJsonObjectOrNull(it) != null }
        if (!hasAnyValidJsonLine) return null

        return importFromJsonLines(characterId, lines, skippedFields, warnings)
    }

    private suspend fun importFromJsonObject(
        characterId: Long,
        obj: JsonObject,
        skippedFields: MutableSet<String>
    ): ImportResult {
        val chatName = obj.stringField("chatName")
        val messages = obj["messages"]?.jsonArray ?: return ImportResult(0, 0, 0, "Tavern JSON")

        val chatId = chatRepository.createChat(characterId, chatName)
        var imported = 0
        var skipped = 0

        for (msgEl in messages) {
            val msg = msgEl.jsonObject
            val role = msg.stringField("role") ?: continue
            val text = msg.stringField("content") ?: continue
            if (text.isBlank()) {
                skipped++
                continue
            }

            chatRepository.sendMessage(chatId, text, role)
            imported++
        }

        checkSkippedFields(messages.firstOrNull()?.jsonObject, skippedFields)
        return ImportResult(chatId, imported, skipped, "Tavern JSON")
    }

    private suspend fun importFromJsonArray(
        characterId: Long,
        content: String,
        skippedFields: MutableSet<String>
    ): ImportResult {
        val arr = json.parseToJsonElement(content).jsonArray

        val chatId = chatRepository.createChat(characterId, null)
        var imported = 0
        var skipped = 0

        for (msgEl in arr) {
            val msg = msgEl.jsonObject
            when (importSillyTavernMessage(chatId, msg)) {
                ImportMessageResult.IMPORTED -> imported++
                ImportMessageResult.SKIPPED -> skipped++
                ImportMessageResult.FAILED -> {}
            }
        }

        checkSkippedFields(arr.firstOrNull()?.jsonObject, skippedFields)
        return ImportResult(chatId, imported, skipped, "SillyTavern JSON Array")
    }

    private suspend fun importFromJsonLines(
        characterId: Long,
        lines: List<String>,
        skippedFields: MutableSet<String>,
        warnings: MutableList<String>
    ): ImportResult {
        val chatId = chatRepository.createChat(characterId, null)
        var imported = 0
        var skipped = 0
        var parseErrors = 0

        for (line in lines) {
            try {
                val msg = json.parseToJsonElement(line).jsonObject
                when (importSillyTavernMessage(chatId, msg)) {
                    ImportMessageResult.IMPORTED -> imported++
                    ImportMessageResult.SKIPPED -> skipped++
                    ImportMessageResult.FAILED -> parseErrors++
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("ChatImporter", "Failed to parse jsonl line: ${e.message}", e)
                parseErrors++
            }
        }

        if (parseErrors > 0) {
            warnings.add("$parseErrors 条消息解析失败")
        }

        val firstMsg = lines.firstOrNull()?.let { parseJsonObjectOrNull(it) }
        checkSkippedFields(firstMsg, skippedFields)

        return ImportResult(chatId, imported, skipped, "SillyTavern JSONL")
    }

    private fun parseJsonObjectOrNull(content: String): JsonObject? {
        return try {
            json.parseToJsonElement(content).jsonObject
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** JsonNull.content 是字符串 "null"；导入时必须视为缺失字段而非字面值。 */
    private fun JsonObject.stringField(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.content
    }

    private enum class ImportMessageResult {
        IMPORTED, SKIPPED, FAILED
    }

    private suspend fun importSillyTavernMessage(
        chatId: Long,
        obj: JsonObject
    ): ImportMessageResult {
        val role = obj.stringField("role")
        val content = obj.stringField("content")

        if (role != null && content != null) {
            if (content.isBlank()) return ImportMessageResult.SKIPPED
            chatRepository.sendMessage(chatId, content, role)
            return ImportMessageResult.IMPORTED
        }

        val mes = obj.stringField("mes")
        if (mes != null) {
            if (mes.isBlank()) return ImportMessageResult.SKIPPED
            val isUser = obj.stringField("is_user")?.toBooleanStrictOrNull() ?: false
            val roleStr = if (isUser) "user" else "assistant"
            chatRepository.sendMessage(chatId, mes, roleStr)
            return ImportMessageResult.IMPORTED
        }

        return ImportMessageResult.FAILED
    }

    private fun checkSkippedFields(firstMessage: JsonObject?, skippedFields: MutableSet<String>) {
        firstMessage ?: return
        val stOnlyFields = listOf("swipes", "swipe_id", "attachments", "extra")

        for (field in stOnlyFields) {
            if (firstMessage.containsKey(field)) {
                skippedFields.add(field)
            }
        }
    }
}
