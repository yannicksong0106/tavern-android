package com.tavern.lite.ui.screens.worldbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.importexport.LorebookExporter
import com.tavern.lite.data.repository.WorldBookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            entries.forEach { entry ->
                worldBookRepository.insertEntry(entry)
            }
        }
    }
}
