package com.tavern.lite.util

import org.json.JSONArray

object SwipeUtils {

    fun parseSwipeContent(json: String): List<String> {
        if (json == "[]" || json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toJsonArray(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }
}
