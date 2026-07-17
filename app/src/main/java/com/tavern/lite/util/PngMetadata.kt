package com.tavern.lite.util

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * 读取/写入 PNG 文件中的 tEXt chunk（用于 SillyTavern 角色卡）。
 * SillyTavern 将角色卡 JSON Base64 编码后存储在 PNG 的 tEXt chunk 的 "chara" 字段中。
 */
object PngMetadata {

    private const val MAX_EDITABLE_PNG_BYTES = 20L * 1024L * 1024L

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun readTextChunks(file: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        RandomAccessFile(file, "r").use { raf ->
            // 验证 PNG 签名
            val sig = ByteArray(8)
            raf.readFully(sig)
            if (!sig.contentEquals(PNG_SIGNATURE)) {
                throw IllegalArgumentException("Not a valid PNG file")
            }

            while (raf.filePointer < raf.length()) {
                val length = raf.readBigEndianInt()
                val type = ByteArray(4)
                raf.readFully(type)
                val typeName = String(type, Charsets.US_ASCII)

                if (length < 0 || length.toLong() > raf.length() - raf.filePointer - 4) {
                    throw IllegalArgumentException("Invalid PNG chunk length")
                }

                val data = ByteArray(length)
                raf.readFully(data)

                val crc = ByteArray(4)
                raf.readFully(crc)

                // 验证 CRC（检测文件损坏）
                val crcCalc = CRC32()
                crcCalc.update(type)
                crcCalc.update(data)
                val expectedCrc = ByteBuffer.wrap(crc).order(ByteOrder.BIG_ENDIAN).int
                if (crcCalc.value.toInt() != expectedCrc) {
                    Log.w("PngMetadata", "CRC mismatch for chunk '$typeName', data may be corrupted")
                }

                if (typeName == "tEXt") {
                    val nullIdx = data.indexOf(0)
                    if (nullIdx > 0) {
                        val key = String(data, 0, nullIdx, Charsets.ISO_8859_1)
                        val value = String(data, nullIdx + 1, data.size - nullIdx - 1, Charsets.ISO_8859_1)
                        result[key] = value
                    }
                }

                // IEND 结束
                if (typeName == "IEND") break
            }
        }
        return result
    }

    fun readCharaCard(file: File): String? {
        val chunks = readTextChunks(file)
        val charaBase64 = chunks["chara"] ?: return null
        return String(android.util.Base64.decode(charaBase64, android.util.Base64.DEFAULT), Charsets.UTF_8)
    }

    fun writeCharaCard(sourcePng: File, jsonStr: String, outputFile: File) {
        // 复制原图到输出位置
        if (sourcePng != outputFile) {
            sourcePng.copyTo(outputFile, overwrite = true)
        }
        val base64 = android.util.Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        writeTextChunk(outputFile, "chara", base64)
    }

    fun writeTextChunk(file: File, key: String, value: String) {
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("PNG file does not exist")
        }
        if (file.length() !in 1..MAX_EDITABLE_PNG_BYTES) {
            throw IllegalArgumentException("PNG file is too large")
        }

        val keyBytes = key.toByteArray(Charsets.ISO_8859_1)
        val valueBytes = value.toByteArray(Charsets.ISO_8859_1)
        val data = ByteArray(keyBytes.size + 1 + valueBytes.size)
        keyBytes.copyInto(data)
        data[keyBytes.size] = 0
        valueBytes.copyInto(data, keyBytes.size + 1)

        val chunkType = "tEXt".toByteArray(Charsets.US_ASCII)

        // 计算 CRC
        val crcData = ByteArray(4 + data.size)
        chunkType.copyInto(crcData)
        data.copyInto(crcData, 4)
        val crc = CRC32()
        crc.update(crcData)

        // 读取原始文件，插入 tEXt chunk 到 IEND 之前
        val allBytes = file.readBytes()
        val iendPos = findIendPosition(allBytes)
        if (iendPos < 0) {
            throw IllegalArgumentException("Cannot find IEND chunk in PNG")
        }

        val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()
        val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc.value.toInt()).array()

        // 直接流写而非再建一份整文件大小的 newBytes：allBytes 已全在内存，
        // 第二份缓冲会造成 ~2x 文件大小峰值堆（20MB 上限时可达 ~40MB）。
        // outputStream() 截断原文件，但 allBytes 已读入，重写同 path 安全（X3 审计 CONFIRMED）。
        BufferedOutputStream(file.outputStream()).use { out ->
            out.write(allBytes, 0, iendPos)      // IEND 之前
            out.write(lengthBytes)               // tEXt 长度
            out.write(chunkType)                 // "tEXt"
            out.write(data)                      // key\0value
            out.write(crcBytes)                  // CRC
            out.write(allBytes, iendPos, allBytes.size - iendPos) // IEND 及之后
        }
    }

    private fun RandomAccessFile.readBigEndianInt(): Int {
        val buf = ByteArray(4)
        readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun findIendPosition(data: ByteArray): Int {
        // IEND is always the last chunk in a valid PNG (12 bytes: 4 len + 4 type + 4 CRC)
        val iendStart = data.size - 12
        if (iendStart < 8) return -1
        if (data[iendStart + 4] == 'I'.code.toByte() &&
            data[iendStart + 5] == 'E'.code.toByte() &&
            data[iendStart + 6] == 'N'.code.toByte() &&
            data[iendStart + 7] == 'D'.code.toByte()
        ) {
            return iendStart
        }
        return -1
    }
}
