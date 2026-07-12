package com.tavern.lite.util

import android.util.Base64
import java.io.File

object ImageUtils {
    const val MAX_DATA_URI_IMAGE_BYTES = 8L * 1024L * 1024L

    /**
     * 将图片文件转换为 base64 data URI（OpenAI multimodal API 格式）。
     * 支持常见图片格式：jpg, png, gif, webp。
     */
    fun fileToDataUri(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        if (file.length() !in 1..MAX_DATA_URI_IMAGE_BYTES) return null
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mimeType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return "data:$mimeType;base64,$base64"
    }
}
