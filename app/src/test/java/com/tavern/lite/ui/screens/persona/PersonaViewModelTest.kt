package com.tavern.lite.ui.screens.persona

import com.tavern.lite.data.repository.PersonaRepository
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
class PersonaViewModelTest {

    @MockK private lateinit var personaRepository: PersonaRepository

    private lateinit var viewModel: PersonaViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { personaRepository.getAllPersonas() } returns flowOf(emptyList())
        viewModel = PersonaViewModel(personaRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `createPersona delegates to repository`() = runTest {
        coEvery { personaRepository.createPersona(any(), any(), any()) } returns 1L
        viewModel.createPersona("Test", "Bio", "/path")
        advanceUntilIdle()
        coVerify { personaRepository.createPersona("Test", "Bio", "/path") }
    }

    @Test
    fun `updatePersona delegates to repository`() = runTest {
        coEvery { personaRepository.updatePersona(any(), any(), any(), any()) } returns Unit
        viewModel.updatePersona(1L, "New Name", "New Bio", null)
        advanceUntilIdle()
        coVerify { personaRepository.updatePersona(1L, "New Name", "New Bio", null) }
    }

    @Test
    fun `deletePersona delegates to repository`() = runTest {
        coEvery { personaRepository.deletePersona(any()) } returns Unit
        viewModel.deletePersona(1L)
        advanceUntilIdle()
        coVerify { personaRepository.deletePersona(1L) }
    }

    @Test
    fun `setDefault delegates to repository`() = runTest {
        coEvery { personaRepository.setDefault(any()) } returns Unit
        viewModel.setDefault(2L)
        advanceUntilIdle()
        coVerify { personaRepository.setDefault(2L) }
    }
}
