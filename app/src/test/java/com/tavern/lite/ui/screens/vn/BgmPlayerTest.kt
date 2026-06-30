package com.tavern.lite.ui.screens.vn

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.time.Duration
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BgmPlayerTest {

    private lateinit var audioManager: AudioManager
    private lateinit var tempAudio: File
    private lateinit var player: MediaPlayer
    private lateinit var bgmPlayer: BgmPlayer

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tempAudio = File.createTempFile("bgm-player", ".mp3")
        player = mockMediaPlayer(isPlaying = true)
        bgmPlayer = BgmPlayer(
            mediaPlayerFactory = { player },
            audioManager = audioManager
        )
    }

    @After
    fun tearDown() {
        bgmPlayer.release()
        tempAudio.delete()
        clearAllMocks()
    }

    @Test
    fun `play ignores missing audio file`() {
        val missingPath = File(tempAudio.parentFile, "missing.mp3").absolutePath

        bgmPlayer.play(missingPath)

        verify(exactly = 0) { player.start() }
        assertFalse(bgmPlayer.isPlaying())
        assertNull(bgmPlayer.currentPath())
    }

    @Test
    fun `play prepares starts and requests audio focus`() {
        bgmPlayer.play(tempAudio.absolutePath, volume = 0.7f, loop = false)

        verify { player.setDataSource(tempAudio.absolutePath) }
        verify { player.isLooping = false }
        verify { player.setVolume(0f, 0f) }
        verify { player.prepare() }
        verify { player.start() }

        assertEquals(tempAudio.absolutePath, bgmPlayer.currentPath())
        assertTrue(bgmPlayer.isPlaying())
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN,
            shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest.focusGain
        )
    }

    @Test
    fun `play same currently playing file only updates volume`() {
        bgmPlayer.play(tempAudio.absolutePath, volume = 0.4f)

        bgmPlayer.play(tempAudio.absolutePath, volume = 0.8f)

        verify(exactly = 1) { player.setDataSource(tempAudio.absolutePath) }
        verify { player.setVolume(0.8f, 0.8f) }
        assertEquals(tempAudio.absolutePath, bgmPlayer.currentPath())
    }

    @Test
    fun `setVolume clamps value and applies it to current player`() {
        bgmPlayer.play(tempAudio.absolutePath)

        bgmPlayer.setVolume(2f)
        bgmPlayer.setVolume(-1f)

        verify { player.setVolume(1f, 1f) }
        verify { player.setVolume(0f, 0f) }
    }

    @Test
    fun `pause and resume forward to media player`() {
        bgmPlayer.play(tempAudio.absolutePath)

        bgmPlayer.pause()
        bgmPlayer.resume()

        verify { player.pause() }
        verify(atLeast = 2) { player.start() }
    }

    @Test
    fun `stop fades out then releases player and audio focus`() {
        bgmPlayer.play(tempAudio.absolutePath)

        bgmPlayer.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))

        verify { player.stop() }
        verify { player.release() }
        assertNull(bgmPlayer.currentPath())
        assertFalse(bgmPlayer.isPlaying())
        assertEquals(
            shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest,
            shadowOf(audioManager).lastAbandonedAudioFocusRequest
        )
    }

    @Test
    fun `audio focus duck lowers volume and gain restores it`() {
        bgmPlayer.play(tempAudio.absolutePath, volume = 0.6f)
        val listener = shadowOf(audioManager).lastAudioFocusRequest.listener

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        verify {
            player.setVolume(
                match { abs(it - 0.12f) < 0.001f },
                match { abs(it - 0.12f) < 0.001f }
            )
        }
        verify { player.setVolume(0.6f, 0.6f) }
    }

    @Test
    fun `audio focus loss pauses playback`() {
        bgmPlayer.play(tempAudio.absolutePath)
        val listener = shadowOf(audioManager).lastAudioFocusRequest.listener

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        verify { player.pause() }
    }

    private fun mockMediaPlayer(isPlaying: Boolean): MediaPlayer =
        mockk(relaxed = true) {
            every { setAudioAttributes(any()) } just Runs
            every { setDataSource(any<String>()) } just Runs
            every { setVolume(any(), any()) } just Runs
            every { prepare() } just Runs
            every { start() } just Runs
            every { pause() } just Runs
            every { stop() } just Runs
            every { release() } just Runs
            every { setOnCompletionListener(any()) } just Runs
            every { this@mockk.isLooping = any() } just Runs
            every { this@mockk.isPlaying } returns isPlaying
        }
}
