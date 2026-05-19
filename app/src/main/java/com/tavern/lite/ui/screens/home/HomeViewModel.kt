package com.tavern.lite.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.util.SillyTavernImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val importer: SillyTavernImporter
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _importResult = MutableSharedFlow<String>()
    val importResult: SharedFlow<String> = _importResult.asSharedFlow()

    val characters: StateFlow<List<CharacterEntity>> = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else 300L }
        .flatMapLatest { query ->
            if (query.isBlank()) characterRepository.getAllCharacters()
            else characterRepository.searchCharacters(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupChats: StateFlow<List<ChatEntity>> = chatRepository.getAllGroupChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteCharacter(id: Long) {
        viewModelScope.launch {
            characterRepository.deleteCharacter(id)
        }
    }

    fun deleteGroupChat(chatId: Long) {
        viewModelScope.launch {
            chatRepository.deleteChatById(chatId)
        }
    }

    fun exportCharacter(characterId: Long) {
        viewModelScope.launch {
            try {
                val exportDir = File(context.cacheDir, "exports")
                exportDir.mkdirs()
                val character = characterRepository.getCharacterById(characterId) ?: return@launch
                val outputFile = File(exportDir, "${character.name}.json")
                val result = importer.exportToJson(characterId, outputFile)
                result.fold(
                    onSuccess = { _importResult.emit("导出成功: ${outputFile.absolutePath}") },
                    onFailure = { _importResult.emit("导出失败: ${it.message}") }
                )
            } catch (e: Exception) {
                _importResult.emit("导出失败: ${e.message}")
            }
        }
    }

    fun exportCharacterPng(characterId: Long) {
        viewModelScope.launch {
            try {
                val exportDir = File(context.cacheDir, "exports")
                exportDir.mkdirs()
                val character = characterRepository.getCharacterById(characterId) ?: return@launch
                val outputFile = File(exportDir, "${character.name}.png")
                val result = importer.exportToPng(characterId, outputFile)
                result.fold(
                    onSuccess = { _importResult.emit("PNG 导出成功: ${outputFile.absolutePath}") },
                    onFailure = { _importResult.emit("PNG 导出失败: ${it.message}") }
                )
            } catch (e: Exception) {
                _importResult.emit("PNG 导出失败: ${e.message}")
            }
        }
    }

    fun importCharacter(uri: Uri) {
        viewModelScope.launch {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isPng = mimeType == "image/png" || mimeType == "application/octet-stream"

                // Copy URI content to temp file
                val ext = if (isPng) "png" else "json"
                val tempFile = File(context.cacheDir, "import_temp.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    _importResult.emit("无法读取文件")
                    return@launch
                }

                val isPngByMagic = try {
                    tempFile.inputStream().use { stream ->
                        val header = ByteArray(8)
                        stream.read(header) == 8 && header.contentEquals(byteArrayOf(
                            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                        ))
                    }
                } catch (_: Exception) { false }

                val result = if (isPng || isPngByMagic) {
                    importer.importFromPng(tempFile)
                } else {
                    importer.importFromJson(tempFile)
                }

                tempFile.delete()

                result.fold(
                    onSuccess = { _importResult.emit("导入成功") },
                    onFailure = { _importResult.emit("导入失败: ${it.message}") }
                )
            } catch (e: Exception) {
                _importResult.emit("导入失败: ${e.message}")
            }
        }
    }
}
