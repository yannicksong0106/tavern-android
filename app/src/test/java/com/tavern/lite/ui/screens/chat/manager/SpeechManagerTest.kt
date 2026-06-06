package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.util.SttHelper
import com.tavern.lite.util.TtsHelper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechManagerTest {

    @MockK private lateinit var ttsHelper: TtsHelper
    @MockK private lateinit var sttHelper: SttHelper

    private val isSpeakingFlow = MutableStateFlow(false)
    private val speakingMessageIdFlow = MutableStateFlow<Long?>(null)
    private val isListeningFlow = MutableStateFlow(false)
    private val partialTextFlow = MutableStateFlow("")

    private val dispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope
    private lateinit var manager: SpeechManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { ttsHelper.isSpeaking } returns isSpeakingFlow
        every { ttsHelper.speakingMessageId } returns speakingMessageIdFlow
        every { sttHelper.isListening } returns isListeningFlow
        every { sttHelper.partialText } returns partialTextFlow
        every { ttsHelper.speak(any(), any()) } just runs
        every { ttsHelper.stop() } just runs
        every { sttHelper.startListening(any(), any()) } just runs
        every { sttHelper.stopListening() } just runs
        every { sttHelper.shutdown() } just runs

        scope = CoroutineScope(SupervisorJob() + dispatcher)
        manager = SpeechManager(ttsHelper, sttHelper, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `initial speech states mirror helpers`() = runTest(dispatcher) {
        val job = launch { manager.isSpeaking.collect {} }
        advanceUntilIdle()

        assertFalse(manager.isSpeaking.value)
        assertNull(manager.speakingMessageId.value)
        assertFalse(manager.isListening.value)
        assertEquals("", manager.sttPartialText.value)
        job.cancel()
    }

    @Test
    fun `state flows update from helper flows`() = runTest(dispatcher) {
        val jobs = listOf(
            launch { manager.isSpeaking.collect {} },
            launch { manager.speakingMessageId.collect {} },
            launch { manager.isListening.collect {} },
            launch { manager.sttPartialText.collect {} }
        )
        advanceUntilIdle()

        isSpeakingFlow.value = true
        speakingMessageIdFlow.value = 42L
        isListeningFlow.value = true
        partialTextFlow.value = "hello"
        advanceUntilIdle()

        assertTrue(manager.isSpeaking.value)
        assertEquals(42L, manager.speakingMessageId.value)
        assertTrue(manager.isListening.value)
        assertEquals("hello", manager.sttPartialText.value)
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `speakMessage delegates content and id to tts helper`() {
        val message = MessageEntity(id = 7L, chatId = 1L, role = "assistant", content = "Hello")

        manager.speakMessage(message)

        verify { ttsHelper.speak("Hello", 7L) }
    }

    @Test
    fun `stopSpeaking delegates to tts helper`() {
        manager.stopSpeaking()

        verify { ttsHelper.stop() }
    }

    @Test
    fun `startVoiceInput delegates to stt helper`() {
        val callback: (String) -> Unit = {}

        manager.startVoiceInput(callback)

        verify { sttHelper.startListening(onResult = callback) }
    }

    @Test
    fun `stopVoiceInput delegates to stt helper`() {
        manager.stopVoiceInput()

        verify { sttHelper.stopListening() }
    }

    @Test
    fun `shutdown stops tts and shuts down stt`() {
        manager.shutdown()

        verify { ttsHelper.stop() }
        verify { sttHelper.shutdown() }
    }
}
