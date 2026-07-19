package com.tavern.lite.ui.screens.worldbook

import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.data.importexport.LorebookExporter
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorldBookListViewModelTest {

    @MockK private lateinit var worldBookRepository: WorldBookRepository
    @MockK private lateinit var lorebookExporter: LorebookExporter

    private lateinit var viewModel: WorldBookListViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testBook = WorldBookEntity(id = 1, name = "Test Book")

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { worldBookRepository.getAllWorldBooks() } returns flowOf(emptyList())
        viewModel = WorldBookListViewModel(worldBookRepository, lorebookExporter)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `createWorldBook delegates to repository and invokes callback`() = runTest {
        coEvery { worldBookRepository.createWorldBook(any(), any()) } returns 100L

        var callbackId = 0L
        viewModel.createWorldBook("New Book", "Description") { callbackId = it }
        advanceUntilIdle()

        coVerify { worldBookRepository.createWorldBook("New Book", "Description") }
        assertEquals(100L, callbackId)
    }

    @Test
    fun `deleteWorldBook delegates to repository`() = runTest {
        coEvery { worldBookRepository.deleteWorldBook(any()) } returns Unit
        viewModel.deleteWorldBook(testBook)
        advanceUntilIdle()
        coVerify { worldBookRepository.deleteWorldBook(testBook) }
    }

    @Test
    fun `exportWorldBook calls exporter and invokes callback`() = runTest {
        val entries = listOf(WorldBookEntryEntity(id = 1, worldBookId = 1, content = "entry"))
        every { worldBookRepository.getEntries(1) } returns flowOf(entries)
        coEvery { lorebookExporter.exportToJson(any(), any()) } returns """{"entries":[]}"""

        var resultJson = ""
        viewModel.exportWorldBook(testBook) { resultJson = it }
        advanceUntilIdle()

        coVerify { lorebookExporter.exportToJson(testBook, entries) }
        assertEquals("""{"entries":[]}""", resultJson)
    }

    @Test
    fun `importWorldBook calls exporter and inserts entries`() = runTest {
        val importedEntries = listOf(
            WorldBookEntryEntity(id = 0, worldBookId = 5, content = "imported")
        )
        coEvery { lorebookExporter.importFromJson(any(), any()) } returns importedEntries
        coEvery { worldBookRepository.insertEntry(any()) } returns 1L

        viewModel.importWorldBook("""{"entries":[]}""", 5L)
        advanceUntilIdle()

        coVerify { lorebookExporter.importFromJson("""{"entries":[]}""", 5L) }
        coVerify { worldBookRepository.insertEntry(importedEntries[0]) }
        assertFalse(viewModel.importError.value)
        coVerify(exactly = 0) { worldBookRepository.deleteWorldBook(any()) }
    }

    @Test
    fun `importWorldBook empty list is success and does not rollback`() = runTest {
        coEvery { lorebookExporter.importFromJson(any(), any()) } returns emptyList()

        viewModel.importWorldBook("""{"entries":{}}""", 9L)
        advanceUntilIdle()

        coVerify(exactly = 0) { worldBookRepository.insertEntry(any()) }
        coVerify(exactly = 0) { worldBookRepository.deleteWorldBook(any()) }
        assertFalse(viewModel.importError.value)
    }

    @Test
    fun `importWorldBook parse failure rolls back orphan book and signals error`() = runTest {
        val orphan = WorldBookEntity(id = 42, name = "Imported Lorebook")
        coEvery { lorebookExporter.importFromJson(any(), any()) } returns null
        coEvery { worldBookRepository.getWorldBookById(42L) } returns orphan
        coEvery { worldBookRepository.deleteWorldBook(orphan) } returns Unit

        viewModel.importWorldBook("""{"entries":""", 42L)
        advanceUntilIdle()

        coVerify { worldBookRepository.getWorldBookById(42L) }
        coVerify { worldBookRepository.deleteWorldBook(orphan) }
        coVerify(exactly = 0) { worldBookRepository.insertEntry(any()) }
        assertTrue(viewModel.importError.value)

        viewModel.clearImportError()
        assertFalse(viewModel.importError.value)
    }

    @Test
    fun `importWorldBook parse failure still signals error when book already gone`() = runTest {
        coEvery { lorebookExporter.importFromJson(any(), any()) } returns null
        coEvery { worldBookRepository.getWorldBookById(7L) } returns null

        viewModel.importWorldBook("not-json", 7L)
        advanceUntilIdle()

        coVerify(exactly = 0) { worldBookRepository.deleteWorldBook(any()) }
        coVerify(exactly = 0) { worldBookRepository.insertEntry(any()) }
        assertTrue(viewModel.importError.value)
    }
}
