package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.PresetDao
import com.tavern.lite.data.db.entity.PresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PresetRepositoryTest {

    private lateinit var repository: PresetRepository
    private lateinit var fakeDao: FakePresetDao

    @Before
    fun setup() {
        fakeDao = FakePresetDao()
        repository = PresetRepository(fakeDao)
    }

    @Test
    fun `insertPreset returns id`() = runTest {
        val preset = PresetEntity(name = "Creative", systemPrompt = "Be creative")
        val id = repository.insertPreset(preset)
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
        assertEquals("Creative", fakeDao.inserted[0].name)
    }

    @Test
    fun `getPresetById returns entity`() = runTest {
        fakeDao.inserted.add(PresetEntity(id = 5, name = "Test"))
        val result = repository.getPresetById(5)
        assertNotNull(result)
        assertEquals("Test", result!!.name)
    }

    @Test
    fun `getPresetById returns null for missing`() = runTest {
        assertNull(repository.getPresetById(999))
    }

    @Test
    fun `getDefaultPreset returns default`() = runTest {
        fakeDao.defaultPreset = PresetEntity(id = 1, name = "Default", isDefault = true)
        val result = repository.getDefaultPreset()
        assertNotNull(result)
        assertEquals("Default", result!!.name)
    }

    @Test
    fun `getDefaultPreset returns null when none set`() = runTest {
        assertNull(repository.getDefaultPreset())
    }

    @Test
    fun `setDefaultPreset clears all then sets one`() = runTest {
        repository.setDefaultPreset(3)
        assertEquals(true, fakeDao.clearedDefaults)
        assertEquals(3L, fakeDao.defaultedId)
    }

    @Test
    fun `deletePreset calls dao`() = runTest {
        val preset = PresetEntity(id = 2, name = "ToDelete")
        repository.deletePreset(preset)
        assertNotNull(fakeDao.deletedPreset)
        assertEquals(2L, fakeDao.deletedPreset!!.id)
    }

    @Test
    fun `updatePreset calls dao`() = runTest {
        val preset = PresetEntity(id = 1, name = "Updated")
        repository.updatePreset(preset)
        assertNotNull(fakeDao.updatedPreset)
        assertEquals("Updated", fakeDao.updatedPreset!!.name)
    }
}

private class FakePresetDao : PresetDao {
    val inserted = mutableListOf<PresetEntity>()
    var defaultPreset: PresetEntity? = null
    var clearedDefaults = false
    var defaultedId: Long? = null
    var deletedPreset: PresetEntity? = null
    var updatedPreset: PresetEntity? = null
    private var nextId = 1L

    override fun getAllPresets(): Flow<List<PresetEntity>> = flowOf(inserted.toList())
    override suspend fun getPresetById(id: Long): PresetEntity? = inserted.find { it.id == id }
    override suspend fun getDefaultPreset(): PresetEntity? = defaultPreset
    override suspend fun insertPreset(preset: PresetEntity): Long {
        val id = nextId++
        inserted.add(preset.copy(id = id))
        return id
    }
    override suspend fun updatePreset(preset: PresetEntity) { updatedPreset = preset }
    override suspend fun deletePreset(preset: PresetEntity) { deletedPreset = preset }
    override suspend fun clearDefaultPresets() { clearedDefaults = true }
    override suspend fun setDefaultPreset(id: Long) { defaultedId = id }
    override suspend fun getAllPresetsSync(): List<PresetEntity> = inserted.toList()
}
