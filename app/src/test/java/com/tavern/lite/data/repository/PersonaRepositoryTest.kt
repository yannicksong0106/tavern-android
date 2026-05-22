package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.PersonaDao
import com.tavern.lite.data.db.entity.CharacterPersonaEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PersonaRepositoryTest {

    private lateinit var repository: PersonaRepository
    private lateinit var fakeDao: FakePersonaDao

    @Before
    fun setup() {
        fakeDao = FakePersonaDao()
        repository = PersonaRepository(fakeDao)
    }

    @Test
    fun `createPersona inserts with correct fields`() = runTest {
        val id = repository.createPersona("yannick", "A developer", "/avatar.png")
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
        val p = fakeDao.inserted[0]
        assertEquals("yannick", p.name)
        assertEquals("A developer", p.biography)
        assertEquals("/avatar.png", p.avatarPath)
    }

    @Test
    fun `createPersona with default avatar`() = runTest {
        repository.createPersona("anon", "mysterious")
        assertNull(fakeDao.inserted[0].avatarPath)
    }

    @Test
    fun `setDefault clears all then sets one`() = runTest {
        fakeDao.inserted.add(PersonaEntity(id = 1, name = "A"))
        fakeDao.inserted.add(PersonaEntity(id = 2, name = "B"))
        repository.setDefault(2)
        assertEquals(true, fakeDao.clearedDefaults)
        assertEquals(2L, fakeDao.defaultedId)
    }

    @Test
    fun `linkToCharacter unlinks existing then links new`() = runTest {
        repository.linkToCharacter(10, 5)
        assertEquals(10L, fakeDao.unlinkedCharacterId)
        assertNotNull(fakeDao.linkedEntity)
        assertEquals(10L, fakeDao.linkedEntity!!.characterId)
        assertEquals(5L, fakeDao.linkedEntity!!.personaId)
    }

    @Test
    fun `unlinkFromCharacter calls dao`() = runTest {
        repository.unlinkFromCharacter(10)
        assertEquals(10L, fakeDao.unlinkedCharacterId)
    }

    @Test
    fun `getEffectivePersona returns linked persona when exists`() = runTest {
        val linked = PersonaEntity(id = 5, name = "Linked Persona")
        fakeDao.linkedPersona = linked
        val result = repository.getEffectivePersona(10)
        assertNotNull(result)
        assertEquals("Linked Persona", result!!.name)
    }

    @Test
    fun `getEffectivePersona falls back to default when no link`() = runTest {
        fakeDao.linkedPersona = null
        val defaultP = PersonaEntity(id = 1, name = "Default", isDefault = true)
        fakeDao.defaultPersona = defaultP
        val result = repository.getEffectivePersona(10)
        assertNotNull(result)
        assertEquals("Default", result!!.name)
    }

    @Test
    fun `getEffectivePersona returns null when neither linked nor default`() = runTest {
        fakeDao.linkedPersona = null
        fakeDao.defaultPersona = null
        assertNull(repository.getEffectivePersona(10))
    }

    @Test
    fun `getPersonaById returns entity`() = runTest {
        fakeDao.inserted.add(PersonaEntity(id = 3, name = "Test"))
        val result = repository.getPersonaById(3)
        assertNotNull(result)
        assertEquals("Test", result!!.name)
    }

    @Test
    fun `deletePersona calls dao delete`() = runTest {
        repository.deletePersona(7)
        assertEquals(7L, fakeDao.deletedId)
    }
}

private class FakePersonaDao : PersonaDao {
    val inserted = mutableListOf<PersonaEntity>()
    var clearedDefaults = false
    var defaultedId: Long? = null
    var unlinkedCharacterId: Long? = null
    var linkedEntity: CharacterPersonaEntity? = null
    var linkedPersona: PersonaEntity? = null
    var defaultPersona: PersonaEntity? = null
    var deletedId: Long? = null
    private var nextId = 1L

    override fun getAllPersonas(): Flow<List<PersonaEntity>> = flowOf(inserted.toList())
    override fun getDefaultPersonaFlow(): Flow<PersonaEntity?> = flowOf(defaultPersona)
    override suspend fun getPersonaById(id: Long): PersonaEntity? = inserted.find { it.id == id }
    override suspend fun getDefaultPersona(): PersonaEntity? = defaultPersona
    override suspend fun insert(persona: PersonaEntity): Long {
        val id = nextId++
        inserted.add(persona.copy(id = id))
        return id
    }
    override suspend fun update(id: Long, name: String, biography: String, avatarPath: String?) {}
    override suspend fun delete(id: Long) { deletedId = id }
    override suspend fun clearAllDefaults() { clearedDefaults = true }
    override suspend fun setDefault(id: Long) { defaultedId = id }
    override suspend fun linkCharacterPersona(link: CharacterPersonaEntity) { linkedEntity = link }
    override suspend fun unlinkCharacterPersona(characterId: Long) { unlinkedCharacterId = characterId }
    override suspend fun getLinkedPersonaId(characterId: Long): Long? = linkedPersona?.id
    override suspend fun getLinkedPersona(characterId: Long): PersonaEntity? = linkedPersona
    override suspend fun getAllPersonasSync(): List<PersonaEntity> = inserted.toList()
}
