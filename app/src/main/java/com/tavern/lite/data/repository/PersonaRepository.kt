package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.PersonaDao
import com.tavern.lite.data.db.entity.CharacterPersonaEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao
) {
    fun getAllPersonas(): Flow<List<PersonaEntity>> = personaDao.getAllPersonas()

    fun getDefaultPersonaFlow(): Flow<PersonaEntity?> = personaDao.getDefaultPersonaFlow()

    suspend fun getPersonaById(id: Long): PersonaEntity? = personaDao.getPersonaById(id)

    suspend fun getDefaultPersona(): PersonaEntity? = personaDao.getDefaultPersona()

    suspend fun createPersona(name: String, biography: String, avatarPath: String? = null): Long {
        return personaDao.insert(
            PersonaEntity(
                name = name,
                biography = biography,
                avatarPath = avatarPath
            )
        )
    }

    suspend fun updatePersona(id: Long, name: String, biography: String, avatarPath: String?) {
        personaDao.update(id, name, biography, avatarPath)
    }

    suspend fun deletePersona(id: Long) {
        personaDao.delete(id)
    }

    suspend fun setDefault(id: Long) {
        personaDao.clearAllDefaults()
        personaDao.setDefault(id)
    }

    suspend fun linkToCharacter(characterId: Long, personaId: Long) {
        personaDao.unlinkCharacterPersona(characterId)
        personaDao.linkCharacterPersona(CharacterPersonaEntity(characterId, personaId))
    }

    suspend fun unlinkFromCharacter(characterId: Long) {
        personaDao.unlinkCharacterPersona(characterId)
    }

    suspend fun getLinkedPersona(characterId: Long): PersonaEntity? {
        return personaDao.getLinkedPersona(characterId)
    }

    suspend fun getEffectivePersona(characterId: Long): PersonaEntity? {
        // Per-character override takes priority over default
        val linked = personaDao.getLinkedPersona(characterId)
        if (linked != null) return linked
        return personaDao.getDefaultPersona()
    }
}
