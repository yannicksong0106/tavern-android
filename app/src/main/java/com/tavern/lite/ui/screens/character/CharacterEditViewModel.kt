package com.tavern.lite.ui.screens.character

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.data.db.entity.WorldBookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class CharacterEditState(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creator: String = "",
    val tags: String = "",
    val isEditing: Boolean = false,
    val characterId: Long? = null,
    val avatarPath: String? = null,
    val backgroundPath: String? = null,
    // World Book
    val worldBookId: Long? = null,
    val worldBookName: String? = null,
    // Author's Note
    val authorNoteContent: String = "",
    val authorNotePosition: String = "after_an",
    val authorNoteDepth: Int = 4,
    // Chattiness
    val chattiness: Int = 50
)

@HiltViewModel
class CharacterEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterRepository: CharacterRepository,
    private val worldBookRepository: WorldBookRepository,
    private val authorNoteDao: AuthorNoteDao
) : ViewModel() {

    private val _state = MutableStateFlow(CharacterEditState())
    val state: StateFlow<CharacterEditState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val worldBooks: StateFlow<List<WorldBookEntity>> = worldBookRepository.getAllWorldBooks()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadCharacter(id: Long) {
        viewModelScope.launch {
            val entity = characterRepository.getCharacterById(id) ?: return@launch
            val authorNote = authorNoteDao.getAuthorNoteSync(id)
            val worldBookName = entity.worldBookId?.let {
                worldBookRepository.getWorldBookById(it)?.name
            }
            _state.value = CharacterEditState(
                name = entity.name,
                description = entity.description,
                personality = entity.personality,
                firstMes = entity.firstMes,
                mesExample = entity.mesExample,
                systemPrompt = entity.systemPrompt ?: "",
                postHistoryInstructions = entity.postHistoryInstructions ?: "",
                creator = entity.creator,
                tags = entity.tags,
                isEditing = true,
                characterId = entity.id,
                avatarPath = entity.avatarPath,
                backgroundPath = entity.backgroundPath,
                worldBookId = entity.worldBookId,
                worldBookName = worldBookName,
                authorNoteContent = authorNote?.content ?: "",
                authorNotePosition = authorNote?.position ?: "after_an",
                authorNoteDepth = authorNote?.depth ?: 4,
                chattiness = entity.chattiness
            )
        }
    }

    fun updateField(field: String, value: String) {
        _state.value = when (field) {
            "name" -> _state.value.copy(name = value)
            "description" -> _state.value.copy(description = value)
            "personality" -> _state.value.copy(personality = value)
            "firstMes" -> _state.value.copy(firstMes = value)
            "mesExample" -> _state.value.copy(mesExample = value)
            "systemPrompt" -> _state.value.copy(systemPrompt = value)
            "postHistoryInstructions" -> _state.value.copy(postHistoryInstructions = value)
            "creator" -> _state.value.copy(creator = value)
            "tags" -> _state.value.copy(tags = value)
            "authorNoteContent" -> _state.value.copy(authorNoteContent = value)
            "authorNoteDepth" -> _state.value.copy(authorNoteDepth = value.toIntOrNull() ?: 4)
            "chattiness" -> _state.value.copy(chattiness = value.toIntOrNull()?.coerceIn(0, 100) ?: 50)
            else -> _state.value
        }
    }

    fun updateAuthorNotePosition(position: String) {
        _state.value = _state.value.copy(authorNotePosition = position)
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            try {
                val avatarPath = withContext(Dispatchers.IO) {
                    // Delete old avatar file if it exists
                    val oldPath = _state.value.avatarPath
                    if (oldPath != null) {
                        File(oldPath).delete()
                    }

                    val avatarDir = File(context.filesDir, "avatars")
                    avatarDir.mkdirs()
                    val avatarFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.png")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        avatarFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    avatarFile.absolutePath
                }
                _state.value = _state.value.copy(avatarPath = avatarPath)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("CharacterEditVM", "头像保存失败", e)
                _error.value = "头像保存失败: ${e.message}"
            }
        }
    }

    fun updateBackground(uri: Uri) {
        viewModelScope.launch {
            try {
                val bgPath = withContext(Dispatchers.IO) {
                    // Delete old background file if it's a custom image (not a preset)
                    val oldPath = _state.value.backgroundPath
                    if (oldPath != null && !oldPath.startsWith("preset:")) {
                        File(oldPath).delete()
                    }

                    val bgDir = File(context.filesDir, "backgrounds")
                    bgDir.mkdirs()
                    val bgFile = File(bgDir, "bg_${System.currentTimeMillis()}.png")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        bgFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    bgFile.absolutePath
                }
                _state.value = _state.value.copy(backgroundPath = bgPath)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("CharacterEditVM", "背景保存失败", e)
                _error.value = "背景保存失败: ${e.message}"
            }
        }
    }

    fun clearBackground() {
        _state.value = _state.value.copy(backgroundPath = null)
    }

    fun clearError() {
        _error.value = null
    }

    fun setWorldBook(worldBookId: Long, worldBookName: String) {
        _state.value = _state.value.copy(worldBookId = worldBookId, worldBookName = worldBookName)
    }

    fun clearWorldBook() {
        _state.value = _state.value.copy(worldBookId = null, worldBookName = null)
    }

    fun setPresetBackground(presetId: String) {
        _state.value = _state.value.copy(backgroundPath = "preset:$presetId")
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return

        viewModelScope.launch {
            val savedCharId = if (s.isEditing && s.characterId != null) {
                val entity = characterRepository.getCharacterById(s.characterId) ?: return@launch
                characterRepository.updateCharacter(
                    entity.copy(
                        name = s.name,
                        description = s.description,
                        personality = s.personality,
                        firstMes = s.firstMes,
                        mesExample = s.mesExample,
                        systemPrompt = s.systemPrompt.ifBlank { null },
                        postHistoryInstructions = s.postHistoryInstructions.ifBlank { null },
                        creator = s.creator,
                        avatarPath = s.avatarPath,
                        backgroundPath = s.backgroundPath,
                        worldBookId = s.worldBookId,
                        chattiness = s.chattiness
                    )
                )
                s.characterId
            } else {
                characterRepository.createCharacter(
                    com.tavern.lite.data.model.CharacterData(
                        name = s.name,
                        description = s.description,
                        personality = s.personality,
                        firstMes = s.firstMes,
                        mesExample = s.mesExample,
                        systemPrompt = s.systemPrompt.ifBlank { null },
                        postHistoryInstructions = s.postHistoryInstructions.ifBlank { null },
                        creator = s.creator
                    )
                )
            }

            // Save author's note
            if (savedCharId != null) {
                if (s.authorNoteContent.isNotBlank()) {
                    authorNoteDao.insertOrUpdate(
                        AuthorNoteEntity(
                            characterId = savedCharId,
                            content = s.authorNoteContent,
                            position = s.authorNotePosition,
                            depth = s.authorNoteDepth
                        )
                    )
                } else {
                    authorNoteDao.delete(savedCharId)
                }
            }

            onDone()
        }
    }
}
