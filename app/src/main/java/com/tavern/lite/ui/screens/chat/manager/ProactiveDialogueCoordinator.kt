package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.domain.usecase.ProactiveDialogueUseCase
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.network.ApiConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProactiveDialogueCoordinator(
    private val chatId: Long,
    private val apiConfigStore: ApiConfigStore,
    private val proactiveMessageUseCase: ProactiveMessageUseCase,
    private val proactiveDialogueUseCase: ProactiveDialogueUseCase,
    private val scope: CoroutineScope,
    private val streamingMutex: Mutex,
) {
    var characterProvider: () -> CharacterEntity? = { null }
    var groupCharactersProvider: () -> List<CharacterEntity> = { emptyList() }
    var isGroupChatProvider: () -> Boolean = { false }
    var isGeneratingProvider: () -> Boolean = { false }
    var onGeneratingChanged: (Boolean) -> Unit = {}
    var onRespondingCharacterChanged: (CharacterEntity?) -> Unit = {}
    var onToast: suspend (Exception) -> Unit = {}
    var onAssistantReplyCommit: suspend (
        assistantMsgId: Long?,
        updateEmotion: Boolean,
        respectCancellation: Boolean,
    ) -> Boolean = { _, _, _ -> false }
    var onStreamingJobChanged: (Job?) -> Unit = {}

    private var isProactiveMessage = false

    fun triggerIfNeeded(
        currentMessages: List<MessageEntity>,
        sendSingleChatMessage: (String) -> Unit,
        sendGroupChatMessage: (String) -> Unit,
        sendDirectMessage: (String, CharacterEntity) -> Unit,
    ) {
        if (isGeneratingProvider() || isProactiveMessage) return

        val lastMsg = currentMessages.lastOrNull() ?: return
        if (lastMsg.role == "user") {
            if (isGroupChatProvider()) {
                sendGroupChatMessage("")
            } else {
                sendSingleChatMessage("")
            }
        } else if (isGroupChatProvider() && lastMsg.role == "assistant") {
            val characters = groupCharactersProvider()
            val lastCharIndex = characters.indexOfFirst { it.id == lastMsg.characterId }
            if (lastCharIndex >= 0 && lastCharIndex < characters.size - 1) {
                sendDirectMessage("", characters[lastCharIndex + 1])
            }
        }
    }

    fun scheduleSingle() {
        if (isGroupChatProvider() || isProactiveMessage) return
        val character = characterProvider() ?: return
        val delayMs = proactiveDialogueUseCase.shouldScheduleProactive(character.chattiness) ?: return

        scope.launch {
            delay(delayMs)
            if (!isGeneratingProvider()) {
                sendProactiveSingleMessage()
            }
        }
    }

    fun scheduleGroup() {
        if (!isGroupChatProvider() || isProactiveMessage) return
        val characters = groupCharactersProvider()
        if (characters.isEmpty()) return

        val delayMs = proactiveDialogueUseCase.shouldScheduleGroupProactive(characters) ?: return

        scope.launch {
            delay(delayMs)
            if (!isGeneratingProvider()) {
                val nextChar = proactiveDialogueUseCase.selectNextProactiveCharacter(characters)
                if (nextChar != null) {
                    sendProactiveGroupMessage(nextChar)
                }
            }
        }
    }

    private fun sendProactiveSingleMessage() {
        val job = scope.launch {
            streamingMutex.withLock {
                onGeneratingChanged(true)
                isProactiveMessage = true
                try {
                    val character = characterProvider() ?: return@withLock
                    val config = apiConfigStore.configFlow.first()
                    val result = proactiveMessageUseCase.sendProactiveMessage(chatId, character, config)
                    onAssistantReplyCommit(
                        result?.assistantMsgId,
                        UPDATE_EMOTION_DISABLED,
                        RESPECT_CANCELLATION_DISABLED
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    onToast(e)
                } finally {
                    onGeneratingChanged(false)
                    isProactiveMessage = false
                    onStreamingJobChanged(null)
                }
            }
        }
        onStreamingJobChanged(job)
    }

    private fun sendProactiveGroupMessage(character: CharacterEntity) {
        val job = scope.launch {
            streamingMutex.withLock {
                onGeneratingChanged(true)
                onRespondingCharacterChanged(character)
                isProactiveMessage = true
                try {
                    val characters = groupCharactersProvider()
                    val config = apiConfigStore.configFlow.first()
                    val result = proactiveMessageUseCase.sendProactiveGroupMessage(chatId, characters, character, config)
                    onAssistantReplyCommit(
                        result?.assistantMsgId,
                        UPDATE_EMOTION_DISABLED,
                        RESPECT_CANCELLATION_DISABLED
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    onToast(e)
                } finally {
                    onGeneratingChanged(false)
                    onRespondingCharacterChanged(null)
                    isProactiveMessage = false
                    onStreamingJobChanged(null)
                }
            }
        }
        onStreamingJobChanged(job)
    }

    private companion object {
        const val UPDATE_EMOTION_DISABLED = false
        const val RESPECT_CANCELLATION_DISABLED = false
    }
}
