package com.tavern.lite.util

import android.util.Base64
import java.io.File

object ImageUtils {
    /**
     * 将图片文件转换为 base64 data URI（OpenAI multimodal API 格式）。
     * 支持常见图片格式：jpg, png, gif, webp。
     */
    fun fileToDataUri(file: File): String? {
        if (!file.exists()) return null
        if (file.length() > 20 * 1024 * 1024) return null // 20MB 上限，防止 OOM
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
