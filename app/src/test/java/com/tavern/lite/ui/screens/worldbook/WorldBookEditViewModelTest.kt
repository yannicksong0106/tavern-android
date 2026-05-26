package com.tavern.lite.ui.screens.worldbook

import androidx.lifecycle.SavedStateHandle
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.data.repository.WorldBookRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorldBookEditViewModelTest {

    @MockK private lateinit var worldBookRepository: WorldBookRepository

    private lateinit var viewModel: WorldBookEditViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val worldBookId = 10L

    private val testBook = WorldBookEntity(id = worldBookId, name = "Test Book")
    private val testEntry = WorldBookEntryEntity(
        id = 1, worldBookId = worldBookId, comment = "Entry 1",
        content = "Some content", keys = """["key1"]"""
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { worldBookRepository.getEntries(any()) } returns flowOf(emptyList())
        coEvery { worldBookRepository.getWorldBookById(worldBookId) } returns testBook
        val savedStateHandle = SavedStateHandle(mapOf("worldBookId" to worldBookId))
        viewModel = WorldBookEditViewModel(savedStateHandle, worldBookRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `init loads world book by id`() = runTest {
        advanceUntilIdle()
        assertEquals(testBook, viewModel.worldBook.value)
    }

    @Test
    fun `init with unknown id sets worldBook to null`() = runTest {
        coEvery { worldBookRepository.getWorldBookById(999) } returns null
        val savedStateHandle = SavedStateHandle(mapOf("worldBookId" to 999L))
        val vm = WorldBookEditViewModel(savedStateHandle, worldBookRepository)
        advanceUntilIdle()
        assertNull(vm.worldBook.value)
    }

    @Test
    fun `updateWorldBook updates name and description`() = runTest {
        coEvery { worldBookRepository.updateWorldBook(any()) } returns Unit
        advanceUntilIdle()

        viewModel.updateWorldBook("New Name", "New Desc")
        advanceUntilIdle()

        coVerify { worldBookRepository.updateWorldBook(match { it.name == "New Name" && it.description == "New Desc" }) }
        assertEquals("New Name", viewModel.worldBook.value?.name)
    }

    @Test
    fun `updateWorldBook does nothing when worldBook is null`() = runTest {
        coEvery { worldBookRepository.getWorldBookById(999) } returns null
        val savedStateHandle = SavedStateHandle(mapOf("worldBookId" to 999L))
        val vm = WorldBookEditViewModel(savedStateHandle, worldBookRepository)
        advanceUntilIdle()

        vm.updateWorldBook("Name", "Desc")
        advanceUntilIdle()

        coVerify(exactly = 0) { worldBookRepository.updateWorldBook(any()) }
    }

    @Test
    fun `addEntry delegates to repository`() = runTest {
        coEvery { worldBookRepository.insertEntry(any()) } returns 50L
        advanceUntilIdle()

        viewModel.addEntry("Comment", "Content", listOf("key"), listOf("sec"), false, true, 0)
        advanceUntilIdle()

        coVerify { worldBookRepository.insertEntry(match {
            it.worldBookId == worldBookId &&
            it.comment == "Comment" &&
            it.content == "Content" &&
            it.constant == false &&
            it.selective == true
        }) }
    }

    @Test
    fun `updateEntry delegates to repository`() = runTest {
        coEvery { worldBookRepository.updateEntry(any()) } returns Unit
        advanceUntilIdle()

        viewModel.updateEntry(testEntry)
        advanceUntilIdle()

        coVerify { worldBookRepository.updateEntry(testEntry) }
    }

    @Test
    fun `deleteEntry delegates to repository`() = runTest {
        coEvery { worldBookRepository.deleteEntry(any()) } returns Unit
        advanceUntilIdle()

        viewModel.deleteEntry(testEntry)
        advanceUntilIdle()

        coVerify { worldBookRepository.deleteEntry(testEntry) }
    }

    @Test
    fun `toggleEntryDisabled flips disabled flag`() = runTest {
        coEvery { worldBookRepository.updateEntry(any()) } returns Unit
        advanceUntilIdle()

        viewModel.toggleEntryDisabled(testEntry)
        advanceUntilIdle()

        coVerify { worldBookRepository.updateEntry(match { it.disabled }) }
    }

    @Test
    fun `toggleEntryDisabled on disabled entry enables it`() = runTest {
        coEvery { worldBookRepository.updateEntry(any()) } returns Unit
        advanceUntilIdle()

        val disabled = testEntry.copy(disabled = true)
        viewModel.toggleEntryDisabled(disabled)
        advanceUntilIdle()

        coVerify { worldBookRepository.updateEntry(match { !it.disabled }) }
    }
}
