package com.tavern.lite.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.tavern.lite.data.store.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        _speakingMessageId.value = null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        _speakingMessageId.value = null
                    }
                })
            }
        }
    }

    fun speak(text: String, messageId: Long) {
        if (!isInitialized || tts == null) return
        if (text.isBlank()) return

        stop()

        scope.launch {
            val settings = settingsStore.ttsSettingsFlow.first()
            val speed = settings.speed.coerceIn(0.5f, 2.0f)
            val pitch = settings.pitch.coerceIn(0.5f, 2.0f)

            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)

            // Auto-detect language based on content
            val locale = detectLocale(text)
            tts?.language = locale

            _speakingMessageId.value = messageId
            _isSpeaking.value = true

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "msg_$messageId")
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _speakingMessageId.value = null
    }

    private fun detectLocale(text: String): Locale {
        val hasChinese = text.any { it.code in 0x4E00..0x9FFF }
        val hasJapanese = text.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }
        val hasKorean = text.any { it.code in 0xAC00..0xD7AF }

        return when {
            hasJapanese -> Locale.JAPANESE
            hasKorean -> Locale.KOREAN
            hasChinese -> Locale.CHINESE
            else -> Locale.ENGLISH
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
