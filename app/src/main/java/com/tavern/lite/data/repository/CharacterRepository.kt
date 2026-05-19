package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.CharacterCard
import com.tavern.lite.data.model.CharacterData
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CharacterDao,
    private val json: Json
) {
    fun getAllCharacters(): Flow<List<CharacterEntity>> = characterDao.getAllCharacters()

    fun searchCharacters(query: String): Flow<List<CharacterEntity>> = characterDao.searchCharacters(query)

    suspend fun getCharacterById(id: Long): CharacterEntity? = characterDao.getCharacterById(id)

    suspend fun createCharacter(data: CharacterData, avatarPath: String? = null): Long {
        val entity = CharacterEntity(
            name = data.name,
            description = data.description,
            personality = data.personality,
            firstMes = data.firstMes,
            mesExample = data.mesExample,
            systemPrompt = data.systemPrompt,
            postHistoryInstructions = data.postHistoryInstructions,
            tags = json.encodeToString(data.tags),
            creator = data.creator,
            version = data.characterVersion,
            spec = "chara_card_v2",
            avatarPath = avatarPath
        )
        return characterDao.insert(entity)
    }

    suspend fun updateCharacter(entity: CharacterEntity) {
        characterDao.update(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCharacter(id: Long) = characterDao.deleteById(id)

    fun toCharacterCard(entity: CharacterEntity): CharacterCard {
        val tags: List<String> = try {
            json.decodeFromString(entity.tags)
        } catch (_: Exception) {
            emptyList()
        }
        return CharacterCard(
            spec = entity.spec,
            data = CharacterData(
                name = entity.name,
                description = entity.description,
                personality = entity.personality,
                mesExample = entity.mesExample,
                firstMes = entity.firstMes,
                tags = tags,
                creator = entity.creator,
                characterVersion = entity.version,
                systemPrompt = entity.systemPrompt,
                postHistoryInstructions = entity.postHistoryInstructions
            )
        )
    }
}
