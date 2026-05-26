package com.tavern.lite.ui.screens.character

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.model.CharacterData
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.WorldBookRepository
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
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
class CharacterEditViewModelTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var characterRepository: CharacterRepository
    @MockK private lateinit var worldBookRepository: WorldBookRepository
    @MockK private lateinit var authorNoteDao: AuthorNoteDao

    private lateinit var viewModel: CharacterEditViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testCharacter = CharacterEntity(
        id = 42, name = "Alice", description = "A character",
        personality = "Friendly", firstMes = "Hello!", chattiness = 60
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { worldBookRepository.getAllWorldBooks() } returns flowOf(emptyList())
        viewModel = CharacterEditViewModel(context, characterRepository, worldBookRepository, authorNoteDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ==================== updateField ====================

    @Test
    fun `updateField name updates state`() {
        viewModel.updateField("name", "Bob")
        assertEquals("Bob", viewModel.state.value.name)
    }

    @Test
    fun `updateField description updates state`() {
        viewModel.updateField("description", "New desc")
        assertEquals("New desc", viewModel.state.value.description)
    }

    @Test
    fun `updateField personality updates state`() {
        viewModel.updateField("personality", "Shy")
        assertEquals("Shy", viewModel.state.value.personality)
    }

    @Test
    fun `updateField chattiness coerces to 0-100`() {
        viewModel.updateField("chattiness", "150")
        assertEquals(100, viewModel.state.value.chattiness)

        viewModel.updateField("chattiness", "-10")
        assertEquals(0, viewModel.state.value.chattiness)
    }

    @Test
    fun `updateField chattiness with non-numeric defaults to 50`() {
        viewModel.updateField("chattiness", "abc")
        assertEquals(50, viewModel.state.value.chattiness)
    }

    @Test
    fun `updateField authorNoteDepth with non-numeric defaults to 4`() {
        viewModel.updateField("authorNoteDepth", "xyz")
        assertEquals(4, viewModel.state.value.authorNoteDepth)
    }

    @Test
    fun `updateField unknown field does not change state`() {
        val before = viewModel.state.value
        viewModel.updateField("unknownField", "value")
        assertEquals(before, viewModel.state.value)
    }

    // ==================== loadCharacter ====================

    @Test
    fun `loadCharacter populates state from entity`() = runTest {
        coEvery { characterRepository.getCharacterById(42) } returns testCharacter
        coEvery { authorNoteDao.getAuthorNoteSync(42) } returns null

        viewModel.loadCharacter(42)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals("Alice", s.name)
        assertEquals("A character", s.description)
        assertEquals("Friendly", s.personality)
        assertEquals("Hello!", s.firstMes)
        assertEquals(true, s.isEditing)
        assertEquals(42L, s.characterId)
        assertEquals(60, s.chattiness)
    }

    @Test
    fun `loadCharacter with author note populates note fields`() = runTest {
        coEvery { characterRepository.getCharacterById(42) } returns testCharacter
        coEvery { authorNoteDao.getAuthorNoteSync(42) } returns AuthorNoteEntity(
            characterId = 42, content = "Note text", position = "before_an", depth = 2
        )

        viewModel.loadCharacter(42)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals("Note text", s.authorNoteContent)
        assertEquals("before_an", s.authorNotePosition)
        assertEquals(2, s.authorNoteDepth)
    }

    @Test
    fun `loadCharacter with worldBookId loads world book name`() = runTest {
        val charWithWB = testCharacter.copy(worldBookId = 10)
        coEvery { characterRepository.getCharacterById(42) } returns charWithWB
        coEvery { authorNoteDao.getAuthorNoteSync(42) } returns null
        coEvery { worldBookRepository.getWorldBookById(10) } returns WorldBookEntity(id = 10, name = "Lore")

        viewModel.loadCharacter(42)
        advanceUntilIdle()

        assertEquals(10L, viewModel.state.value.worldBookId)
        assertEquals("Lore", viewModel.state.value.worldBookName)
    }

    @Test
    fun `loadCharacter with null character does nothing`() = runTest {
        coEvery { characterRepository.getCharacterById(999) } returns null

        viewModel.loadCharacter(999)
        advanceUntilIdle()

        // State should remain default
        assertEquals("", viewModel.state.value.name)
    }

    // ==================== clearBackground / clearWorldBook / clearError ====================

    @Test
    fun `clearBackground sets backgroundPath to null`() {
        viewModel.updateField("backgroundPath", "/some/path")
        viewModel.clearBackground()
        assertNull(viewModel.state.value.backgroundPath)
    }

    @Test
    fun `setWorldBook updates worldBookId and worldBookName`() {
        viewModel.setWorldBook(5L, "My Book")
        assertEquals(5L, viewModel.state.value.worldBookId)
        assertEquals("My Book", viewModel.state.value.worldBookName)
    }

    @Test
    fun `clearWorldBook sets both to null`() {
        viewModel.setWorldBook(5L, "My Book")
        viewModel.clearWorldBook()
        assertNull(viewModel.state.value.worldBookId)
        assertNull(viewModel.state.value.worldBookName)
    }

    @Test
    fun `setPresetBackground sets preset path`() {
        viewModel.setPresetBackground("forest")
        assertEquals("preset:forest", viewModel.state.value.backgroundPath)
    }

    @Test
    fun `clearError sets error to null`() {
        viewModel.clearError()
        assertNull(viewModel.error.value)
    }

    // ==================== save ====================

    @Test
    fun `save with blank name does nothing`() = runTest {
        viewModel.save {}
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { characterRepository.createCharacter(any()) }
    }

    @Test
    fun `save new character calls createCharacter`() = runTest {
        coEvery { characterRepository.createCharacter(any()) } returns 100L
        coEvery { authorNoteDao.insertOrUpdate(any()) } returns Unit
        coEvery { authorNoteDao.delete(any()) } returns Unit

        viewModel.updateField("name", "New Char")
        viewModel.updateField("description", "Desc")
        viewModel.save {}
        advanceUntilIdle()

        coVerify { characterRepository.createCharacter(match { it.name == "New Char" }) }
    }

    @Test
    fun `save editing character calls updateCharacter`() = runTest {
        coEvery { characterRepository.getCharacterById(42) } returns testCharacter
        coEvery { characterRepository.updateCharacter(any()) } returns Unit
        coEvery { authorNoteDao.getAuthorNoteSync(42) } returns null
        coEvery { authorNoteDao.delete(any()) } returns Unit

        viewModel.loadCharacter(42)
        advanceUntilIdle()

        viewModel.updateField("name", "Updated Alice")
        viewModel.save {}
        advanceUntilIdle()

        coVerify { characterRepository.updateCharacter(match { it.name == "Updated Alice" }) }
    }

    @Test
    fun `save with author note content inserts note`() = runTest {
        coEvery { characterRepository.createCharacter(any()) } returns 100L
        coEvery { authorNoteDao.insertOrUpdate(any()) } returns Unit

        viewModel.updateField("name", "Char")
        viewModel.updateField("authorNoteContent", "Remember this")
        viewModel.save {}
        advanceUntilIdle()

        coVerify { authorNoteDao.insertOrUpdate(match { it.content == "Remember this" && it.characterId == 100L }) }
    }

    @Test
    fun `save with blank author note deletes existing note`() = runTest {
        coEvery { characterRepository.createCharacter(any()) } returns 100L
        coEvery { authorNoteDao.delete(any()) } returns Unit

        viewModel.updateField("name", "Char")
        // authorNoteContent is blank by default
        viewModel.save {}
        advanceUntilIdle()

        coVerify { authorNoteDao.delete(100L) }
    }

    // ==================== updateAuthorNotePosition ====================

    @Test
    fun `updateAuthorNotePosition updates state`() {
        viewModel.updateAuthorNotePosition("before_an")
        assertEquals("before_an", viewModel.state.value.authorNotePosition)
    }
}
