package com.tavern.lite.ui.screens.worldbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.importexport.LorebookExporter
import com.tavern.lite.data.repository.WorldBookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorldBookListViewModel @Inject constructor(
    private val worldBookRepository: WorldBookRepository,
    private val lorebookExporter: LorebookExporter
) : ViewModel() {

    val worldBooks: StateFlow<List<WorldBookEntity>> = worldBookRepository.getAllWorldBooks()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 导入失败信号：解析失败时置 true，屏幕消费后调 clearImportError 复位。
    private val _importError = MutableStateFlow(false)
    val importError: StateFlow<Boolean> = _importError.asStateFlow()

    fun clearImportError() {
        _importError.value = false
    }

    fun createWorldBook(name: String, description: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = worldBookRepository.createWorldBook(name, description)
            onDone(id)
        }
    }

    fun deleteWorldBook(worldBook: WorldBookEntity) {
        viewModelScope.launch {
            worldBookRepository.deleteWorldBook(worldBook)
        }
    }

    fun exportWorldBook(worldBook: WorldBookEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val entries = worldBookRepository.getEntries(worldBook.id).first()
            val json = lorebookExporter.exportToJson(worldBook, entries)
            onResult(json)
        }
    }

    fun importWorldBook(json: String, worldBookId: Long) {
        viewModelScope.launch {
            val entries = lorebookExporter.importFromJson(json, worldBookId)
            if (entries == null) {
                // 解析失败：回滚屏幕先建的孤儿世界书，避免列表留空 "Imported Lorebook" 行。
                worldBookRepository.getWorldBookById(worldBookId)?.let {
                    worldBookRepository.deleteWorldBook(it)
                }
                _importError.value = true
                return@launch
            }
            entries.forEach { entry ->
                worldBookRepository.insertEntry(entry)
            }
        }
    }
}
