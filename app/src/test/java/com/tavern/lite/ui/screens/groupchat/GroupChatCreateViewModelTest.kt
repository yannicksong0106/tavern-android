package com.tavern.lite.ui.screens.groupchat

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.GroupChatRepository
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupChatCreateViewModelTest {

    @MockK private lateinit var characterRepository: CharacterRepository
    @MockK private lateinit var groupChatRepository: GroupChatRepository

    private lateinit var viewModel: GroupChatCreateViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { characterRepository.getAllCharacters() } returns flowOf(emptyList())
        viewModel = GroupChatCreateViewModel(characterRepository, groupChatRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `createGroupChat calls repository and invokes callback`() = runTest {
        coEvery { groupChatRepository.createGroupChat(any()) } returns 100L

        var callbackChatId = 0L
        var callbackPrimaryId = 0L
        viewModel.createGroupChat(listOf(1L, 2L)) { chatId, primaryId ->
            callbackChatId = chatId
            callbackPrimaryId = primaryId
        }
        advanceUntilIdle()

        coVerify { groupChatRepository.createGroupChat(listOf(1L, 2L)) }
        assertEquals(100L, callbackChatId)
        assertEquals(1L, callbackPrimaryId)
    }

    @Test
    fun `createGroupChat passes first characterId as primary`() = runTest {
        coEvery { groupChatRepository.createGroupChat(any()) } returns 200L

        var primaryId = 0L
        viewModel.createGroupChat(listOf(5L, 10L, 15L)) { _, p -> primaryId = p }
        advanceUntilIdle()

        assertEquals(5L, primaryId)
    }
}
