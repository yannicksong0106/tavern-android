package com.tavern.lite.ui.screens.chatlist

import androidx.lifecycle.SavedStateHandle
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.util.ChatExporter
import com.tavern.lite.util.ChatImporter
import com.tavern.lite.util.ExportFormat
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    @MockK private lateinit var characterRepository: CharacterRepository
    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var chatExporter: ChatExporter
    @MockK private lateinit var chatImporter: ChatImporter

    private lateinit var viewModel: ChatListViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        coEvery { characterRepository.getCharacterById(any()) } returns null
        coEvery { chatRepository.getChatsWithLastMessage(any()) } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("characterId" to 1L))
        viewModel = ChatListViewModel(savedStateHandle, characterRepository, chatRepository, chatExporter, chatImporter)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `exportChat rethrows CancellationException`() = runTest {
        coEvery { chatExporter.exportChat(any(), any(), any()) } throws CancellationException()
        var errorEmitted = false
        val job = launch(Dispatchers.Unconfined) {
            viewModel.exportResult.collect { msg ->
                if (msg.startsWith("导出失败")) errorEmitted = true
            }
        }

        viewModel.exportChat(1L, ExportFormat.JSON)
        advanceUntilIdle()

        assertFalse("CE should be rethrown, not caught with error emission", errorEmitted)
        job.cancel()
    }

    @Test
    fun `exportAllChats rethrows CancellationException`() = runTest {
        coEvery { chatExporter.exportAllChats(any(), any()) } throws CancellationException()
        var errorEmitted = false
        val job = launch(Dispatchers.Unconfined) {
            viewModel.exportResult.collect { msg ->
                if (msg.startsWith("导出失败")) errorEmitted = true
            }
        }

        viewModel.exportAllChats(ExportFormat.JSON)
        advanceUntilIdle()

        assertFalse("CE should be rethrown, not caught with error emission", errorEmitted)
        job.cancel()
    }
}
