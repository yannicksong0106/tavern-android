package com.tavern.lite.ui.screens.settings

import android.content.Context
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.util.BackupManager
import com.tavern.lite.worker.ProactiveWorkScheduler
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
class SettingsViewModelTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var apiConfigStore: ApiConfigStore
    @MockK private lateinit var chatApiService: ChatApiService
    @MockK private lateinit var settingsStore: SettingsStore
    @MockK private lateinit var proactiveWorkScheduler: ProactiveWorkScheduler
    @MockK private lateinit var backupManager: BackupManager

    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        every { apiConfigStore.configFlow } returns flowOf(ApiConfig())
        every { settingsStore.bubbleStyleFlow } returns flowOf(com.tavern.lite.data.model.BubbleStyleConfig())
        every { settingsStore.languageFlow } returns flowOf("system")
        every { settingsStore.backgroundProactiveFlow } returns flowOf(false)
        every { settingsStore.ttsSettingsFlow } returns flowOf(com.tavern.lite.data.store.TtsSettings())
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "test_settings_${System.nanoTime()}")
        tmpDir.mkdirs()
        every { context.cacheDir } returns tmpDir
        every { context.getDatabasePath(any()) } returns File(tmpDir, "tavern_db")

        viewModel = SettingsViewModel(context, apiConfigStore, chatApiService, settingsStore, proactiveWorkScheduler, backupManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `testConnection rethrows CancellationException`() = runTest {
        every { chatApiService.streamChat(any(), any()) } returns flow { throw CancellationException() }

        viewModel.testConnection()
        advanceUntilIdle()

        // If CE was swallowed by catch block, testState would be Error.
        // If CE is rethrown, the coroutine is cancelled before reaching the catch's error path.
        // The state should be Testing (set before streamChat) or Idle, never Error.
        val state = viewModel.testState.value
        assertEquals("CE should be rethrown, testState should be Testing not Error", ConnectionTestState.Testing, state)
    }
}
