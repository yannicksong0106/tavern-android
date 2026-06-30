package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.repository.BgmRepository
import com.tavern.lite.data.repository.SpriteRepository
import com.tavern.lite.domain.port.EmotionDetectionPort
import com.tavern.lite.ui.screens.vn.BgmPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 封装 VN 模式状态管理：立绘表情检测、切换、加载 + BGM 播放。
 */
class VnModeManager(
    private val spriteRepository: SpriteRepository,
    private val emotionDetector: EmotionDetectionPort,
    private val bgmRepository: BgmRepository,
    private val bgmPlayer: BgmPlayer,
    private val scope: CoroutineScope
) {
    private val _currentEmotion = MutableStateFlow("neutral")
    val currentEmotion: StateFlow<String> = _currentEmotion.asStateFlow()

    private val _currentSpritePath = MutableStateFlow<String?>(null)
    val currentSpritePath: StateFlow<String?> = _currentSpritePath.asStateFlow()

    private val _availableEmotions = MutableStateFlow<List<String>>(emptyList())
    val availableEmotions: StateFlow<List<String>> = _availableEmotions.asStateFlow()

    private val _isBgmPlaying = MutableStateFlow(false)
    val isBgmPlaying: StateFlow<Boolean> = _isBgmPlaying.asStateFlow()

    /** 获取当前角色 ID（群聊时取回复角色，单聊时取固定角色） */
    var characterIdProvider: () -> Long = { 0 }

    /** 是否群聊 */
    var isGroupChatProvider: () -> Boolean = { false }

    /** 获取当前回复角色 */
    var respondingCharacterProvider: () -> CharacterEntity? = { null }

    /** 获取群聊角色列表 */
    var groupCharactersProvider: () -> List<CharacterEntity> = { emptyList() }

    fun loadAvailableEmotions() {
        scope.launch {
            val charId = resolveCharId() ?: return@launch
            _availableEmotions.value = spriteRepository.getAvailableEmotions(charId)
        }
    }

    fun updateEmotionFromResponse(responseText: String) {
        val emotion = emotionDetector.detectEmotion(responseText)
        _currentEmotion.value = emotion

        scope.launch {
            val charId = resolveCharId() ?: return@launch
            val sprite = spriteRepository.getSpriteByEmotion(charId, emotion)
            _currentSpritePath.value = sprite?.imagePath
            updateBgm(charId, emotion)
        }
    }

    fun setEmotion(emotion: String) {
        _currentEmotion.value = emotion
        scope.launch {
            val charId = resolveCharId() ?: return@launch
            val sprite = spriteRepository.getSpriteByEmotion(charId, emotion)
            _currentSpritePath.value = sprite?.imagePath
            updateBgm(charId, emotion)
        }
    }

    /** 为当前角色加载默认 BGM（进入 VN 模式时调用） */
    fun loadDefaultBgm() {
        scope.launch {
            val charId = resolveCharId() ?: return@launch
            val bgm = bgmRepository.getDefaultBgm(charId) ?: return@launch
            bgmPlayer.play(bgm.audioPath, bgm.volume, bgm.loop)
            _isBgmPlaying.value = true
        }
    }

    /** 暂停/恢复 BGM */
    fun toggleBgmPause() {
        if (bgmPlayer.isPlaying()) {
            bgmPlayer.pause()
            _isBgmPlaying.value = false
        } else {
            bgmPlayer.resume()
            _isBgmPlaying.value = true
        }
    }

    /** 停止 BGM（离开 VN 模式时调用） */
    fun stopBgm() {
        bgmPlayer.stop()
        _isBgmPlaying.value = false
    }

    private suspend fun updateBgm(charId: Long, emotion: String) {
        val bgm = bgmRepository.getBgmForEmotion(charId, emotion) ?: return
        bgmPlayer.play(bgm.audioPath, bgm.volume, bgm.loop)
        _isBgmPlaying.value = true
    }

    private fun resolveCharId(): Long? {
        return if (isGroupChatProvider()) {
            respondingCharacterProvider()?.id ?: groupCharactersProvider().firstOrNull()?.id
        } else {
            characterIdProvider()
        }
    }
}
