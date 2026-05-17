package com.tavern.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BubbleStyleConfig(
    val userBubbleColor: Long = 0L,       // 0 = use theme default
    val assistantBubbleColor: Long = 0L,  // 0 = use theme default
    val cornerRadius: Int = 16,           // dp
    val fontSize: Int = 15,               // sp
    val dynamicColor: Boolean = false,    // Material You
)
