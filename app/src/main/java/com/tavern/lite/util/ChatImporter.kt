package com.tavern.lite.util

import android.content.Context
import android.util.Log
import com.tavern.lite.data.repository.ChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val json: Json
) {
    /**
     * 从 JSON 备份文件导入对话，返回详细报告
     */
    suspend fun importChat(characterId: Long, file: File): Result<ImportReport> = try {
        val content = file.readText(Charsets.UTF_8).trim()
        val skippedFields = mutableSetOf<String>()
        val warnings = mutableListOf<String>()

        val (chatId, imported, skipped, format) = if (content.startsWith("[")) {
            // SillyTavern jsonl-as-array 或酒馆 AI 格式
            importFromJsonArray(characterId, content, skippedFields, warnings)
        } else if (content.lines().firstOrNull()?.trimStart()?.startsWith("{") == true) {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.size > 1) {
                // SillyTavern jsonl 格式（每行一个 JSON）
                importFromJsonLines(characterId, lines, skippedFields, warnings)
            } else {
                // 单个 JSON 对象（酒馆 AI 导出格式）
                importFromJsonObject(characterId, content, skippedFields, warnings)
            }
        } else {
            return Result.failure(Exception("无法识别的文件格式"))
        }

        Result.success(
            ImportReport(
                chatId = chatId,
                importedMessages = imported,
                skippedMessages = skipped,
                format = format,
                skippedFields = skippedFields.toList(),
                warnings = warnings
            )
        )
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.failure(e)
    }

    private data class ImportResult(
        val chatId: Long,
        val imported: Int,
        val skipped: Int,
        val format: String
    )

    /**
     * 从酒馆 AI 导出的 JSON 对象导入
     */
    private suspend fun importFromJsonObject(
        characterId: Long,
        content: String,
        skippedFields: MutableSet<String>,
        warnings: MutableList<String>
    ): ImportResult {
        val obj = json.parseToJsonElement(content).jsonObject
        val chatName = obj["chatName"]?.jsonPrimitive?.content
        val messages = obj["messages"]?.jsonArray ?: return ImportResult(0, 0, 0, "Tavern JSON")

        val chatId = chatRepository.createChat(characterId, chatName)
        var imported = 0
        var skipped = 0

        for (msgEl in messages) {
            val msg = msgEl.jsonObject
            val role = msg["role"]?.jsonPrimitive?.content ?: continue
            val text = msg["content"]?.jsonPrimitive?.content ?: continue
            if (text.isBlank()) {
                skipped++
                continue
            }

            chatRepository.sendMessage(chatId, text, role)
            imported++
        }

        return ImportResult(chatId, imported, skipped, "Tavern JSON")
    }

    /**
     * 从 JSON 数组导入（可能是 SillyTavern 格式）
     */
    private suspend fun importFromJsonArray(
        characterId: Long,
        content: String,
        skippedFields: MutableSet<String>,
        warnings: MutableList<String>
    ): ImportResult {
        val arr = json.parseToJsonElement(content).jsonArray

        val chatId = chatRepository.createChat(characterId, null)
        var imported = 0
        var skipped = 0

        for (msgEl in arr) {
            val msg = msgEl.jsonObject
            val result = importSillyTavernMessage(chatId, msg, skippedFields)
            when (result) {
                ImportMessageResult.IMPORTED -> imported++
                ImportMessageResult.SKIPPED -> skipped++
                ImportMessageResult.FAILED -> {}
            }
        }

        checkSkippedFields(arr.firstOrNull()?.jsonObject, skippedFields)
        return ImportResult(chatId, imported, skipped, "SillyTavern JSON Array")
    }

    /**
     * 从 SillyTavern jsonl 格式导入（每行一个 JSON 对象）
     */
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
                val msg = json.parseToJsonElement(line.trim()).jsonObject
                val result = importSillyTavernMessage(chatId, msg, skippedFields)
                when (result) {
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
        
        val firstMsg = lines.firstOrNull()?.let { 
            try { json.parseToJsonElement(it.trim()).jsonObject } catch (_: Exception) { null }
        }
        checkSkippedFields(firstMsg, skippedFields)
        
        return ImportResult(chatId, imported, skipped, "SillyTavern JSONL")
    }

    private enum class ImportMessageResult {
        IMPORTED, SKIPPED, FAILED
    }

    /**
     * 解析 SillyTavern 格式的消息对象
     */
    private suspend fun importSillyTavernMessage(
        chatId: Long,
        obj: JsonObject,
        skippedFields: MutableSet<String>
    ): ImportMessageResult {
        // 先尝试标准格式
        val role = obj["role"]?.jsonPrimitive?.content
        val content = obj["content"]?.jsonPrimitive?.content

        if (role != null && content != null) {
            if (content.isBlank()) return ImportMessageResult.SKIPPED
            chatRepository.sendMessage(chatId, content, role)
            return ImportMessageResult.IMPORTED
        }

        // SillyTavern 格式
        val mes = obj["mes"]?.jsonPrimitive?.content
        if (mes != null) {
            if (mes.isBlank()) return ImportMessageResult.SKIPPED
            val isUser = obj["is_user"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val roleStr = if (isUser) "user" else "assistant"
            chatRepository.sendMessage(chatId, mes, roleStr)
            return ImportMessageResult.IMPORTED
        }

        return ImportMessageResult.FAILED
    }

    /**
     * 检测 ST 消息中存在但本应用未处理的字段
     */
    private fun checkSkippedFields(firstMessage: JsonObject?, skippedFields: MutableSet<String>) {
        firstMessage ?: return
        val knownFields = setOf("role", "content", "mes", "is_user", "name", "send_date")
        val stOnlyFields = listOf("swipes", "swipe_id", "attachments", "extra")
        
        for (field in stOnlyFields) {
            if (firstMessage.containsKey(field)) {
                skippedFields.add(field)
            }
        }
    }
}
