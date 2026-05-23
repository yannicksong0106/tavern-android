package com.tavern.lite.ui.screens.groupchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.GroupChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupChatCreateViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val groupChatRepository: GroupChatRepository
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> = characterRepository.getAllCharacters()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createGroupChat(
        characterIds: List<Long>,
        onCreated: (chatId: Long, primaryCharacterId: Long) -> Unit
    ) {
        viewModelScope.launch {
            val chatId = groupChatRepository.createGroupChat(characterIds)
            onCreated(chatId, characterIds.first())
        }
    }
}
