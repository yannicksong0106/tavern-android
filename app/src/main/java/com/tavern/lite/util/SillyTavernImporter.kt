package com.tavern.lite.util

import android.content.Context
import android.util.Log
import com.tavern.lite.data.model.CharacterCard
import com.tavern.lite.data.repository.CharacterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SillyTavernImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterRepository: CharacterRepository,
    private val json: Json
) {
    /**
     * 从 PNG 文件导入 SillyTavern 角色卡
     */
    suspend fun importFromPng(pngFile: File): Result<Long> {
        return try {
            val jsonStr = PngMetadata.readCharaCard(pngFile)
                ?: return Result.failure(Exception("PNG 文件中没有找到角色卡数据"))

            val card = json.decodeFromString<CharacterCard>(jsonStr)

            // 保存头像
            val avatarDir = File(context.filesDir, "avatars")
            avatarDir.mkdirs()
            val avatarFile = File(avatarDir, "${card.data.name.hashCode()}.png")
            pngFile.copyTo(avatarFile, overwrite = true)

            val id = characterRepository.createCharacter(card.data, avatarPath = avatarFile.absolutePath)
            Result.success(id)
        } catch (e: Exception) {
            Log.w("SillyTavernImporter", "PNG 导入失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从 JSON 文件导入角色卡
     */
    suspend fun importFromJson(jsonFile: File): Result<Long> {
        return try {
            val jsonStr = jsonFile.readText(Charsets.UTF_8)
            val card = json.decodeFromString<CharacterCard>(jsonStr)
            val id = characterRepository.createCharacter(card.data)
            Result.success(id)
        } catch (e: Exception) {
            Log.w("SillyTavernImporter", "JSON 导入失败", e)
            Result.failure(e)
        }
    }

    /**
     * 导出角色卡为 JSON
     */
    suspend fun exportToJson(characterId: Long, outputFile: File): Result<Unit> {
        return try {
            val entity = characterRepository.getCharacterById(characterId)
                ?: return Result.failure(Exception("角色不存在"))
            val card = characterRepository.toCharacterCard(entity)
            outputFile.writeText(json.encodeToString(CharacterCard.serializer(), card), Charsets.UTF_8)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("SillyTavernImporter", "JSON 导出失败", e)
            Result.failure(e)
        }
    }

    /**
     * 导出角色卡为 PNG（将 JSON 嵌入 tEXt chunk）
     */
    suspend fun exportToPng(characterId: Long, outputFile: File): Result<Unit> {
        return try {
            val entity = characterRepository.getCharacterById(characterId)
                ?: return Result.failure(Exception("角色不存在"))
            val card = characterRepository.toCharacterCard(entity)
            val jsonStr = json.encodeToString(CharacterCard.serializer(), card)

            // 如果有头像，用头像作为基础 PNG；否则创建一个最小 PNG
            val avatarPath = entity.avatarPath
            val sourcePng = if (avatarPath != null) {
                File(avatarPath)
            } else {
                // 创建一个 1x1 透明 PNG 作为占位
                val placeholder = File(context.cacheDir, "placeholder.png")
                if (!placeholder.exists()) {
                    createMinimalPng(placeholder)
                }
                placeholder
            }

            PngMetadata.writeCharaCard(sourcePng, jsonStr, outputFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("SillyTavernImporter", "PNG 导出失败", e)
            Result.failure(e)
        }
    }

    private fun createMinimalPng(file: File) {
        // 最小合法 PNG：1x1 透明像素
        val bytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            // IHDR
            0, 0, 0, 0x0D, // length = 13
            0x49, 0x48, 0x44, 0x52, // "IHDR"
            0, 0, 0, 1, // width = 1
            0, 0, 0, 1, // height = 1
            8, // bit depth
            6, // color type = RGBA
            0, 0, 0, // compression, filter, interlace
            0, 0, 0, 0, // CRC placeholder
            // IDAT
            0, 0, 0, 0x0E, // length
            0x49, 0x44, 0x41, 0x54, // "IDAT"
            0x78, 0x01.toByte(), 0x62, 0x60, 0x60, 0x60, 0x00, 0x00, 0x00, 0x64, 0x00, 0x01.toByte(),
            0, 0, 0, 0, // CRC placeholder
            // IEND
            0, 0, 0, 0, // length = 0
            0x49, 0x45, 0x4E, 0x44, // "IEND"
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte() // CRC
        )
        file.writeBytes(bytes)
    }
}
