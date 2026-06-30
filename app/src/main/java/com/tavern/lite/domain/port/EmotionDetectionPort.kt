package com.tavern.lite.domain.port

interface EmotionDetectionPort {
    fun detectEmotion(text: String): String
    fun getSupportedEmotions(): List<String>
}
