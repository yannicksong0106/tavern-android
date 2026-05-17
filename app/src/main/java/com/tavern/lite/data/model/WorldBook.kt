package com.tavern.lite.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorldBook(
    val entries: Map<String, WorldBookEntryData> = emptyMap()
)

@Serializable
data class WorldBookEntryData(
    val uid: Int = 0,
    val key: List<String> = emptyList(),
    val keysecondary: List<String> = emptyList(),
    val content: String = "",
    val comment: String = "",
    val constant: Boolean = false,
    val vectorized: Boolean = false,
    val selective: Boolean = false,
    @SerialName("selectiveLogic") val selectiveLogic: Int = 0,
    @SerialName("addMemo") val addMemo: Boolean = true,
    val order: Int = 100,
    val position: Int = 0,
    val disable: Boolean = false,
    @SerialName("excludeRecursion") val excludeRecursion: Boolean = false,
    @SerialName("preventRecursion") val preventRecursion: Boolean = false,
    @SerialName("delayUntilRecursion") val delayUntilRecursion: Boolean = false,
    val probability: Int = 100,
    @SerialName("useProbability") val useProbability: Boolean = true,
    val depth: Int = 4,
    val group: String = "",
    @SerialName("groupOverride") val groupOverride: Boolean = false,
    @SerialName("groupWeight") val groupWeight: Int = 100,
    val extensions: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)
