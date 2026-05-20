package com.tavern.lite.ui.screens.chatlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.util.ChatExporter
import com.tavern.lite.util.ChatImporter
import com.tavern.lite.util.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
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

    val chats: StateFlow<List<ChatEntity>> = chatRepository.getChatsForCharacter(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportResult = MutableSharedFlow<String>()
    val exportResult: SharedFlow<String> = _exportResult.asSharedFlow()

    private val _exportedFile = MutableSharedFlow<File>()
    val exportedFile: SharedFlow<File> = _exportedFile.asSharedFlow()

    // chatId -> Pair(raw role string, content preview)
    private val _lastMessages = MutableStateFlow<Map<Long, Pair<String, String>>>(emptyMap())
    val lastMessages: StateFlow<Map<Long, Pair<String, String>>> = _lastMessages.asStateFlow()

    init {
        viewModelScope.launch {
            _character.value = characterRepository.getCharacterById(characterId)
        }
        viewModelScope.launch {
            chats.collectLatest { chatList ->
                val map = mutableMapOf<Long, Pair<String, String>>()
                for (chat in chatList) {
                    val msg = chatRepository.getLastMessageForChat(chat.id)
                    if (msg != null) {
                        val preview = msg.content.take(80).replace("\n", " ")
                        map[chat.id] = Pair(msg.role, preview)
                    }
                }
                _lastMessages.value = map
            }
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
                _exportResult.emit("导出失败: ${e.message}")
            }
        }
    }

    fun importChatFromFile(file: File) {
        viewModelScope.launch {
            val result = chatImporter.importChat(characterId, file)
            result.fold(
                onSuccess = { msg -> _exportResult.emit(msg) },
                onFailure = { _exportResult.emit("导入失败: ${it.message}") }
            )
        }
    }
}
