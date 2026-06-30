package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.BgmEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.SpriteEntity
import com.tavern.lite.data.repository.BgmRepository
import com.tavern.lite.data.repository.SpriteRepository
import com.tavern.lite.domain.port.EmotionDetectionPort
import com.tavern.lite.ui.screens.vn.BgmPlayer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class VnModeManagerTest {

    @MockK private lateinit var spriteRepository: SpriteRepository
    @MockK private lateinit var emotionDetector: EmotionDetectionPort
    @MockK private lateinit var bgmRepository: BgmRepository
    @MockK private lateinit var bgmPlayer: BgmPlayer

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var manager: VnModeManager

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)

        manager = VnModeManager(
            spriteRepository = spriteRepository,
            emotionDetector = emotionDetector,
            bgmRepository = bgmRepository,
            bgmPlayer = bgmPlayer,
            scope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initial state ====================

    @Test
    fun `initial emotion is neutral`() {
        assertEquals("neutral", manager.currentEmotion.value)
    }

    @Test
    fun `initial sprite path is null`() {
        assertNull(manager.currentSpritePath.value)
    }

    @Test
    fun `initial available emotions is empty`() {
        assertTrue(manager.availableEmotions.value.isEmpty())
    }

    @Test
    fun `initial bgm playing is false`() {
        assertFalse(manager.isBgmPlaying.value)
    }

    // ==================== loadAvailableEmotions ====================

    @Test
    fun `loadAvailableEmotions fetches from sprite repository`() = runTest {
        manager.characterIdProvider = { 10L }
        val emotions = listOf("happy", "sad", "neutral")
        coEvery { spriteRepository.getAvailableEmotions(10L) } returns emotions

        manager.loadAvailableEmotions()
        advanceUntilIdle()

        assertEquals(emotions, manager.availableEmotions.value)
    }

    @Test
    fun `loadAvailableEmotions fetches for characterId 0`() = runTest {
        manager.characterIdProvider = { 0L }
        coEvery { spriteRepository.getAvailableEmotions(0L) } returns emptyList()

        manager.loadAvailableEmotions()
        advanceUntilIdle()

        assertTrue(manager.availableEmotions.value.isEmpty())
        coVerify { spriteRepository.getAvailableEmotions(0L) }
    }

    // ==================== updateEmotionFromResponse ====================

    @Test
    fun `updateEmotionFromResponse detects emotion and loads sprite`() = runTest {
        manager.characterIdProvider = { 10L }
        every { emotionDetector.detectEmotion("我很开心") } returns "happy"
        val sprite = SpriteEntity(id = 1, characterId = 10, emotion = "happy", imagePath = "/sprites/happy.png")
        coEvery { spriteRepository.getSpriteByEmotion(10L, "happy") } returns sprite
        coEvery { bgmRepository.getBgmForEmotion(10L, "happy") } returns null

        manager.updateEmotionFromResponse("我很开心")
        advanceUntilIdle()

        assertEquals("happy", manager.currentEmotion.value)
        assertEquals("/sprites/happy.png", manager.currentSpritePath.value)
    }

    @Test
    fun `updateEmotionFromResponse sets null sprite path when no sprite found`() = runTest {
        manager.characterIdProvider = { 10L }
        every { emotionDetector.detectEmotion("hello") } returns "neutral"
        coEvery { spriteRepository.getSpriteByEmotion(10L, "neutral") } returns null

        manager.updateEmotionFromResponse("hello")
        advanceUntilIdle()

        assertEquals("neutral", manager.currentEmotion.value)
        assertNull(manager.currentSpritePath.value)
    }

    @Test
    fun `updateEmotionFromResponse plays emotion BGM when available`() = runTest {
        manager.characterIdProvider = { 10L }
        every { emotionDetector.detectEmotion("我很开心") } returns "happy"
        coEvery { spriteRepository.getSpriteByEmotion(10L, "happy") } returns null
        val bgm = BgmEntity(id = 1, characterId = 10, name = "Happy", audioPath = "/audio/happy.mp3", volume = 0.7f, loop = true)
        coEvery { bgmRepository.getBgmForEmotion(10L, "happy") } returns bgm

        manager.updateEmotionFromResponse("我很开心")
        advanceUntilIdle()

        verify { bgmPlayer.play("/audio/happy.mp3", 0.7f, true) }
        assertTrue(manager.isBgmPlaying.value)
    }

    // ==================== setEmotion ====================

    @Test
    fun `setEmotion updates emotion and loads sprite`() = runTest {
        manager.characterIdProvider = { 10L }
        val sprite = SpriteEntity(id = 2, characterId = 10, emotion = "sad", imagePath = "/sprites/sad.png")
        coEvery { spriteRepository.getSpriteByEmotion(10L, "sad") } returns sprite
        coEvery { bgmRepository.getBgmForEmotion(10L, "sad") } returns null

        manager.setEmotion("sad")
        advanceUntilIdle()

        assertEquals("sad", manager.currentEmotion.value)
        assertEquals("/sprites/sad.png", manager.currentSpritePath.value)
    }

    @Test
    fun `setEmotion plays emotion BGM`() = runTest {
        manager.characterIdProvider = { 10L }
        coEvery { spriteRepository.getSpriteByEmotion(10L, "angry") } returns null
        val bgm = BgmEntity(id = 3, characterId = 10, name = "Battle", audioPath = "/audio/battle.mp3", volume = 0.8f, loop = true)
        coEvery { bgmRepository.getBgmForEmotion(10L, "angry") } returns bgm

        manager.setEmotion("angry")
        advanceUntilIdle()

        verify { bgmPlayer.play("/audio/battle.mp3", 0.8f, true) }
    }

    // ==================== loadDefaultBgm ====================

    @Test
    fun `loadDefaultBgm plays default bgm for character`() = runTest {
        manager.characterIdProvider = { 10L }
        val bgm = BgmEntity(id = 1, characterId = 10, name = "Default", audioPath = "/audio/default.mp3", volume = 0.5f, loop = true)
        coEvery { bgmRepository.getDefaultBgm(10L) } returns bgm

        manager.loadDefaultBgm()
        advanceUntilIdle()

        verify { bgmPlayer.play("/audio/default.mp3", 0.5f, true) }
        assertTrue(manager.isBgmPlaying.value)
    }

    @Test
    fun `loadDefaultBgm does nothing when no default bgm`() = runTest {
        manager.characterIdProvider = { 10L }
        coEvery { bgmRepository.getDefaultBgm(10L) } returns null

        manager.loadDefaultBgm()
        advanceUntilIdle()

        verify(exactly = 0) { bgmPlayer.play(any(), any(), any()) }
        assertFalse(manager.isBgmPlaying.value)
    }

    @Test
    fun `loadDefaultBgm fetches for characterId 0`() = runTest {
        manager.characterIdProvider = { 0L }
        coEvery { bgmRepository.getDefaultBgm(0L) } returns null

        manager.loadDefaultBgm()
        advanceUntilIdle()

        coVerify { bgmRepository.getDefaultBgm(0L) }
        assertFalse(manager.isBgmPlaying.value)
    }

    // ==================== toggleBgmPause ====================

    @Test
    fun `toggleBgmPause pauses when playing`() {
        every { bgmPlayer.isPlaying() } returns true

        manager.toggleBgmPause()

        verify { bgmPlayer.pause() }
        assertFalse(manager.isBgmPlaying.value)
    }

    @Test
    fun `toggleBgmPause resumes when paused`() {
        every { bgmPlayer.isPlaying() } returns false

        manager.toggleBgmPause()

        verify { bgmPlayer.resume() }
        assertTrue(manager.isBgmPlaying.value)
    }

    // ==================== stopBgm ====================

    @Test
    fun `stopBgm stops player and updates state`() {
        manager.stopBgm()

        verify { bgmPlayer.stop() }
        assertFalse(manager.isBgmPlaying.value)
    }

    // ==================== Group chat character resolution ====================

    @Test
    fun `resolveCharId uses responding character in group chat`() = runTest {
        manager.isGroupChatProvider = { true }
        val respondingChar = CharacterEntity(id = 42, name = "Alice")
        manager.respondingCharacterProvider = { respondingChar }
        manager.groupCharactersProvider = { emptyList() }

        val sprite = SpriteEntity(id = 1, characterId = 42, emotion = "happy", imagePath = "/sprites/happy.png")
        coEvery { emotionDetector.detectEmotion("开心") } returns "happy"
        coEvery { spriteRepository.getSpriteByEmotion(42L, "happy") } returns sprite
        coEvery { bgmRepository.getBgmForEmotion(42L, "happy") } returns null

        manager.updateEmotionFromResponse("开心")
        advanceUntilIdle()

        assertEquals("happy", manager.currentEmotion.value)
        assertEquals("/sprites/happy.png", manager.currentSpritePath.value)
    }

    @Test
    fun `resolveCharId falls back to first group character when no responding character`() = runTest {
        manager.isGroupChatProvider = { true }
        manager.respondingCharacterProvider = { null }
        val chars = listOf(CharacterEntity(id = 5, name = "Bob"), CharacterEntity(id = 6, name = "Carol"))
        manager.groupCharactersProvider = { chars }

        coEvery { emotionDetector.detectEmotion("neutral text") } returns "neutral"
        coEvery { spriteRepository.getSpriteByEmotion(5L, "neutral") } returns null

        manager.updateEmotionFromResponse("neutral text")
        advanceUntilIdle()

        assertEquals("neutral", manager.currentEmotion.value)
    }

    @Test
    fun `resolveCharId returns null when group chat has no characters`() = runTest {
        manager.isGroupChatProvider = { true }
        manager.respondingCharacterProvider = { null }
        manager.groupCharactersProvider = { emptyList() }

        coEvery { emotionDetector.detectEmotion("hello") } returns "neutral"

        manager.updateEmotionFromResponse("hello")
        advanceUntilIdle()

        // Should not crash, sprite stays null
        assertNull(manager.currentSpritePath.value)
    }

    @Test
    fun `resolveCharId uses characterIdProvider in single chat`() = runTest {
        manager.characterIdProvider = { 10L }
        manager.isGroupChatProvider = { false }

        coEvery { emotionDetector.detectEmotion("test") } returns "neutral"
        coEvery { spriteRepository.getSpriteByEmotion(10L, "neutral") } returns null

        manager.updateEmotionFromResponse("test")
        advanceUntilIdle()

        assertEquals("neutral", manager.currentEmotion.value)
    }
}
