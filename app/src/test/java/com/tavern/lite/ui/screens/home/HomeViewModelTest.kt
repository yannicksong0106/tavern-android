package com.tavern.lite.ui.screens.home

import android.content.Context
import android.net.Uri
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.util.SillyTavernImporter
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var characterRepository: CharacterRepository
    @MockK private lateinit var chatRepository: ChatRepository
    @MockK private lateinit var importer: SillyTavernImporter

    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir"), "test_cache")
        every { context.contentResolver } returns mockk()
        coEvery { characterRepository.getAllCharacters() } returns flowOf(emptyList())
        coEvery { chatRepository.getAllGroupChats() } returns flowOf(emptyList())

        viewModel = HomeViewModel(context, characterRepository, chatRepository, importer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `exportCharacter rethrows CancellationException`() = runTest {
        coEvery { characterRepository.getCharacterById(any()) } throws CancellationException()
        var errorEmitted = false
        val job = launch(Dispatchers.Unconfined) {
            viewModel.importResult.collect { msg ->
                if (msg.startsWith("导出失败")) errorEmitted = true
            }
        }

        viewModel.exportCharacter(1L)
        advanceUntilIdle()

        assertFalse("CE should be rethrown, not caught with error emission", errorEmitted)
        job.cancel()
    }

    @Test
    fun `exportCharacterPng rethrows CancellationException`() = runTest {
        coEvery { characterRepository.getCharacterById(any()) } throws CancellationException()
        var errorEmitted = false
        val job = launch(Dispatchers.Unconfined) {
            viewModel.importResult.collect { msg ->
                if (msg.startsWith("PNG 导出失败")) errorEmitted = true
            }
        }

        viewModel.exportCharacterPng(1L)
        advanceUntilIdle()

        assertFalse("CE should be rethrown, not caught with error emission", errorEmitted)
        job.cancel()
    }

    @Test
    fun `importCharacter rethrows CancellationException`() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver.getType(uri) } throws CancellationException()
        var errorEmitted = false
        val job = launch(Dispatchers.Unconfined) {
            viewModel.importResult.collect { msg ->
                if (msg.startsWith("导入失败")) errorEmitted = true
            }
        }

        viewModel.importCharacter(uri)
        advanceUntilIdle()

        assertFalse("CE should be rethrown, not caught with error emission", errorEmitted)
        job.cancel()
    }
}
