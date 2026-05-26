package com.tavern.lite.ui.screens.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.model.MemoryCategory
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.MemoryConsolidator
import com.tavern.lite.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MemoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoryRepository: MemoryRepository,
    private val characterRepository: CharacterRepository,
    private val memoryConsolidator: MemoryConsolidator
) : ViewModel() {

    private val initialCharacterId: Long = savedStateHandle.get<Long>("characterId") ?: 0

    // --- Character selector ---
    val characters: StateFlow<List<CharacterEntity>> = characterRepository.getAllCharacters()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCharacterId = MutableStateFlow(initialCharacterId)
    val selectedCharacterId: StateFlow<Long> = _selectedCharacterId.asStateFlow()

    // --- Category counts (for tab badges) ---
    val categoryCounts: StateFlow<Map<String, Int>> = _selectedCharacterId.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList())
        else memoryRepository.getCategoryCounts(id)
    }.map { list ->
        list.associate { it.category to it.count }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // --- Currently selected tab ---
    private val _selectedCategory = MutableStateFlow<MemoryCategory?>(null) // null = "All"
    val selectedCategory: StateFlow<MemoryCategory?> = _selectedCategory.asStateFlow()

    // --- Memory atoms (filtered by selected category or all) ---
    val atoms: StateFlow<List<MemoryAtomEntity>> = _selectedCharacterId.flatMapLatest { charId ->
        if (charId == 0L) return@flatMapLatest flowOf(emptyList())

        _selectedCategory.flatMapLatest { category ->
            if (category == null) {
                memoryRepository.getAtomsForCharacter(charId)
            } else {
                memoryRepository.getAtomsByCategory(charId, category.key)
            }
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Search ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    val searchResults: StateFlow<List<MemoryAtomEntity>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else {
                val charId = _selectedCharacterId.value
                if (charId == 0L) flowOf(emptyList())
                else memoryRepository.searchAtoms(charId, query)
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Sort mode ---
    enum class SortMode { IMPORTANCE, RECENCY, ACCESS_COUNT }
    private val _sortMode = MutableStateFlow(SortMode.IMPORTANCE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    // --- Last extraction time ---
    val lastExtractionTime: StateFlow<Long?> = _selectedCharacterId.flatMapLatest { id ->
        if (id == 0L) flowOf(null)
        else memoryRepository.getLastExtractionTime(id)
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Real-time update pulse indicator ---
    private val _showPulse = MutableStateFlow(false)
    val showPulse: StateFlow<Boolean> = _showPulse.asStateFlow()

    // --- Total memory count ---
    val totalMemoryCount: StateFlow<Int> = _selectedCharacterId.flatMapLatest { id ->
        if (id == 0L) flowOf(0)
        else memoryRepository.getAtomsForCharacter(id).map { it.size }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Edit dialog state ---
    private val _editingAtom = MutableStateFlow<MemoryAtomEntity?>(null)
    val editingAtom: StateFlow<MemoryAtomEntity?> = _editingAtom.asStateFlow()

    init {
        // Auto-select first character if none selected
        viewModelScope.launch {
            characters.collect { list ->
                if (_selectedCharacterId.value == 0L && list.isNotEmpty()) {
                    _selectedCharacterId.value = list.first().id
                }
            }
        }

        // Observe new memory additions for pulse animation
        viewModelScope.launch {
            var previousSize = 0
            _selectedCharacterId.flatMapLatest { id ->
                if (id == 0L) flowOf(emptyList())
                else memoryRepository.getAtomsForCharacter(id)
            }.collect { newList ->
                if (newList.size > previousSize && previousSize > 0) {
                    _showPulse.value = true
                    delay(2000)
                    _showPulse.value = false
                }
                previousSize = newList.size
            }
        }

        // Purge expired temporary memories on load
        viewModelScope.launch {
            memoryRepository.purgeExpired()
        }
    }

    fun selectCharacter(id: Long) {
        _selectedCharacterId.value = id
        _selectedCategory.value = null
        _searchQuery.value = ""
        _searchActive.value = false
    }

    fun selectCategory(category: MemoryCategory?) {
        _selectedCategory.value = category
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _searchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun startEdit(atom: MemoryAtomEntity) {
        _editingAtom.value = atom
    }

    fun clearEdit() {
        _editingAtom.value = null
    }

    fun addAtom(content: String, category: MemoryCategory, importance: Int) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val expiresAt = if (category == MemoryCategory.TEMPORARY) {
                System.currentTimeMillis() + 4 * 3600_000L
            } else null
            memoryRepository.insertAtom(
                MemoryAtomEntity(
                    characterId = _selectedCharacterId.value,
                    content = content,
                    category = category.key,
                    importance = importance,
                    source = "manual",
                    createdAt = System.currentTimeMillis(),
                    lastAccessed = System.currentTimeMillis(),
                    expiresAt = expiresAt
                )
            )
        }
    }

    fun updateAtom(atom: MemoryAtomEntity) {
        viewModelScope.launch { memoryRepository.updateAtom(atom) }
    }

    fun deleteAtom(id: Long) {
        viewModelScope.launch { memoryRepository.deleteAtom(id) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            memoryRepository.deleteAllAtomsForCharacter(_selectedCharacterId.value)
            memoryRepository.deleteAllForCharacter(_selectedCharacterId.value)
        }
    }
}
