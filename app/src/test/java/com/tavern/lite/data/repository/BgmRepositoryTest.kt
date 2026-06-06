package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.BgmDao
import com.tavern.lite.data.db.entity.BgmEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BgmRepositoryTest {

    private lateinit var repository: BgmRepository
    private lateinit var fakeDao: FakeBgmDao

    @Before
    fun setup() {
        fakeDao = FakeBgmDao()
        repository = BgmRepository(fakeDao)
    }

    @Test
    fun `getBgmsForCharacter returns flow of bgms`() = runTest {
        val bgm = BgmEntity(id = 1, characterId = 10, name = "Theme", audioPath = "/audio/theme.mp3")
        fakeDao.bgms[10L] = mutableListOf(bgm)

        val flow = repository.getBgmsForCharacter(10)
        flow.collect { result ->
            assertEquals(1, result.size)
            assertEquals("Theme", result[0].name)
        }
    }

    @Test
    fun `getBgmsForCharacterSync returns list`() = runTest {
        val bgm = BgmEntity(id = 1, characterId = 10, name = "Theme", audioPath = "/audio/theme.mp3")
        fakeDao.bgms[10L] = mutableListOf(bgm)

        val result = repository.getBgmsForCharacterSync(10)
        assertEquals(1, result.size)
        assertEquals("Theme", result[0].name)
    }

    @Test
    fun `getBgmById returns bgm`() = runTest {
        val bgm = BgmEntity(id = 5, characterId = 10, name = "Battle", audioPath = "/audio/battle.mp3")
        fakeDao.allBgms.add(bgm)

        val result = repository.getBgmById(5)
        assertNotNull(result)
        assertEquals("Battle", result!!.name)
    }

    @Test
    fun `getBgmById returns null when not found`() = runTest {
        val result = repository.getBgmById(999)
        assertNull(result)
    }

    @Test
    fun `getDefaultBgm returns first bgm for character`() = runTest {
        val bgm = BgmEntity(id = 1, characterId = 10, name = "Default", audioPath = "/audio/default.mp3")
        fakeDao.bgms[10L] = mutableListOf(bgm)

        val result = repository.getDefaultBgm(10)
        assertNotNull(result)
        assertEquals("Default", result!!.name)
    }

    @Test
    fun `addBgm inserts bgm with correct fields`() = runTest {
        val id = repository.addBgm(
            characterId = 10,
            name = "New Theme",
            audioPath = "/audio/new.mp3",
            loop = true,
            volume = 0.7f,
            displayOrder = 1
        )
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
        val bgm = fakeDao.inserted[0]
        assertEquals(10L, bgm.characterId)
        assertEquals("New Theme", bgm.name)
        assertEquals("/audio/new.mp3", bgm.audioPath)
        assertEquals(true, bgm.loop)
        assertEquals(0.7f, bgm.volume, 0.01f)
        assertEquals(1, bgm.displayOrder)
    }

    @Test
    fun `updateBgm calls dao update`() = runTest {
        val bgm = BgmEntity(id = 1, characterId = 10, name = "Updated", audioPath = "/audio/updated.mp3")
        repository.updateBgm(bgm)
        assertEquals(bgm, fakeDao.updatedBgm)
    }

    @Test
    fun `deleteBgm calls dao deleteById`() = runTest {
        repository.deleteBgm(5)
        assertEquals(5L, fakeDao.deletedId)
    }

    @Test
    fun `deleteAllForCharacter calls dao`() = runTest {
        repository.deleteAllForCharacter(10)
        assertEquals(10L, fakeDao.deletedCharacterId)
    }

    @Test
    fun `getBgmCount returns count`() = runTest {
        fakeDao.bgms[10L] = mutableListOf(
            BgmEntity(id = 1, characterId = 10, name = "A", audioPath = "/a.mp3"),
            BgmEntity(id = 2, characterId = 10, name = "B", audioPath = "/b.mp3")
        )

        val count = repository.getBgmCount(10)
        assertEquals(2, count)
    }
}

private class FakeBgmDao : BgmDao {
    val bgms = mutableMapOf<Long, MutableList<BgmEntity>>()
    val allBgms = mutableListOf<BgmEntity>()
    val inserted = mutableListOf<BgmEntity>()
    var updatedBgm: BgmEntity? = null
    var deletedId: Long? = null
    var deletedCharacterId: Long? = null
    private var nextId = 1L

    override fun getBgmsForCharacter(characterId: Long): Flow<List<BgmEntity>> =
        flowOf(bgms[characterId]?.toList() ?: emptyList())

    override suspend fun getBgmsForCharacterSync(characterId: Long): List<BgmEntity> =
        bgms[characterId]?.toList() ?: emptyList()

    override suspend fun getBgmById(id: Long): BgmEntity? =
        allBgms.find { it.id == id } ?: inserted.find { it.id == id }

    override suspend fun getDefaultBgm(characterId: Long): BgmEntity? =
        bgms[characterId]?.firstOrNull()

    override suspend fun getBgmByEmotion(characterId: Long, emotion: String): BgmEntity? =
        bgms[characterId]?.find { it.emotion == emotion }

    override suspend fun getAllBgms(): List<BgmEntity> = allBgms.toList()

    override suspend fun insert(bgm: BgmEntity): Long {
        val id = nextId++
        val newBgm = bgm.copy(id = id)
        inserted.add(newBgm)
        bgms.getOrPut(bgm.characterId) { mutableListOf() }.add(newBgm)
        return id
    }

    override suspend fun insertAll(bgms: List<BgmEntity>) {
        bgms.forEach { insert(it) }
    }

    override suspend fun update(bgm: BgmEntity) {
        updatedBgm = bgm
    }

    override suspend fun delete(bgm: BgmEntity) {}

    override suspend fun deleteById(id: Long) {
        deletedId = id
    }

    override suspend fun deleteAllForCharacter(characterId: Long) {
        deletedCharacterId = characterId
        bgms.remove(characterId)
    }

    override suspend fun getBgmCount(characterId: Long): Int =
        bgms[characterId]?.size ?: 0
}
