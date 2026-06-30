package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.model.ChatMessage
import com.tavern.lite.domain.port.PromptBuilderPort
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProactiveMessageUseCaseTest {

    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var presetRepository: PresetRepository
    @MockK private lateinit var helper: MessageExecutionHelper
    @MockK private lateinit var promptBuilder: PromptBuilderPort

    private lateinit var useCase: ProactiveMessageUseCase
    private val testDispatcher = StandardTestDispatcher()

    private val character = CharacterEntity(id = 42, name = "Alice", chattiness = 50)
    private val config = ApiConfig(contextLength = 20, userName = "User")

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)
        useCase = ProactiveMessageUseCase(chatRepository, presetRepository, helper, promptBuilder)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== sendProactiveMessage ====================

    @Test
    fun `sendProactiveMessage returns null when chat history is empty`() = runTest {
        coEvery { chatRepository.getRecentMessages(1L, 20) } returns emptyList()

        val result = useCase.sendProactiveMessage(1L, character, config)

        assertNull(result)
    }

    @Test
    fun `sendProactiveMessage builds prompt and executes`() = runTest {
        val messages = listOf(
            MessageEntity(id = 1, chatId = 1, role = "user", content = "hello")
        )
        coEvery { chatRepository.getRecentMessages(1L, 20) } returns messages
        coEvery { helper.personasafe(42L) } returns null
        coEvery { presetRepository.resolveEffectivePreset(1L, 42L) } returns null
        val promptMessages = listOf(ChatMessage("user", "hello"))
        every { promptBuilder.buildProactive(any()) } returns promptMessages
        val expectedResult = MessageExecutionHelper.ExecutionResult(
            assistantMsgId = 10L, fullResponse = "hi"
        )
        coEvery {
            helper.executeAndSave(1L, 42L, "Alice", promptMessages, config, "")
        } returns expectedResult

        val result = useCase.sendProactiveMessage(1L, character, config)

        advanceUntilIdle()
        assertSame(expectedResult, result)
        coVerify { helper.executeAndSave(1L, 42L, "Alice", promptMessages, config, "") }
    }

    // ==================== sendProactiveGroupMessage ====================

    @Test
    fun `sendProactiveGroupMessage returns null when chat history is empty`() = runTest {
        coEvery { chatRepository.getRecentMessages(1L, 20) } returns emptyList()

        val result = useCase.sendProactiveGroupMessage(1L, listOf(character), character, config)

        assertNull(result)
    }

    @Test
    fun `sendProactiveGroupMessage builds group prompt and executes`() = runTest {
        val characters = listOf(character, CharacterEntity(id = 43, name = "Bob"))
        val messages = listOf(
            MessageEntity(id = 1, chatId = 1, role = "user", content = "hello")
        )
        coEvery { chatRepository.getRecentMessages(1L, 20) } returns messages
        coEvery { helper.personasafe(42L) } returns null
        coEvery { presetRepository.resolveEffectivePreset(1L, 42L) } returns null
        val promptMessages = listOf(ChatMessage("user", "hello"))
        every { promptBuilder.buildGroupProactive(any()) } returns promptMessages
        val expectedResult = MessageExecutionHelper.ExecutionResult(
            assistantMsgId = 11L, fullResponse = "group reply"
        )
        coEvery {
            helper.executeAndSave(1L, 42L, "Alice", promptMessages, config, "")
        } returns expectedResult

        val result = useCase.sendProactiveGroupMessage(1L, characters, character, config)

        advanceUntilIdle()
        assertSame(expectedResult, result)
    }
}
