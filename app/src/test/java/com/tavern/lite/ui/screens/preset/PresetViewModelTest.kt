package com.tavern.lite.ui.screens.preset

import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.domain.port.TemplateRendererPort
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
class PresetViewModelTest {

    @MockK private lateinit var presetRepository: PresetRepository
    @MockK private lateinit var templateRenderer: TemplateRendererPort

    private lateinit var viewModel: PresetViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPreset = PresetEntity(id = 1, name = "Default Preset")

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { presetRepository.getAllPresets() } returns flowOf(emptyList())
        every { templateRenderer.render(any(), any()) } answers { firstArg() }
        viewModel = PresetViewModel(presetRepository, templateRenderer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `insertPreset delegates to repository`() = runTest {
        coEvery { presetRepository.insertPreset(any()) } returns 1L
        viewModel.insertPreset(testPreset)
        advanceUntilIdle()
        coVerify { presetRepository.insertPreset(testPreset) }
    }

    @Test
    fun `updatePreset delegates to repository with updated timestamp`() = runTest {
        coEvery { presetRepository.updatePreset(any()) } returns Unit
        viewModel.updatePreset(testPreset)
        advanceUntilIdle()
        coVerify { presetRepository.updatePreset(match { it.id == testPreset.id && it.updatedAt >= testPreset.updatedAt }) }
    }

    @Test
    fun `deletePreset delegates to repository`() = runTest {
        coEvery { presetRepository.deletePreset(any()) } returns Unit
        viewModel.deletePreset(testPreset)
        advanceUntilIdle()
        coVerify { presetRepository.deletePreset(testPreset) }
    }

    @Test
    fun `setDefaultPreset delegates to repository`() = runTest {
        coEvery { presetRepository.setDefaultPreset(any()) } returns Unit
        viewModel.setDefaultPreset(1L)
        advanceUntilIdle()
        coVerify { presetRepository.setDefaultPreset(1L) }
    }
}
