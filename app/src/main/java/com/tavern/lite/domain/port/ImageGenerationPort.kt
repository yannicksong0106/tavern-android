package com.tavern.lite.domain.port

import com.tavern.lite.data.model.ApiConfig

interface ImageGenerationPort {
    suspend fun generateImage(
        prompt: String,
        config: ApiConfig,
        size: String = "1024x1024"
    ): String?
}
