package com.tavern.lite.ui.screens.worldbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import com.tavern.lite.data.repository.WorldBookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
class WorldBookEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val worldBookRepository: WorldBookRepository
) : ViewModel() {

    private val worldBookId: Long = savedStateHandle.get<Long>("worldBookId") ?: 0

    private val _worldBook = MutableStateFlow<WorldBookEntity?>(null)
    val worldBook: StateFlow<WorldBookEntity?> = _worldBook.asStateFlow()

    val entries: StateFlow<List<WorldBookEntryEntity>> = worldBookRepository.getEntries(worldBookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _worldBook.value = worldBookRepository.getWorldBookById(worldBookId)
        }
    }

    fun updateWorldBook(name: String, description: String) {
        viewModelScope.launch {
            val book = _worldBook.value ?: return@launch
            val updated = book.copy(
                name = name,
                description = description,
                updatedAt = System.currentTimeMillis()
            )
            worldBookRepository.updateWorldBook(updated) // need to add this method
            _worldBook.value = updated
        }
    }

    fun addEntry(
        comment: String,
        content: String,
        keys: List<String>,
        keysSecondary: List<String>,
        constant: Boolean,
        selective: Boolean,
        selectiveLogic: Int
    ) {
        viewModelScope.launch {
            worldBookRepository.insertEntry(
                WorldBookEntryEntity(
                    worldBookId = worldBookId,
                    comment = comment,
                    content = content,
                    keys = JsonArray(keys.map { JsonPrimitive(it) }).toString(),
                    keysSecondary = JsonArray(keysSecondary.map { JsonPrimitive(it) }).toString(),
                    constant = constant,
                    selective = selective,
                    selectiveLogic = selectiveLogic,
                    orderVal = (entries.value.maxOfOrNull { it.orderVal } ?: 0) + 1
                )
            )
        }
    }

    fun updateEntry(entry: WorldBookEntryEntity) {
        viewModelScope.launch {
            worldBookRepository.updateEntry(entry)
        }
    }

    fun deleteEntry(entry: WorldBookEntryEntity) {
        viewModelScope.launch {
            worldBookRepository.deleteEntry(entry)
        }
    }

    fun toggleEntryDisabled(entry: WorldBookEntryEntity) {
        viewModelScope.launch {
            worldBookRepository.updateEntry(entry.copy(disabled = !entry.disabled))
        }
    }
}
