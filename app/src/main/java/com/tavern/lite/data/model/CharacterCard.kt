package com.tavern.lite.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CharacterCard(
    val spec: String = "chara_card_v2",
    val data: CharacterData
)

@Serializable
data class CharacterData(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    @SerialName("mes_example") val mesExample: String = "",
    @SerialName("first_mes") val firstMes: String = "",
    val avatar: String = "avatar.png",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    @SerialName("character_version") val characterVersion: String = "1.0",
    @SerialName("system_prompt") val systemPrompt: String? = null,
    @SerialName("post_history_instructions") val postHistoryInstructions: String? = null,
    // v3 扩展字段
    @SerialName("alternate_greetings") val alternateGreetings: List<String> = emptyList(),
    @SerialName("group_only_greetings") val groupOnlyGreetings: List<String> = emptyList(),
    val extensions: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)
