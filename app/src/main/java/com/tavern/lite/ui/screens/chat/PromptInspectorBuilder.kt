package com.tavern.lite.ui.screens.chat

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.repository.AuthorNoteRepository
import com.tavern.lite.data.repository.CharacterRepository
import com.tavern.lite.data.repository.MemoryRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.PresetRepository
import com.tavern.lite.data.repository.WorldBookRepository
import com.tavern.lite.network.PromptBuilder
import com.tavern.lite.network.PromptConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptInspectorBuilder @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val worldBookRepository: WorldBookRepository,
    private val memoryRepository: MemoryRepository,
    private val authorNoteRepository: AuthorNoteRepository,
    private val personaRepository: PersonaRepository,
    private val presetRepository: PresetRepository,
) {
    suspend fun buildSingle(
        chatId: Long,
        character: CharacterEntity,
        userMessage: String,
        chatHistory: List<MessageEntity>,
        userName: String,
        summary: String?,
    ): PromptInspectorState {
        val data = buildData(chatId, character.id, userMessage)
        val config = PromptConfig(
            character = character,
            userMessage = userMessage,
            chatHistory = chatHistory,
            worldBookEntries = data.worldBookEntries,
            userName = userName,
            memories = data.memories,
            memoryAtoms = data.memoryAtoms,
            authorNote = data.authorNote,
            persona = data.persona,
            preset = data.preset,
            summary = summary
        )
        val (messages, sections) = PromptBuilder.buildWithSections(config)
        return data.toState(messages, summary, character.name).copy(sections = sections)
    }

    suspend fun buildGroup(
        chatId: Long,
        characters: List<CharacterEntity>,
        respondingCharacter: CharacterEntity,
        userMessage: String,
        chatHistory: List<MessageEntity>,
        userName: String,
        summary: String?,
    ): PromptInspectorState {
        val data = buildData(chatId, respondingCharacter.id, userMessage)
        val effectiveCharacters = characters.ifEmpty { listOf(respondingCharacter) }
        val config = PromptConfig(
            character = respondingCharacter,
            userMessage = userMessage,
            chatHistory = chatHistory,
            worldBookEntries = data.worldBookEntries,
            userName = userName,
            memories = data.memories,
            memoryAtoms = data.memoryAtoms,
            persona = data.persona,
            authorNote = data.authorNote,
            preset = data.preset,
            summary = summary,
            characters = effectiveCharacters,
            characterMap = effectiveCharacters.associateBy { it.id },
            isGroupChat = true
        )
        val (messages, sections) = PromptBuilder.buildWithSections(config)
        return data.toState(messages, summary, respondingCharacter.name).copy(sections = sections)
    }

    private suspend fun buildData(
        chatId: Long,
        characterId: Long,
        userMessage: String
    ): PromptInspectorData {
        val character = characterRepository.getCharacterById(characterId)
            ?: return PromptInspectorData()
        val worldBookEntries = if (character.worldBookId != null) {
            worldBookRepository.matchEntriesRecursive(character.worldBookId, userMessage)
        } else {
            emptyList()
        }
        val memoryAtoms = memoryRepository.getRelevantAtoms(character.id, 10)
        val memories = if (memoryAtoms.isEmpty()) {
            memoryRepository.getRelevantMemories(character.id, userMessage)
        } else {
            emptyList()
        }
        return PromptInspectorData(
            worldBookEntries = worldBookEntries,
            memoryAtoms = memoryAtoms,
            memories = memories,
            authorNote = authorNoteRepository.getAuthorNoteSync(character.id),
            persona = personaRepository.getEffectivePersona(character.id),
            preset = presetRepository.resolveEffectivePreset(chatId, character.id)
        )
    }
}
