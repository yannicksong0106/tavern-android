package com.tavern.lite.ui.screens.vn

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BGM 播放器 — 封装 MediaPlayer，支持播放/暂停/停止/音量/淡入淡出/AudioFocus。
 *
 * 设计为单例，VnScreen 通过 DisposableEffect 管理生命周期：
 * - 进入时 loadBgm，离开时 stop。
 */
@Singleton
class BgmPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BgmPlayer"
        private const val FADE_DURATION_MS = 800L
        private const val FADE_STEPS = 20
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentPath: String? = null
    private var targetVolume: Float = 0.5f
    private var isPaused: Boolean = false
    private var shouldLoop: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null

    // AudioFocus
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hadAudioFocus: Boolean = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "AudioFocus lost, pausing")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "AudioFocus transient loss, pausing")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "AudioFocus duck")
                mediaPlayer?.setVolume(targetVolume * 0.2f, targetVolume * 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AudioFocus gained")
                if (isPaused) resume()
                else mediaPlayer?.setVolume(targetVolume, targetVolume)
            }
        }
    }

    /**
     * 加载并播放指定路径的 BGM。
     * 若已播放相同路径，不重复加载。
     *
     * @param audioPath 音频文件路径
     * @param volume    0.0–1.0
     * @param loop      是否循环
     */
    fun play(audioPath: String, volume: Float = 0.5f, loop: Boolean = true) {
        if (!File(audioPath).exists()) {
            Log.w(TAG, "Audio file not found: $audioPath")
            return
        }

        // 同一首歌已在播放，只更新参数
        if (currentPath == audioPath && mediaPlayer?.isPlaying == true) {
            setVolume(volume)
            return
        }

        stopInternal(release = true)

        targetVolume = volume
        shouldLoop = loop
        currentPath = audioPath

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(audioPath)
                isLooping = loop
                setVolume(0f, 0f) // 淡入从 0 开始
                prepare()
                start()
            }

            requestAudioFocus()
            fadeIn()

            mediaPlayer?.setOnCompletionListener {
                if (!loop) {
                    Log.d(TAG, "Playback completed (non-loop)")
                    abandonAudioFocus()
                }
            }

            Log.d(TAG, "Playing: $audioPath (vol=$volume, loop=$loop)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: $audioPath", e)
            stopInternal(release = true)
        }
    }

    /** 暂停播放 */
    fun pause() {
        cancelFade()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                isPaused = true
                Log.d(TAG, "Paused")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Pause failed", e)
        }
    }

    /** 恢复播放 */
    fun resume() {
        if (!isPaused) return
        try {
            mediaPlayer?.start()
            isPaused = false
            Log.d(TAG, "Resumed")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Resume failed", e)
        }
    }

    /** 停止播放并淡出 */
    fun stop() {
        fadeOut {
            stopInternal(release = true)
            abandonAudioFocus()
        }
    }

    /** 立即停止（无淡出），用于 VnScreen dispose */
    fun release() {
        cancelFade()
        stopInternal(release = true)
        abandonAudioFocus()
    }

    /** 动态调整音量 */
    fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(targetVolume, targetVolume)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "setVolume failed", e)
        }
    }

    /** 当前是否正在播放 */
    fun isPlaying(): Boolean = try {
        mediaPlayer?.isPlaying == true
    } catch (e: IllegalStateException) {
        Log.d(TAG, "isPlaying returned false because player state changed", e)
        false
    }

    /** 当前播放路径 */
    fun currentPath(): String? = currentPath

    // ─── 内部方法 ───

    private fun stopInternal(release: Boolean) {
        cancelFade()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                if (release) release()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "stopInternal failed", e)
        }
        if (release) {
            mediaPlayer = null
            currentPath = null
            isPaused = false
        }
    }

    private fun fadeIn() {
        cancelFade()
        val delayPerStep = FADE_DURATION_MS / FADE_STEPS
        var step = 0

        fadeRunnable = object : Runnable {
            override fun run() {
                step++
                val fraction = step.toFloat() / FADE_STEPS
                val vol = targetVolume * fraction
                try {
                    mediaPlayer?.setVolume(vol, vol)
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "fadeIn setVolume skipped because player state changed", e)
                }

                if (step < FADE_STEPS) {
                    handler.postDelayed(this, delayPerStep)
                }
            }
        }
        handler.postDelayed(fadeRunnable!!, delayPerStep)
    }

    private fun fadeOut(onComplete: () -> Unit) {
        cancelFade()
        val delayPerStep = FADE_DURATION_MS / FADE_STEPS
        val startVolume = targetVolume
        var step = 0

        fadeRunnable = object : Runnable {
            override fun run() {
                step++
                val fraction = 1f - step.toFloat() / FADE_STEPS
                val vol = startVolume * fraction
                try {
                    mediaPlayer?.setVolume(vol, vol)
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "fadeOut setVolume skipped because player state changed", e)
                }

                if (step < FADE_STEPS) {
                    handler.postDelayed(this, delayPerStep)
                } else {
                    onComplete()
                }
            }
        }
        handler.postDelayed(fadeRunnable!!, delayPerStep)
    }

    private fun cancelFade() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus() {
        if (hadAudioFocus) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setAcceptsDelayedFocusGain(true)
            .build()
        audioFocusRequest = request
        val result = audioManager.requestAudioFocus(request)
        hadAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "AudioFocus request: ${if (hadAudioFocus) "granted" else "denied"}")
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
        hadAudioFocus = false
    }
}
