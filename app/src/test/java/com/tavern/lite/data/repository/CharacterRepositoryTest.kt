package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.CharacterData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CharacterRepositoryTest {

    private lateinit var repository: CharacterRepository
    private lateinit var fakeDao: FakeCharacterDao
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Before
    fun setup() {
        fakeDao = FakeCharacterDao()
        repository = CharacterRepository(fakeDao, json)
    }

    @Test
    fun `createCharacter converts CharacterData to entity with JSON tags`() = runTest {
        val data = CharacterData(
            name = "Alice",
            description = "A friendly AI",
            personality = "Kind",
            firstMes = "Hello!",
            tags = listOf("fantasy", "friendly")
        )
        val id = repository.createCharacter(data, avatarPath = "/path/to/avatar.png")
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
        val entity = fakeDao.inserted[0]
        assertEquals("Alice", entity.name)
        assertEquals("A friendly AI", entity.description)
        assertEquals("Kind", entity.personality)
        assertEquals("Hello!", entity.firstMes)
        assertEquals("/path/to/avatar.png", entity.avatarPath)
        assertEquals("chara_card_v2", entity.spec)
        // Tags should be JSON-encoded
        assertTrue(entity.tags.contains("fantasy"))
        assertTrue(entity.tags.contains("friendly"))
    }

    @Test
    fun `createCharacter with empty tags produces JSON array`() = runTest {
        val data = CharacterData(name = "Bob", tags = emptyList())
        repository.createCharacter(data)
        assertEquals("[]", fakeDao.inserted[0].tags)
    }

    @Test
    fun `toCharacterCard converts entity back to card`() {
        val entity = CharacterEntity(
            id = 1,
            name = "Alice",
            description = "A dragon rider",
            personality = "Brave",
            firstMes = "Greetings, traveler!",
            mesExample = "{{user}}: Hi\n{{char}}: Hello!",
            tags = """["fantasy","adventure"]""",
            creator = "testuser",
            version = "2.0",
            systemPrompt = "Be creative",
            postHistoryInstructions = "Stay in character"
        )
        val card = repository.toCharacterCard(entity)

        assertEquals("chara_card_v2", card.spec)
        assertEquals("Alice", card.data.name)
        assertEquals("A dragon rider", card.data.description)
        assertEquals("Brave", card.data.personality)
        assertEquals("Greetings, traveler!", card.data.firstMes)
        assertEquals(listOf("fantasy", "adventure"), card.data.tags)
        assertEquals("testuser", card.data.creator)
        assertEquals("2.0", card.data.characterVersion)
        assertEquals("Be creative", card.data.systemPrompt)
    }

    @Test
    fun `toCharacterCard handles invalid JSON tags gracefully`() {
        val entity = CharacterEntity(id = 1, name = "Test", tags = "not valid json")
        val card = repository.toCharacterCard(entity)
        assertTrue(card.data.tags.isEmpty())
    }

    @Test
    fun `updateCharacter sets updatedAt timestamp`() = runTest {
        fakeDao.inserted.add(CharacterEntity(id = 1, name = "Alice"))
        val before = System.currentTimeMillis()
        repository.updateCharacter(fakeDao.inserted[0].copy(name = "Alice Updated"))
        assertTrue(fakeDao.updatedEntity!!.updatedAt >= before)
        assertEquals("Alice Updated", fakeDao.updatedEntity!!.name)
    }

    @Test
    fun `deleteCharacter calls deleteById`() = runTest {
        repository.deleteCharacter(42)
        assertEquals(42L, fakeDao.deletedId)
    }

    @Test
    fun `getCharacterById returns entity`() = runTest {
        fakeDao.inserted.add(CharacterEntity(id = 5, name = "Eve"))
        val result = repository.getCharacterById(5)
        assertNotNull(result)
        assertEquals("Eve", result!!.name)
    }

    @Test
    fun `getCharacterById returns null for missing id`() = runTest {
        val result = repository.getCharacterById(999)
        assertNull(result)
    }
}

private class FakeCharacterDao : CharacterDao {
    val inserted = mutableListOf<CharacterEntity>()
    var updatedEntity: CharacterEntity? = null
    var deletedId: Long? = null
    private var nextId = 1L

    override fun getAllCharacters(): Flow<List<CharacterEntity>> = flowOf(inserted.toList())
    override suspend fun getAllCharactersSync(): List<CharacterEntity> = inserted.toList()
    override suspend fun getCharacterById(id: Long): CharacterEntity? = inserted.find { it.id == id }
    override fun searchCharacters(query: String): Flow<List<CharacterEntity>> = flowOf(
        inserted.filter { it.name.contains(query, ignoreCase = true) }
    )
    override suspend fun insert(character: CharacterEntity): Long {
        val id = nextId++
        inserted.add(character.copy(id = id))
        return id
    }
    override suspend fun update(character: CharacterEntity) {
        updatedEntity = character
        val index = inserted.indexOfFirst { it.id == character.id }
        if (index >= 0) inserted[index] = character
    }
    override suspend fun delete(character: CharacterEntity) { inserted.removeIf { it.id == character.id } }
    override suspend fun deleteById(id: Long) { deletedId = id; inserted.removeIf { it.id == id } }
}
