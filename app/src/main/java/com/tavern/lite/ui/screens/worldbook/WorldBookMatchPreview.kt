package com.tavern.lite.ui.screens.worldbook

import android.util.Log
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import kotlinx.serialization.json.Json

data class KeywordMatchPreview(
    val primaryKeys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val matchedPrimaryKeys: Set<String> = emptySet(),
    val matchedSecondaryKeys: Set<String> = emptySet(),
    val triggered: Boolean = false,
)

fun buildKeywordMatchPreview(
    entry: WorldBookEntryEntity,
    previewText: String,
    json: Json = Json,
): KeywordMatchPreview {
    val primaryKeys = decodeKeywordList(entry.keys, json)
    val secondaryKeys = decodeKeywordList(entry.keysSecondary, json)
    val lowerText = previewText.lowercase()

    val matchedPrimary = primaryKeys
        .filter { key -> key.isNotBlank() && lowerText.contains(key.lowercase()) }
        .toSet()
    val matchedSecondary = secondaryKeys
        .filter { key -> key.isNotBlank() && lowerText.contains(key.lowercase()) }
        .toSet()

    val triggered = entry.constant || if (entry.selective && secondaryKeys.isNotEmpty()) {
        when (entry.selectiveLogic) {
            0 -> matchedPrimary.isNotEmpty() && matchedSecondary.isNotEmpty()
            1 -> matchedPrimary.isNotEmpty() || matchedSecondary.isNotEmpty()
            2 -> matchedPrimary.isNotEmpty() && matchedSecondary.isEmpty()
            else -> matchedPrimary.isNotEmpty()
        }
    } else {
        matchedPrimary.isNotEmpty()
    }

    return KeywordMatchPreview(
        primaryKeys = primaryKeys,
        secondaryKeys = secondaryKeys,
        matchedPrimaryKeys = matchedPrimary,
        matchedSecondaryKeys = matchedSecondary,
        triggered = triggered
    )
}

private fun decodeKeywordList(raw: String, json: Json): List<String> {
    return try {
        json.decodeFromString<List<String>>(raw)
    } catch (e: Exception) {
        Log.w("WorldBookMatchPreview", "Failed to decode keyword list: ${e.message}", e)
        emptyList()
    }
}
