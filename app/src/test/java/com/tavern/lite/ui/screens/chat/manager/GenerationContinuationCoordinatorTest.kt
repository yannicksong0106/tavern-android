package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.usecase.ContinueGenerationUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GenerationContinuationCoordinatorTest {

    @MockK private lateinit var continueGenerationUseCase: ContinueGenerationUseCase

    private val chatId = 10L
    private val characterId = 1L
    private val character = CharacterEntity(id = characterId, name = "Alice")
    private val config = ApiConfig(userName = "Tester")
    private lateinit var coordinator: GenerationContinuationCoordinator

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        coordinator = GenerationContinuationCoordinator(
            chatId = chatId,
            characterId = characterId,
            continueGenerationUseCase = continueGenerationUseCase
        )
    }

    @Test
    fun `resolveContinueRequest uses last assistant message`() {
        val request = coordinator.resolveContinueRequest(
            listOf(
                message(id = 1L, role = "assistant", content = "old"),
                message(id = 2L, role = "user", content = "hello"),
                message(id = 3L, role = "assistant", content = "latest")
            )
        )

        assertEquals(
            GenerationContinuationCoordinator.ContinueRequest(
                assistantMessageId = 3L,
                assistantContent = "latest"
            ),
            request
        )
    }

    @Test
    fun `resolveContinueRequest returns null without assistant message`() {
        val request = coordinator.resolveContinueRequest(
            listOf(message(id = 1L, role = "user", content = "hello"))
        )

        assertNull(request)
    }

    @Test
    fun `resolveRegenerateRequest uses nearest previous user message`() {
        val request = coordinator.resolveRegenerateRequest(
            messages = listOf(
                message(id = 1L, role = "user", content = "first"),
                message(id = 2L, role = "assistant", content = "old reply"),
                message(id = 3L, role = "user", content = "target prompt"),
                message(id = 4L, role = "assistant", content = "target reply")
            ),
            messageId = 4L
        )

        assertEquals(
            GenerationContinuationCoordinator.RegenerateRequest(
                assistantMessageId = 4L,
                userContent = "target prompt"
            ),
            request
        )
    }

    @Test
    fun `resolveRegenerateRequest returns null for non assistant target`() {
        val request = coordinator.resolveRegenerateRequest(
            messages = listOf(message(id = 1L, role = "user", content = "hello")),
            messageId = 1L
        )

        assertNull(request)
    }

    @Test
    fun `resolveRegenerateRequest returns null without previous user message`() {
        val request = coordinator.resolveRegenerateRequest(
            messages = listOf(
                message(id = 1L, role = "system", content = "context"),
                message(id = 2L, role = "assistant", content = "reply")
            ),
            messageId = 2L
        )

        assertNull(request)
    }

    @Test
    fun `continueGeneration delegates request with previous reasoning`() = runTest {
        val request = GenerationContinuationCoordinator.ContinueRequest(
            assistantMessageId = 88L,
            assistantContent = "partial"
        )
        coEvery {
            continueGenerationUseCase.continueGeneration(
                chatId = chatId,
                characterId = characterId,
                character = character,
                lastAssistantMsgId = 88L,
                lastAssistantContent = "partial",
                config = config,
                previousReasoningContent = "reasoning"
            )
        } returns MessageExecutionHelper.ExecutionResult(
            assistantMsgId = 88L,
            fullResponse = " continued",
            reasoningContent = "next reasoning"
        )

        val result = coordinator.continueGeneration(
            request = request,
            character = character,
            config = config,
            previousReasoningContent = "reasoning"
        )

        assertEquals(88L, result?.assistantMsgId)
        assertEquals("next reasoning", result?.reasoningContent)
        coVerify(exactly = 1) {
            continueGenerationUseCase.continueGeneration(
                chatId,
                characterId,
                character,
                88L,
                "partial",
                config,
                previousReasoningContent = "reasoning"
            )
        }
    }

    @Test
    fun `regenerate delegates request with user content`() = runTest {
        val request = GenerationContinuationCoordinator.RegenerateRequest(
            assistantMessageId = 99L,
            userContent = "try again"
        )
        coEvery {
            continueGenerationUseCase.regenerate(
                chatId = chatId,
                characterId = characterId,
                character = character,
                messageId = 99L,
                userMessageContent = "try again",
                config = config,
                previousReasoningContent = null
            )
        } returns MessageExecutionHelper.ExecutionResult(
            assistantMsgId = 99L,
            fullResponse = "new reply"
        )

        val result = coordinator.regenerate(
            request = request,
            character = character,
            config = config,
            previousReasoningContent = null
        )

        assertEquals(99L, result?.assistantMsgId)
        coVerify(exactly = 1) {
            continueGenerationUseCase.regenerate(
                chatId,
                characterId,
                character,
                99L,
                "try again",
                config,
                previousReasoningContent = null
            )
        }
    }

    private fun message(
        id: Long,
        role: String,
        content: String
    ) = MessageEntity(
        id = id,
        chatId = chatId,
        role = role,
        content = content
    )
}
