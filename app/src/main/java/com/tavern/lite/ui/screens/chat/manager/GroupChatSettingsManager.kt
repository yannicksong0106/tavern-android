package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.GroupSchedulingStrategy
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.GroupChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupChatSettingsManager(
    private val chatId: Long,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val groupChatRepository: GroupChatRepository,
    private val scope: CoroutineScope
) {
    private val _characterChattiness = MutableStateFlow(50)
    val characterChattiness: StateFlow<Int> = _characterChattiness.asStateFlow()

    private val _groupChattiness = MutableStateFlow(50)
    val groupChattiness: StateFlow<Int> = _groupChattiness.asStateFlow()

    private val _groupCharacterChattiness = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val groupCharacterChattiness: StateFlow<Map<Long, Int>> = _groupCharacterChattiness.asStateFlow()

    private val _schedulingStrategy = MutableStateFlow(GroupSchedulingStrategy.NATURAL)
    val schedulingStrategy: StateFlow<GroupSchedulingStrategy> = _schedulingStrategy.asStateFlow()

    private val _messageIntervalMs = MutableStateFlow(1500L)
    val messageIntervalMs: StateFlow<Long> = _messageIntervalMs.asStateFlow()

    lateinit var characterProvider: () -> CharacterEntity?

    fun loadCharacterChattiness(chattiness: Int) {
        _characterChattiness.value = chattiness
    }

    fun loadGroupSettings(
        groupChattiness: Int,
        schedulingStrategy: GroupSchedulingStrategy,
        messageIntervalMs: Long,
        chatCharacters: List<Pair<Long, Int>>
    ) {
        _groupChattiness.value = groupChattiness
        _schedulingStrategy.value = schedulingStrategy
        _messageIntervalMs.value = messageIntervalMs
        _groupCharacterChattiness.value = chatCharacters.associate { it.first to it.second }
    }

    fun updateCharacterChattiness(value: Int) {
        _characterChattiness.value = value
        scope.launch {
            val char = characterProvider() ?: return@launch
            characterRepository.updateCharacter(char.copy(chattiness = value))
        }
    }

    fun updateGroupChattiness(value: Int) {
        _groupChattiness.value = value
        scope.launch {
            chatRepository.updateGroupChattiness(chatId, value)
        }
    }

    fun updateGroupCharacterChattiness(characterId: Long, value: Int) {
        _groupCharacterChattiness.value = _groupCharacterChattiness.value.toMutableMap().apply {
            put(characterId, value)
        }
        scope.launch {
            groupChatRepository.updateCharacterChattiness(chatId, characterId, value)
        }
    }

    fun updateSchedulingStrategy(strategy: GroupSchedulingStrategy) {
        _schedulingStrategy.value = strategy
        scope.launch {
            groupChatRepository.updateSchedulingStrategy(chatId, strategy.key)
        }
    }

    fun updateMessageInterval(intervalMs: Long) {
        _messageIntervalMs.value = intervalMs
        scope.launch {
            groupChatRepository.updateMessageInterval(chatId, intervalMs)
        }
    }
}
