package com.tavern.lite.ui.screens.chatlist

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.ChatWithLastMessage
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.util.ChatExporter
import com.tavern.lite.util.ChatImporter
import com.tavern.lite.util.ImportReport
import com.tavern.lite.util.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val chatExporter: ChatExporter,
    private val chatImporter: ChatImporter
) : ViewModel() {

    private val characterId: Long = savedStateHandle.get<Long>("characterId") ?: 0

    private val _character = MutableStateFlow<CharacterEntity?>(null)
    val character: StateFlow<CharacterEntity?> = _character.asStateFlow()

    // Optimized: single query with JOIN instead of N+1 queries
    val chatsWithLastMessage: StateFlow<List<ChatWithLastMessage>> =
        chatRepository.getChatsWithLastMessage(characterId)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived chats list for compatibility
    val chats: StateFlow<List<ChatEntity>> = chatsWithLastMessage
        .map { list -> list.map { it.toChatEntity() } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportResult = MutableSharedFlow<String>()
    val exportResult: SharedFlow<String> = _exportResult.asSharedFlow()

    private val _exportedFile = MutableSharedFlow<File>()
    val exportedFile: SharedFlow<File> = _exportedFile.asSharedFlow()

    private val _importReport = MutableSharedFlow<ImportReport>()
    val importReport: SharedFlow<ImportReport> = _importReport.asSharedFlow()

    init {
        viewModelScope.launch {
            _character.value = characterRepository.getCharacterById(characterId)
        }
    }

    fun createChat(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val chatId = chatRepository.createChat(characterId)
            onCreated(chatId)
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            chatRepository.deleteChatById(chatId)
        }
    }

    fun renameChat(chatId: Long, name: String) {
        viewModelScope.launch {
            chatRepository.renameChat(chatId, name)
        }
    }

    fun exportChat(chatId: Long, format: ExportFormat) {
        viewModelScope.launch {
            try {
                val charName = _character.value?.name ?: "Unknown"
                val result = chatExporter.exportChat(chatId, format, charName)
                result.fold(
                    onSuccess = { file ->
                        _exportedFile.emit(file)
                        _exportResult.emit("导出成功")
                    },
                    onFailure = { _exportResult.emit("导出失败: ${it.message}") }
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("ChatListViewModel", "导出聊天失败", e)
                _exportResult.emit("导出失败: ${e.message}")
            }
        }
    }

    fun exportAllChats(format: ExportFormat) {
        viewModelScope.launch {
            try {
                val result = chatExporter.exportAllChats(characterId, format)
                result.fold(
                    onSuccess = { file ->
                        _exportedFile.emit(file)
                        _exportResult.emit("导出成功")
                    },
                    onFailure = { _exportResult.emit("导出失败: ${it.message}") }
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("ChatListViewModel", "批量导出失败", e)
                _exportResult.emit("导出失败: ${e.message}")
            }
        }
    }

    fun importChatFromFile(file: File) {
        viewModelScope.launch {
            val result = chatImporter.importChat(characterId, file)
            result.fold(
                onSuccess = { report -> _importReport.emit(report) },
                onFailure = { _exportResult.emit("导入失败: ${it.message}") }
            )
        }
    }
}
