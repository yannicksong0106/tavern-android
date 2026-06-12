package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.usecase.SendMessageUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationSendCoordinatorTest {

    @MockK private lateinit var sendMessageUseCase: SendMessageUseCase

    private val chatId = 10L
    private val config = ApiConfig(userName = "Tester")
    private val alice = CharacterEntity(id = 1L, name = "Alice")
    private val bob = CharacterEntity(id = 2L, name = "Bob")
    private lateinit var coordinator: GenerationSendCoordinator

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        coordinator = GenerationSendCoordinator(
            chatId = chatId,
            sendMessageUseCase = sendMessageUseCase,
            random = Random(1)
        )
    }

    @Test
    fun `sendSingle delegates to use case with image paths`() = runTest {
        val expected = MessageExecutionHelper.ExecutionResult(assistantMsgId = 88L, fullResponse = "reply")
        coEvery {
            sendMessageUseCase.sendSingleMessage(chatId, alice, "hello", config, null, listOf("image.png"))
        } returns expected

        val result = coordinator.sendSingle(
            character = alice,
            content = "hello",
            config = config,
            imagePaths = listOf("image.png")
        )

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            sendMessageUseCase.sendSingleMessage(chatId, alice, "hello", config, null, listOf("image.png"))
        }
    }

    @Test
    fun `sendDirect delegates target and group characters`() = runTest {
        val characters = listOf(alice, bob)
        val expected = MessageExecutionHelper.ExecutionResult(assistantMsgId = 99L, fullResponse = "direct")
        coEvery {
            sendMessageUseCase.sendDirectMessage(chatId, characters, bob, "hi", config, emptyList())
        } returns expected

        val result = coordinator.sendDirect(
            characters = characters,
            targetCharacter = bob,
            content = "hi",
            config = config,
            imagePaths = emptyList()
        )

        assertEquals(expected, result)
        coVerify(exactly = 1) {
            sendMessageUseCase.sendDirectMessage(chatId, characters, bob, "hi", config, emptyList())
        }
    }

    @Test
    fun `sendGroup selects responders and commits returned assistant ids in order`() = runTest {
        val characters = listOf(alice, bob)
        val respondingCharacters = mutableListOf<CharacterEntity?>()
        val committedIds = mutableListOf<Long?>()
        coEvery {
            sendMessageUseCase.sendGroupMessage(chatId, characters, "hello", config, emptyList())
        } returns listOf(
            alice.id to MessageExecutionHelper.ExecutionResult(assistantMsgId = 11L, fullResponse = "a"),
            bob.id to MessageExecutionHelper.ExecutionResult(assistantMsgId = 22L, fullResponse = "b")
        )

        coordinator.sendGroup(
            characters = characters,
            content = "hello",
            config = config,
            imagePaths = emptyList(),
            schedulingStrategy = GroupSchedulingStrategy.LIST_ORDER,
            chattinessByCharacterId = emptyMap(),
            intervalMs = 1L,
            isCancelled = { false },
            onRespondingCharacterChanged = { respondingCharacters += it },
            onAssistantReplyCommit = {
                committedIds += it
                true
            }
        )

        assertEquals(listOf(alice, bob), respondingCharacters)
        assertEquals(listOf(11L, 22L), committedIds)
    }

    @Test
    fun `sendGroup stops committing after cancellation`() = runTest {
        val characters = listOf(alice, bob)
        val committedIds = mutableListOf<Long?>()
        var cancelled = false
        coEvery {
            sendMessageUseCase.sendGroupMessage(chatId, characters, "hello", config, emptyList())
        } returns listOf(
            alice.id to MessageExecutionHelper.ExecutionResult(assistantMsgId = 11L, fullResponse = "a"),
            bob.id to MessageExecutionHelper.ExecutionResult(assistantMsgId = 22L, fullResponse = "b")
        )

        coordinator.sendGroup(
            characters = characters,
            content = "hello",
            config = config,
            imagePaths = emptyList(),
            schedulingStrategy = GroupSchedulingStrategy.LIST_ORDER,
            chattinessByCharacterId = emptyMap(),
            intervalMs = 1L,
            isCancelled = { cancelled },
            onRespondingCharacterChanged = {},
            onAssistantReplyCommit = {
                committedIds += it
                cancelled = true
                true
            }
        )

        assertEquals(listOf(11L), committedIds)
    }

    @Test
    fun `sendGroup delays between multiple assistant replies`() = runTest {
        val characters = listOf(alice, bob)
        val committedIds = mutableListOf<Long?>()
        coEvery {
            sendMessageUseCase.sendGroupMessage(chatId, characters, "hello", config, emptyList())
        } returns listOf(
            alice.id to MessageExecutionHelper.ExecutionResult(assistantMsgId = 11L, fullResponse = "a"),
            bob.id to MessageExecutionHelper.ExecutionResult(assistantMsgId = 22L, fullResponse = "b")
        )

        val job = backgroundScope.launch {
            coordinator.sendGroup(
                characters = characters,
                content = "hello",
                config = config,
                imagePaths = emptyList(),
                schedulingStrategy = GroupSchedulingStrategy.LIST_ORDER,
                chattinessByCharacterId = emptyMap(),
                intervalMs = 100L,
                isCancelled = { false },
                onRespondingCharacterChanged = {},
                onAssistantReplyCommit = {
                    committedIds += it
                    true
                }
            )
        }

        runCurrent()
        assertEquals(listOf(11L), committedIds)
        advanceTimeBy(99L)
        assertEquals(listOf(11L), committedIds)
        advanceTimeBy(30L)
        assertEquals(listOf(11L, 22L), committedIds)
        job.cancel()
    }
}
