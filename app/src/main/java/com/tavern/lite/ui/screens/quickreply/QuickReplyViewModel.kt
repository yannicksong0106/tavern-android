package com.tavern.lite.ui.screens.quickreply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.QuickReplyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuickReplyViewModel @Inject constructor(
    private val repository: QuickReplyRepository,
    characterRepository: CharacterRepository,
    chatRepository: ChatRepository
) : ViewModel() {
    val sets: StateFlow<List<QuickReplySetEntity>> = repository.getAllSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val characters: StateFlow<List<CharacterEntity>> = characterRepository.getAllCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chats: StateFlow<List<ChatEntity>> = chatRepository.getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSetId = MutableStateFlow<Long?>(null)
    val selectedSetId: StateFlow<Long?> = _selectedSetId.asStateFlow()

    val replies: StateFlow<List<QuickReplyEntity>> = _selectedSetId
        .flatMapLatest { setId ->
            if (setId == null) flowOf(emptyList()) else repository.getRepliesForSet(setId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            sets.collect { currentSets ->
                val selected = _selectedSetId.value
                when {
                    currentSets.isEmpty() -> _selectedSetId.value = null
                    selected == null || currentSets.none { it.id == selected } -> {
                        _selectedSetId.value = currentSets.first().id
                    }
                }
            }
        }
    }

    fun selectSet(setId: Long) {
        if (setId <= 0L) return
        _selectedSetId.value = setId
    }

    fun createSet(
        name: String,
        scope: String,
        characterId: Long?,
        chatId: Long?,
        enabled: Boolean,
        displayOrder: Int
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank() || !isValidScope(scope) || !isValidSetScopeTarget(scope, characterId, chatId)) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val setId = repository.insertSet(
                QuickReplySetEntity(
                    name = normalizedName,
                    scope = scope,
                    characterId = characterId.takeIf { scope == "character" },
                    chatId = chatId.takeIf { scope == "chat" },
                    enabled = enabled,
                    displayOrder = displayOrder,
                    createdAt = now,
                    updatedAt = now
                )
            )
            _selectedSetId.value = setId
        }
    }

    fun updateSet(
        set: QuickReplySetEntity,
        name: String,
        scope: String,
        characterId: Long?,
        chatId: Long?,
        enabled: Boolean,
        displayOrder: Int
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank() || !isValidScope(scope) || !isValidSetScopeTarget(scope, characterId, chatId)) return
        viewModelScope.launch {
            repository.updateSet(
                set.copy(
                    name = normalizedName,
                    scope = scope,
                    characterId = characterId.takeIf { scope == "character" },
                    chatId = chatId.takeIf { scope == "chat" },
                    enabled = enabled,
                    displayOrder = displayOrder,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteSet(set: QuickReplySetEntity) {
        viewModelScope.launch {
            repository.deleteSet(set)
        }
    }

    fun createReply(
        setId: Long,
        label: String,
        script: String,
        icon: String?,
        automationId: String?,
        enabled: Boolean,
        requiresConfirmation: Boolean,
        allowAutoRun: Boolean,
        canSendMessages: Boolean,
        canTriggerGeneration: Boolean,
        displayOrder: Int
    ) {
        val normalizedLabel = label.trim()
        val normalizedScript = script.trim()
        if (setId <= 0L || normalizedLabel.isBlank() || normalizedScript.isBlank()) return
        viewModelScope.launch {
            repository.insertReply(
                QuickReplyEntity(
                    setId = setId,
                    label = normalizedLabel,
                    script = normalizedScript,
                    icon = icon?.trim()?.ifBlank { null },
                    automationId = automationId?.trim()?.ifBlank { null },
                    enabled = enabled,
                    requiresConfirmation = requiresConfirmation,
                    allowAutoRun = allowAutoRun,
                    canSendMessages = canSendMessages,
                    canTriggerGeneration = canTriggerGeneration,
                    displayOrder = displayOrder
                )
            )
        }
    }

    fun updateReply(
        reply: QuickReplyEntity,
        label: String,
        script: String,
        icon: String?,
        automationId: String?,
        enabled: Boolean,
        requiresConfirmation: Boolean,
        allowAutoRun: Boolean,
        canSendMessages: Boolean,
        canTriggerGeneration: Boolean,
        displayOrder: Int
    ) {
        val normalizedLabel = label.trim()
        val normalizedScript = script.trim()
        if (normalizedLabel.isBlank() || normalizedScript.isBlank()) return
        viewModelScope.launch {
            repository.updateReply(
                reply.copy(
                    label = normalizedLabel,
                    script = normalizedScript,
                    icon = icon?.trim()?.ifBlank { null },
                    automationId = automationId?.trim()?.ifBlank { null },
                    enabled = enabled,
                    requiresConfirmation = requiresConfirmation,
                    allowAutoRun = allowAutoRun,
                    canSendMessages = canSendMessages,
                    canTriggerGeneration = canTriggerGeneration,
                    displayOrder = displayOrder
                )
            )
        }
    }

    fun deleteReply(reply: QuickReplyEntity) {
        viewModelScope.launch {
            repository.deleteReply(reply)
        }
    }

    private val _shareResult = MutableStateFlow<QuickReplyShareResult?>(null)
    val shareResult: StateFlow<QuickReplyShareResult?> = _shareResult.asStateFlow()

    fun exportSet(set: QuickReplySetEntity) {
        viewModelScope.launch {
            val json = repository.exportSetToShareJson(set.id)
            _shareResult.value = if (json == null) {
                QuickReplyShareResult.ExportFailed
            } else {
                QuickReplyShareResult.Exported(set.name, json)
            }
        }
    }

    fun importFromJson(content: String) {
        val normalized = content.trim()
        if (normalized.isBlank()) {
            _shareResult.value = QuickReplyShareResult.ImportFailed("脚本包内容为空")
            return
        }
        viewModelScope.launch {
            repository.importSetFromShareJson(normalized)
                .onSuccess { setId ->
                    _selectedSetId.value = setId
                    _shareResult.value = QuickReplyShareResult.Imported
                }
                .onFailure { error ->
                    _shareResult.value = QuickReplyShareResult.ImportFailed(
                        error.message ?: "导入失败"
                    )
                }
        }
    }

    fun clearShareResult() {
        _shareResult.value = null
    }

    private fun isValidSetScopeTarget(scope: String, characterId: Long?, chatId: Long?): Boolean = when (scope) {
        "character" -> characterId.isValidTargetId()
        "chat" -> chatId.isValidTargetId()
        else -> true
    }

    private fun isValidScope(scope: String): Boolean = scope in VALID_SCOPES

    private fun Long?.isValidTargetId(): Boolean = this != null && this > 0L

    private companion object {
        val VALID_SCOPES = setOf("global", "character", "chat")
    }
}

sealed interface QuickReplyShareResult {
    data class Exported(val setName: String, val json: String) : QuickReplyShareResult
    data object ExportFailed : QuickReplyShareResult
    data object Imported : QuickReplyShareResult
    data class ImportFailed(val message: String) : QuickReplyShareResult
}
