package com.tavern.lite.ui.screens.persona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val personaRepository: PersonaRepository
) : ViewModel() {

    val personas: StateFlow<List<PersonaEntity>> = personaRepository.getAllPersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPersona(name: String, biography: String, avatarPath: String? = null) {
        viewModelScope.launch {
            personaRepository.createPersona(name, biography, avatarPath)
        }
    }

    fun updatePersona(id: Long, name: String, biography: String, avatarPath: String?) {
        viewModelScope.launch {
            personaRepository.updatePersona(id, name, biography, avatarPath)
        }
    }

    fun deletePersona(id: Long) {
        viewModelScope.launch {
            personaRepository.deletePersona(id)
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            personaRepository.setDefault(id)
        }
    }
}
