package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.port.ImageGenerationPort
import com.tavern.lite.domain.usecase.SendMessageUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImageGenerationCoordinatorTest {

    @MockK private lateinit var imageGenerationService: ImageGenerationPort
    @MockK private lateinit var sendMessageUseCase: SendMessageUseCase

    private val chatId = 10L
    private val character = CharacterEntity(id = 1L, name = "Alice")
    private val config = ApiConfig()
    private lateinit var coordinator: ImageGenerationCoordinator

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        coordinator = ImageGenerationCoordinator(
            chatId = chatId,
            imageGenerationService = imageGenerationService,
            sendMessageUseCase = sendMessageUseCase
        )
    }

    @Test
    fun `generateImageReply sends generated image through single message flow`() = runTest {
        coEvery { imageGenerationService.generateImage("cat", config) } returns "/tmp/cat.png"
        coEvery {
            sendMessageUseCase.sendSingleMessage(chatId, character, "/imagine cat", config, null, listOf("/tmp/cat.png"))
        } returns MessageExecutionHelper.ExecutionResult(assistantMsgId = 88L, fullResponse = "done")

        val result = coordinator.generateImageReply(
            prompt = "cat",
            character = character,
            config = config,
            isCancelled = { false }
        )

        assertTrue(result is ImageGenerationCoordinator.ImageGenerationResult.Success)
        assertEquals(
            88L,
            (result as ImageGenerationCoordinator.ImageGenerationResult.Success).executionResult?.assistantMsgId
        )
        coVerify(exactly = 1) {
            sendMessageUseCase.sendSingleMessage(chatId, character, "/imagine cat", config, null, listOf("/tmp/cat.png"))
        }
    }

    @Test
    fun `generateImageReply returns failure when image service returns null`() = runTest {
        coEvery { imageGenerationService.generateImage("cat", config) } returns null

        val result = coordinator.generateImageReply(
            prompt = "cat",
            character = character,
            config = config,
            isCancelled = { false }
        )

        assertTrue(result is ImageGenerationCoordinator.ImageGenerationResult.ImageGenerationFailed)
        coVerify(exactly = 0) { sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `generateImageReply does not send generated image after cancellation`() = runTest {
        coEvery { imageGenerationService.generateImage("cat", config) } returns "/tmp/cat.png"

        val result = coordinator.generateImageReply(
            prompt = "cat",
            character = character,
            config = config,
            isCancelled = { true }
        )

        assertTrue(result is ImageGenerationCoordinator.ImageGenerationResult.Cancelled)
        coVerify(exactly = 0) { sendMessageUseCase.sendSingleMessage(any(), any(), any(), any(), any(), any()) }
    }
}
