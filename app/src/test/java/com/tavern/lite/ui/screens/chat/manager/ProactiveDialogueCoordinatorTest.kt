package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.domain.helper.MessageExecutionHelper
import com.tavern.lite.domain.usecase.ProactiveDialogueUseCase
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.network.ApiConfigStore
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProactiveDialogueCoordinatorTest {

    @MockK private lateinit var apiConfigStore: ApiConfigStore
    @MockK private lateinit var proactiveMessageUseCase: ProactiveMessageUseCase
    @MockK private lateinit var proactiveDialogueUseCase: ProactiveDialogueUseCase

    private val chatId = 10L
    private val config = ApiConfig(userName = "Tester")

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        every { apiConfigStore.configFlow } returns MutableStateFlow(config)
    }

    @Test
    fun `scheduleSingle sends proactive reply after delay and commits without emotion update`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val character = CharacterEntity(id = 1L, name = "Alice", chattiness = 100)
        val coordinator = createCoordinator(dispatcher)
        var isGenerating = false
        val commits = mutableListOf<CommitCall>()

        coordinator.characterProvider = { character }
        coordinator.isGeneratingProvider = { isGenerating }
        coordinator.onGeneratingChanged = { isGenerating = it }
        coordinator.onAssistantReplyCommit = { assistantMsgId, updateEmotion, respectCancellation ->
            commits += CommitCall(assistantMsgId, updateEmotion, respectCancellation)
            true
        }
        every { proactiveDialogueUseCase.shouldScheduleProactive(character.chattiness) } returns 25L
        coEvery {
            proactiveMessageUseCase.sendProactiveMessage(chatId, character, config)
        } returns MessageExecutionHelper.ExecutionResult(assistantMsgId = 88L, fullResponse = "reply")

        coordinator.scheduleSingle()
        advanceTimeBy(25L)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            proactiveMessageUseCase.sendProactiveMessage(chatId, character, config)
        }
        assertEquals(listOf(CommitCall(88L, updateEmotion = false, respectCancellation = false)), commits)
        assertFalse(isGenerating)
    }

    @Test
    fun `scheduleGroup selects next proactive character and clears responding character`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val alice = CharacterEntity(id = 1L, name = "Alice", chattiness = 20)
        val bob = CharacterEntity(id = 2L, name = "Bob", chattiness = 80)
        val characters = listOf(alice, bob)
        val coordinator = createCoordinator(dispatcher)
        val respondingCharacters = mutableListOf<CharacterEntity?>()

        coordinator.groupCharactersProvider = { characters }
        coordinator.isGroupChatProvider = { true }
        coordinator.isGeneratingProvider = { false }
        coordinator.onRespondingCharacterChanged = { respondingCharacters += it }
        every { proactiveDialogueUseCase.shouldScheduleGroupProactive(characters) } returns 10L
        every { proactiveDialogueUseCase.selectNextProactiveCharacter(characters) } returns bob
        coEvery {
            proactiveMessageUseCase.sendProactiveGroupMessage(chatId, characters, bob, config)
        } returns MessageExecutionHelper.ExecutionResult(assistantMsgId = 99L, fullResponse = "group reply")

        coordinator.scheduleGroup()
        advanceTimeBy(10L)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            proactiveMessageUseCase.sendProactiveGroupMessage(chatId, characters, bob, config)
        }
        assertEquals(listOf(bob, null), respondingCharacters)
    }

    @Test
    fun `triggerIfNeeded directs group assistant chain to next character`() {
        val dispatcher = StandardTestDispatcher()
        val alice = CharacterEntity(id = 1L, name = "Alice")
        val bob = CharacterEntity(id = 2L, name = "Bob")
        val coordinator = createCoordinator(dispatcher)
        var directCall: Pair<String, CharacterEntity>? = null

        coordinator.groupCharactersProvider = { listOf(alice, bob) }
        coordinator.isGroupChatProvider = { true }
        coordinator.triggerIfNeeded(
            currentMessages = listOf(
                MessageEntity(
                    id = 1L,
                    chatId = chatId,
                    role = "assistant",
                    content = "hello",
                    characterId = alice.id
                )
            ),
            sendSingleChatMessage = { error("single should not be called") },
            sendGroupChatMessage = { error("group should not be called") },
            sendDirectMessage = { content, character -> directCall = content to character }
        )

        assertEquals("" to bob, directCall)
        verify(exactly = 0) { proactiveDialogueUseCase.shouldScheduleGroupProactive(any()) }
    }

    private fun createCoordinator(dispatcher: TestDispatcher) = ProactiveDialogueCoordinator(
        chatId = chatId,
        apiConfigStore = apiConfigStore,
        proactiveMessageUseCase = proactiveMessageUseCase,
        proactiveDialogueUseCase = proactiveDialogueUseCase,
        scope = CoroutineScope(dispatcher),
        streamingMutex = Mutex()
    )

    private data class CommitCall(
        val assistantMsgId: Long?,
        val updateEmotion: Boolean,
        val respectCancellation: Boolean,
    )
}
