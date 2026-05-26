package com.tavern.lite.ui.screens.script

import androidx.lifecycle.SavedStateHandle
import com.tavern.lite.data.db.entity.ScriptEntity
import com.tavern.lite.data.repository.ScriptRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptViewModelTest {

    @MockK private lateinit var scriptRepository: ScriptRepository

    private lateinit var viewModel: ScriptViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val characterId = 42L

    private val testScript = ScriptEntity(
        id = 1, characterId = characterId, name = "Test Script",
        findPattern = "hello", replacePattern = "world", scriptType = 0
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { scriptRepository.getScriptsForCharacter(characterId) } returns flowOf(emptyList())
        val savedStateHandle = SavedStateHandle(mapOf("characterId" to characterId))
        viewModel = ScriptViewModel(savedStateHandle, scriptRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `addScript delegates to repository`() = runTest {
        coEvery { scriptRepository.insertScript(any()) } returns 10L
        viewModel.addScript("New", "comment", 0, "find", "replace", true, false)
        advanceUntilIdle()
        coVerify { scriptRepository.insertScript(match {
            it.characterId == characterId &&
            it.name == "New" &&
            it.findPattern == "find" &&
            it.replacePattern == "replace"
        }) }
    }

    @Test
    fun `updateScript delegates to repository`() = runTest {
        coEvery { scriptRepository.updateScript(any()) } returns Unit
        viewModel.updateScript(testScript)
        advanceUntilIdle()
        coVerify { scriptRepository.updateScript(testScript) }
    }

    @Test
    fun `deleteScript delegates to repository`() = runTest {
        coEvery { scriptRepository.deleteScript(any()) } returns Unit
        viewModel.deleteScript(testScript)
        advanceUntilIdle()
        coVerify { scriptRepository.deleteScript(testScript) }
    }

    @Test
    fun `toggleEnabled flips enabled flag`() = runTest {
        coEvery { scriptRepository.updateScript(any()) } returns Unit
        viewModel.toggleEnabled(testScript)
        advanceUntilIdle()
        coVerify { scriptRepository.updateScript(match { !it.enabled }) }
    }

    @Test
    fun `toggleEnabled on disabled script enables it`() = runTest {
        coEvery { scriptRepository.updateScript(any()) } returns Unit
        val disabled = testScript.copy(enabled = false)
        viewModel.toggleEnabled(disabled)
        advanceUntilIdle()
        coVerify { scriptRepository.updateScript(match { it.enabled }) }
    }
}
