package com.tavern.lite.ui.screens.script

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.ScriptEntity
import com.tavern.lite.data.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScriptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scriptRepository: ScriptRepository
) : ViewModel() {

    private val characterId: Long = savedStateHandle.get<Long>("characterId") ?: 0

    val scripts: StateFlow<List<ScriptEntity>> =
        scriptRepository.getScriptsForCharacter(characterId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addScript(name: String, comment: String, scriptType: Int, findPattern: String, replacePattern: String, isRegex: Boolean, caseSensitive: Boolean) {
        viewModelScope.launch {
            scriptRepository.insertScript(
                ScriptEntity(
                    characterId = characterId,
                    name = name,
                    comment = comment,
                    scriptType = scriptType,
                    findPattern = findPattern,
                    replacePattern = replacePattern,
                    isRegex = isRegex,
                    caseSensitive = caseSensitive,
                    sortOrder = (scripts.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
                )
            )
        }
    }

    fun updateScript(script: ScriptEntity) {
        viewModelScope.launch {
            scriptRepository.updateScript(script)
        }
    }

    fun deleteScript(script: ScriptEntity) {
        viewModelScope.launch {
            scriptRepository.deleteScript(script)
        }
    }

    fun toggleEnabled(script: ScriptEntity) {
        viewModelScope.launch {
            scriptRepository.updateScript(script.copy(enabled = !script.enabled))
        }
    }
}
