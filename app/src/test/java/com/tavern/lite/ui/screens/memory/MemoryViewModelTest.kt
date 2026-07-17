package com.tavern.lite.ui.screens.memory

import androidx.lifecycle.SavedStateHandle
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.model.MemoryCategory
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.MemoryConsolidator
import com.tavern.lite.data.repository.MemoryRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    @MockK private lateinit var memoryRepository: MemoryRepository
    @MockK private lateinit var characterRepository: CharacterRepository
    @MockK private lateinit var memoryConsolidator: MemoryConsolidator

    private lateinit var viewModel: MemoryViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val characterId = 42L

    private val testAtom = MemoryAtomEntity(
        id = 1, characterId = characterId, content = "Alice likes cats",
        category = "fact", importance = 8
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        every { characterRepository.getAllCharacters() } returns flowOf(emptyList())
        every { memoryRepository.getCategoryCounts(characterId) } returns flowOf(emptyList())
        every { memoryRepository.getAtomsForCharacter(characterId) } returns flowOf(emptyList())
        every { memoryRepository.getAtomCountFlow(characterId) } returns flowOf(0)
        every { memoryRepository.getLastExtractionTime(characterId) } returns flowOf(null)
        coEvery { memoryRepository.purgeExpired() } returns Unit

        val savedStateHandle = SavedStateHandle(mapOf("characterId" to characterId))
        viewModel = MemoryViewModel(savedStateHandle, memoryRepository, characterRepository, memoryConsolidator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ==================== character selection ====================

    @Test
    fun `initial characterId from savedState`() = runTest {
        assertEquals(characterId, viewModel.selectedCharacterId.value)
    }

    @Test
    fun `selectCharacter updates selectedCharacterId and resets category`() {
        viewModel.selectCategory(MemoryCategory.FACT)
        viewModel.selectCharacter(99L)
        assertEquals(99L, viewModel.selectedCharacterId.value)
        assertNull(viewModel.selectedCategory.value)
    }

    @Test
    fun `selectCharacter resets search`() {
        viewModel.updateSearch("query")
        viewModel.setSearchActive(true)
        viewModel.selectCharacter(99L)
        assertEquals("", viewModel.searchQuery.value)
        assertFalse(viewModel.searchActive.value)
    }

    // ==================== category ====================

    @Test
    fun `selectCategory updates selectedCategory`() {
        viewModel.selectCategory(MemoryCategory.EMOTION)
        assertEquals(MemoryCategory.EMOTION, viewModel.selectedCategory.value)
    }

    @Test
    fun `selectCategory null means all`() {
        viewModel.selectCategory(MemoryCategory.FACT)
        viewModel.selectCategory(null)
        assertNull(viewModel.selectedCategory.value)
    }

    // ==================== search ====================

    @Test
    fun `updateSearch updates searchQuery`() {
        viewModel.updateSearch("cats")
        assertEquals("cats", viewModel.searchQuery.value)
    }

    @Test
    fun `setSearchActive false clears query`() {
        viewModel.updateSearch("query")
        viewModel.setSearchActive(false)
        assertEquals("", viewModel.searchQuery.value)
        assertFalse(viewModel.searchActive.value)
    }

    @Test
    fun `setSearchActive true sets active`() {
        viewModel.setSearchActive(true)
        assertTrue(viewModel.searchActive.value)
    }

    // ==================== sort ====================

    @Test
    fun `setSortMode updates sortMode`() {
        viewModel.setSortMode(MemoryViewModel.SortMode.RECENCY)
        assertEquals(MemoryViewModel.SortMode.RECENCY, viewModel.sortMode.value)
    }

    // ==================== edit dialog ====================

    @Test
    fun `startEdit sets editingAtom`() {
        viewModel.startEdit(testAtom)
        assertEquals(testAtom, viewModel.editingAtom.value)
    }

    @Test
    fun `clearEdit sets editingAtom to null`() {
        viewModel.startEdit(testAtom)
        viewModel.clearEdit()
        assertNull(viewModel.editingAtom.value)
    }

    // ==================== addAtom ====================

    @Test
    fun `addAtom with blank content does nothing`() = runTest {
        viewModel.addAtom("", MemoryCategory.FACT, 5)
        advanceUntilIdle()
        coVerify(exactly = 0) { memoryRepository.insertAtom(any()) }
    }

    @Test
    fun `addAtom inserts atom with correct fields`() = runTest {
        coEvery { memoryRepository.insertAtom(any()) } returns 1L
        viewModel.addAtom("New fact", MemoryCategory.FACT, 7)
        advanceUntilIdle()
        coVerify { memoryRepository.insertAtom(match {
            it.characterId == characterId &&
            it.content == "New fact" &&
            it.category == "fact" &&
            it.importance == 7 &&
            it.source == "manual"
        }) }
    }

    @Test
    fun `addAtom with TEMPORARY category sets expiresAt`() = runTest {
        coEvery { memoryRepository.insertAtom(any()) } returns 1L
        viewModel.addAtom("Temp note", MemoryCategory.TEMPORARY, 5)
        advanceUntilIdle()
        coVerify { memoryRepository.insertAtom(match { it.expiresAt != null }) }
    }

    @Test
    fun `addAtom with non-TEMPORARY category has no expiresAt`() = runTest {
        coEvery { memoryRepository.insertAtom(any()) } returns 1L
        viewModel.addAtom("Permanent", MemoryCategory.FACT, 5)
        advanceUntilIdle()
        coVerify { memoryRepository.insertAtom(match { it.expiresAt == null }) }
    }

    // ==================== updateAtom / deleteAtom / deleteAll ====================

    @Test
    fun `updateAtom delegates to repository`() = runTest {
        coEvery { memoryRepository.updateAtom(any()) } returns Unit
        viewModel.updateAtom(testAtom)
        advanceUntilIdle()
        coVerify { memoryRepository.updateAtom(testAtom) }
    }

    @Test
    fun `deleteAtom delegates to repository`() = runTest {
        coEvery { memoryRepository.deleteAtom(any()) } returns Unit
        viewModel.deleteAtom(1L)
        advanceUntilIdle()
        coVerify { memoryRepository.deleteAtom(1L) }
    }

    @Test
    fun `deleteAll deletes atoms and memories for character`() = runTest {
        coEvery { memoryRepository.deleteAllAtomsForCharacter(any()) } returns Unit
        coEvery { memoryRepository.deleteAllForCharacter(any()) } returns Unit
        viewModel.deleteAll()
        advanceUntilIdle()
        coVerify { memoryRepository.deleteAllAtomsForCharacter(characterId) }
        coVerify { memoryRepository.deleteAllForCharacter(characterId) }
    }

    // ==================== auto-select first character ====================

    @Test
    fun `auto-selects first character when initial id is 0`() = runTest {
        val chars = listOf(CharacterEntity(id = 5, name = "First"))
        every { characterRepository.getAllCharacters() } returns flowOf(chars)
        every { memoryRepository.getCategoryCounts(5) } returns flowOf(emptyList())
        every { memoryRepository.getAtomsForCharacter(5) } returns flowOf(emptyList())
        every { memoryRepository.getAtomCountFlow(5) } returns flowOf(0)
        every { memoryRepository.getLastExtractionTime(5) } returns flowOf(null)

        val savedStateHandle = SavedStateHandle(mapOf("characterId" to 0L))
        val vm = MemoryViewModel(savedStateHandle, memoryRepository, characterRepository, memoryConsolidator)
        advanceUntilIdle()

        assertEquals(5L, vm.selectedCharacterId.value)
    }
}
