package com.tavern.lite.ui.screens.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val characterId: Long = savedStateHandle.get<Long>("characterId") ?: 0

    val memories: StateFlow<List<MemoryEntity>> = memoryRepository
        .getMemoriesForCharacter(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMemory(content: String, importance: Int) {
        if (content.isBlank()) return
        viewModelScope.launch {
            memoryRepository.addMemory(characterId, content, importance)
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryRepository.updateMemory(memory)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            memoryRepository.deleteAllForCharacter(characterId)
        }
    }
}
