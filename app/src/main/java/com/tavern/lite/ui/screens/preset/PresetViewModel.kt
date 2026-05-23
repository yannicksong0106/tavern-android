package com.tavern.lite.ui.screens.preset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetViewModel @Inject constructor(
    private val presetRepository: PresetRepository
) : ViewModel() {

    val presets: StateFlow<List<PresetEntity>> = presetRepository.getAllPresets()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertPreset(preset: PresetEntity) {
        viewModelScope.launch {
            presetRepository.insertPreset(preset)
        }
    }

    fun updatePreset(preset: PresetEntity) {
        viewModelScope.launch {
            presetRepository.updatePreset(preset.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deletePreset(preset: PresetEntity) {
        viewModelScope.launch {
            presetRepository.deletePreset(preset)
        }
    }

    fun setDefaultPreset(id: Long) {
        viewModelScope.launch {
            presetRepository.setDefaultPreset(id)
        }
    }
}
