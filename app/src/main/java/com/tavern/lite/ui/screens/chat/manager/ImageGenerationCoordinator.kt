package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.usecase.SendMessageUseCase
import com.tavern.lite.network.ImageGenerationService

internal class ImageGenerationCoordinator(
    private val chatId: Long,
    private val imageGenerationService: ImageGenerationService,
    private val sendMessageUseCase: SendMessageUseCase
) {
    suspend fun generateImageReply(
        prompt: String,
        character: CharacterEntity,
        config: ApiConfig,
        isCancelled: () -> Boolean
    ): ImageGenerationResult {
        val imagePath = imageGenerationService.generateImage(prompt, config)
            ?: return ImageGenerationResult.ImageGenerationFailed

        if (isCancelled()) return ImageGenerationResult.Cancelled

        val result = sendMessageUseCase.sendSingleMessage(
            chatId = chatId,
            character = character,
            userContent = "/imagine $prompt",
            config = config,
            imagePaths = listOf(imagePath)
        )
        return ImageGenerationResult.Success(result)
    }

    sealed class ImageGenerationResult {
        data class Success(
            val executionResult: MessageExecutionHelper.ExecutionResult?
        ) : ImageGenerationResult()

        object ImageGenerationFailed : ImageGenerationResult()
        object Cancelled : ImageGenerationResult()
    }
}
