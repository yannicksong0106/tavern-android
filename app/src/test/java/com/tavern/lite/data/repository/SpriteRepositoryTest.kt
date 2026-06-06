package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.SpriteDao
import com.tavern.lite.data.db.entity.SpriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SpriteRepositoryTest {

    private lateinit var repository: SpriteRepository
    private lateinit var fakeDao: FakeSpriteDao

    @Before
    fun setup() {
        fakeDao = FakeSpriteDao()
        repository = SpriteRepository(fakeDao)
    }

    @Test
    fun `getSpritesForCharacter returns flow of sprites`() = runTest {
        val sprite = SpriteEntity(id = 1, characterId = 10, emotion = "happy", imagePath = "/sprites/happy.png")
        fakeDao.sprites[10L] = mutableListOf(sprite)

        val flow = repository.getSpritesForCharacter(10)
        flow.collect { result ->
            assertEquals(1, result.size)
            assertEquals("happy", result[0].emotion)
        }
    }

    @Test
    fun `getSpritesForCharacterSync returns list`() = runTest {
        val sprite = SpriteEntity(id = 1, characterId = 10, emotion = "sad", imagePath = "/sprites/sad.png")
        fakeDao.sprites[10L] = mutableListOf(sprite)

        val result = repository.getSpritesForCharacterSync(10)
        assertEquals(1, result.size)
        assertEquals("sad", result[0].emotion)
    }

    @Test
    fun `getSpriteByEmotion returns matching sprite`() = runTest {
        val sprite = SpriteEntity(id = 1, characterId = 10, emotion = "angry", imagePath = "/sprites/angry.png")
        fakeDao.sprites[10L] = mutableListOf(sprite)

        val result = repository.getSpriteByEmotion(10, "angry")
        assertNotNull(result)
        assertEquals("angry", result!!.emotion)
    }

    @Test
    fun `getSpriteByEmotion returns null when not found`() = runTest {
        val result = repository.getSpriteByEmotion(10, "nonexistent")
        assertNull(result)
    }

    @Test
    fun `getSpriteById returns sprite`() = runTest {
        val sprite = SpriteEntity(id = 5, characterId = 10, emotion = "happy", imagePath = "/sprites/happy.png")
        fakeDao.allSprites.add(sprite)

        val result = repository.getSpriteById(5)
        assertNotNull(result)
        assertEquals("happy", result!!.emotion)
    }

    @Test
    fun `getSpriteById returns null when not found`() = runTest {
        val result = repository.getSpriteById(999)
        assertNull(result)
    }

    @Test
    fun `getAvailableEmotions returns list of emotions`() = runTest {
        fakeDao.sprites[10L] = mutableListOf(
            SpriteEntity(id = 1, characterId = 10, emotion = "happy", imagePath = "/1.png"),
            SpriteEntity(id = 2, characterId = 10, emotion = "sad", imagePath = "/2.png"),
            SpriteEntity(id = 3, characterId = 10, emotion = "angry", imagePath = "/3.png")
        )

        val emotions = repository.getAvailableEmotions(10)
        assertEquals(3, emotions.size)
        assert(emotions.containsAll(listOf("happy", "sad", "angry")))
    }

    @Test
    fun `addSprite inserts sprite with correct fields`() = runTest {
        val id = repository.addSprite(
            characterId = 10,
            emotion = "surprised",
            imagePath = "/sprites/surprised.png",
            displayOrder = 2
        )
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
        val sprite = fakeDao.inserted[0]
        assertEquals(10L, sprite.characterId)
        assertEquals("surprised", sprite.emotion)
        assertEquals("/sprites/surprised.png", sprite.imagePath)
        assertEquals(2, sprite.displayOrder)
    }

    @Test
    fun `deleteSprite calls dao deleteById`() = runTest {
        repository.deleteSprite(5)
        assertEquals(5L, fakeDao.deletedId)
    }

    @Test
    fun `deleteAllForCharacter calls dao`() = runTest {
        repository.deleteAllForCharacter(10)
        assertEquals(10L, fakeDao.deletedCharacterId)
    }

    @Test
    fun `getSpriteCount returns count`() = runTest {
        fakeDao.sprites[10L] = mutableListOf(
            SpriteEntity(id = 1, characterId = 10, emotion = "happy", imagePath = "/1.png"),
            SpriteEntity(id = 2, characterId = 10, emotion = "sad", imagePath = "/2.png")
        )

        val count = repository.getSpriteCount(10)
        assertEquals(2, count)
    }
}

private class FakeSpriteDao : SpriteDao {
    val sprites = mutableMapOf<Long, MutableList<SpriteEntity>>()
    val allSprites = mutableListOf<SpriteEntity>()
    val inserted = mutableListOf<SpriteEntity>()
    var deletedId: Long? = null
    var deletedCharacterId: Long? = null
    private var nextId = 1L

    override fun getSpritesForCharacter(characterId: Long): Flow<List<SpriteEntity>> =
        flowOf(sprites[characterId]?.toList() ?: emptyList())

    override suspend fun getSpritesForCharacterSync(characterId: Long): List<SpriteEntity> =
        sprites[characterId]?.toList() ?: emptyList()

    override suspend fun getSpriteByEmotion(characterId: Long, emotion: String): SpriteEntity? =
        sprites[characterId]?.find { it.emotion == emotion }

    override suspend fun getSpriteById(id: Long): SpriteEntity? =
        allSprites.find { it.id == id } ?: inserted.find { it.id == id }

    override suspend fun getAvailableEmotions(characterId: Long): List<String> =
        sprites[characterId]?.map { it.emotion } ?: emptyList()

    override suspend fun getAllSprites(): List<SpriteEntity> = allSprites.toList()

    override suspend fun insert(sprite: SpriteEntity): Long {
        val id = nextId++
        val newSprite = sprite.copy(id = id)
        inserted.add(newSprite)
        sprites.getOrPut(sprite.characterId) { mutableListOf() }.add(newSprite)
        return id
    }

    override suspend fun insertAll(sprites: List<SpriteEntity>) {
        sprites.forEach { insert(it) }
    }

    override suspend fun delete(sprite: SpriteEntity) {}

    override suspend fun deleteById(id: Long) {
        deletedId = id
    }

    override suspend fun deleteAllForCharacter(characterId: Long) {
        deletedCharacterId = characterId
        sprites.remove(characterId)
    }

    override suspend fun getSpriteCount(characterId: Long): Int =
        sprites[characterId]?.size ?: 0
}
