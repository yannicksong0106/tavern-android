package com.tavern.lite.util

import android.content.Context
import com.tavern.lite.data.repository.ChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
     * 从 JSON 备份文件导入对话
     * 支持两种格式：
     * 1. 酒馆 AI 导出的 JSON（含 messages 数组）
     * 2. SillyTavern 的 chat_*.jsonl 格式（每行一个 JSON 对象）
     */
    suspend fun importChat(characterId: Long, file: File): Result<String> = try {
        val content = file.readText(Charsets.UTF_8).trim()

        val messageCount = if (content.startsWith("[")) {
            // SillyTavern jsonl-as-array 或酒馆 AI 格式
            importFromJsonArray(characterId, content)
        } else if (content.lines().firstOrNull()?.trimStart()?.startsWith("{") == true) {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.size > 1) {
                // SillyTavern jsonl 格式（每行一个 JSON）
                importFromJsonLines(characterId, lines)
            } else {
                // 单个 JSON 对象（酒馆 AI 导出格式）
                importFromJsonObject(characterId, content)
            }
        } else {
            return Result.failure(Exception("无法识别的文件格式"))
        }

        Result.success("导入成功，共 $messageCount 条消息")
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 从酒馆 AI 导出的 JSON 对象导入
     */
    private suspend fun importFromJsonObject(characterId: Long, content: String): Int {
        val obj = json.parseToJsonElement(content).jsonObject
        val chatName = obj["chatName"]?.jsonPrimitive?.content
        val messages = obj["messages"]?.jsonArray ?: return 0

        val chatId = chatRepository.createChat(characterId, chatName)
        var count = 0

        for (msgEl in messages) {
            val msg = msgEl.jsonObject
            val role = msg["role"]?.jsonPrimitive?.content ?: continue
            val text = msg["content"]?.jsonPrimitive?.content ?: continue
            if (text.isBlank()) continue

            chatRepository.sendMessage(chatId, text, role)
            count++
        }

        return count
    }

    /**
     * 从 JSON 数组导入（可能是 SillyTavern 格式）
     */
    private suspend fun importFromJsonArray(characterId: Long, content: String): Int {
        val arr = json.parseToJsonElement(content).jsonArray

        val chatId = chatRepository.createChat(characterId, null)
        var count = 0

        for (msgEl in arr) {
            val msg = msgEl.jsonObject
            val result = importSillyTavernMessage(chatId, msg)
            if (result) count++
        }

        return count
    }

    /**
     * 从 SillyTavern jsonl 格式导入（每行一个 JSON 对象）
     */
    private suspend fun importFromJsonLines(characterId: Long, lines: List<String>): Int {
        val chatId = chatRepository.createChat(characterId, null)
        var count = 0

        for (line in lines) {
            try {
                val msg = json.parseToJsonElement(line.trim()).jsonObject
                val result = importSillyTavernMessage(chatId, msg)
                if (result) count++
            } catch (_: Exception) {
                // 跳过无法解析的行
            }
        }

        return count
    }

    /**
     * 解析 SillyTavern 格式的消息对象
     * SillyTavern 格式：{"name": "角色名", "is_user": true/false, "mes": "消息内容", "send_date": ...}
     * 或简化格式：{"role": "user/assistant", "content": "..."}
     */
    private suspend fun importSillyTavernMessage(chatId: Long, obj: JsonObject): Boolean {
        // 先尝试标准格式
        val role = obj["role"]?.jsonPrimitive?.content
        val content = obj["content"]?.jsonPrimitive?.content

        if (role != null && content != null) {
            if (content.isBlank()) return false
            chatRepository.sendMessage(chatId, content, role)
            return true
        }

        // SillyTavern 格式
        val mes = obj["mes"]?.jsonPrimitive?.content
        if (mes != null) {
            if (mes.isBlank()) return false
            val isUser = obj["is_user"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val roleStr = if (isUser) "user" else "assistant"
            chatRepository.sendMessage(chatId, mes, roleStr)
            return true
        }

        return false
    }
}
