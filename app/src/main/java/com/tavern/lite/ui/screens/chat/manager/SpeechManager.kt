package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.util.TtsHelper
import com.tavern.lite.util.SttHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SpeechManager(
    private val ttsHelper: TtsHelper,
    private val sttHelper: SttHelper,
    private val scope: CoroutineScope
) {
    val isSpeaking: StateFlow<Boolean> = ttsHelper.isSpeaking
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    val speakingMessageId: StateFlow<Long?> = ttsHelper.speakingMessageId
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    val isListening: StateFlow<Boolean> = sttHelper.isListening
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    val sttPartialText: StateFlow<String> = sttHelper.partialText
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    fun speakMessage(message: MessageEntity) {
        ttsHelper.speak(message.content, message.id)
    }

    fun stopSpeaking() {
        ttsHelper.stop()
    }

    fun startVoiceInput(onResult: (String) -> Unit) {
        sttHelper.startListening(onResult = onResult)
    }

    fun stopVoiceInput() {
        sttHelper.stopListening()
    }

    fun shutdown() {
        ttsHelper.stop()
        sttHelper.shutdown()
    }
}
