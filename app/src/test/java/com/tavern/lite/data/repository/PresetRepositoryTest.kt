package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.PresetDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.store.SettingsStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PresetRepositoryTest {

    private lateinit var repository: PresetRepository
    private lateinit var fakeDao: FakePresetDao
    private lateinit var characterDao: CharacterDao
    private lateinit var chatDao: ChatDao
    private lateinit var settingsStore: SettingsStore

    @Before
    fun setup() {
        fakeDao = FakePresetDao()
        characterDao = mockk(relaxed = true)
        chatDao = mockk(relaxed = true)
        settingsStore = mockk(relaxed = true)
        repository = PresetRepository(
            presetDao = fakeDao,
            characterDao = characterDao,
            chatDao = chatDao,
            settingsStore = settingsStore
        )
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

    // ==================== resolveEffectivePreset ====================

    @Test
    fun `resolveEffectivePreset returns null when no presets configured`() = runTest {
        every { settingsStore.globalPresetIdFlow } returns flowOf(0L)
        coEvery { characterDao.getCharacterById(any()) } returns null
        coEvery { chatDao.getChatById(any()) } returns null
        assertNull(repository.resolveEffectivePreset(1, 1))
    }

    @Test
    fun `resolveEffectivePreset returns global when only global set`() = runTest {
        val global = PresetEntity(id = 10, name = "Global", systemPrompt = "Global prompt")
        fakeDao.inserted.add(global)
        every { settingsStore.globalPresetIdFlow } returns flowOf(10L)
        coEvery { characterDao.getCharacterById(any()) } returns null
        coEvery { chatDao.getChatById(any()) } returns null
        val result = repository.resolveEffectivePreset(1, 1)
        assertNotNull(result)
        assertEquals("Global prompt", result!!.systemPrompt)
    }

    @Test
    fun `resolveEffectivePreset returns null when globalPresetId is 0 and no char or chat preset`() = runTest {
        every { settingsStore.globalPresetIdFlow } returns flowOf(0L)
        coEvery { characterDao.getCharacterById(any()) } returns CharacterEntity(id = 1, name = "Alice")
        coEvery { chatDao.getChatById(any()) } returns ChatEntity(id = 1, characterId = 1)
        assertNull(repository.resolveEffectivePreset(1, 1))
    }

    @Test
    fun `resolveEffectivePreset merges character preset over global`() = runTest {
        val global = PresetEntity(id = 10, name = "Global", systemPrompt = "Global prompt", authorNote = "Global note")
        val charPreset = PresetEntity(id = 20, name = "Char", systemPrompt = "Char prompt")
        fakeDao.inserted.add(global)
        fakeDao.inserted.add(charPreset)
        every { settingsStore.globalPresetIdFlow } returns flowOf(10L)
        coEvery { characterDao.getCharacterById(any()) } returns CharacterEntity(id = 1, name = "Alice", presetId = 20)
        coEvery { chatDao.getChatById(any()) } returns ChatEntity(id = 1, characterId = 1)
        val result = repository.resolveEffectivePreset(1, 1)
        assertNotNull(result)
        assertEquals("Char prompt", result!!.systemPrompt)
        assertEquals("Global note", result.authorNote)
    }

    @Test
    fun `resolveEffectivePreset merges chat preset over character over global`() = runTest {
        val global = PresetEntity(id = 10, name = "Global", systemPrompt = "Global", postHistoryInstructions = "Global post")
        val charPreset = PresetEntity(id = 20, name = "Char", systemPrompt = "Char", postHistoryInstructions = "Char post")
        val chatPreset = PresetEntity(id = 30, name = "Chat", systemPrompt = "Chat")
        fakeDao.inserted.add(global)
        fakeDao.inserted.add(charPreset)
        fakeDao.inserted.add(chatPreset)
        every { settingsStore.globalPresetIdFlow } returns flowOf(10L)
        coEvery { characterDao.getCharacterById(any()) } returns CharacterEntity(id = 1, name = "Alice", presetId = 20)
        coEvery { chatDao.getChatById(any()) } returns ChatEntity(id = 1, characterId = 1, presetId = 30)
        val result = repository.resolveEffectivePreset(1, 1)
        assertNotNull(result)
        assertEquals("Chat", result!!.systemPrompt)
        assertEquals("Char post", result.postHistoryInstructions)
    }

    @Test
    fun `resolveEffectivePreset returns single character preset when no global`() = runTest {
        val charPreset = PresetEntity(id = 20, name = "Char", systemPrompt = "Char only")
        fakeDao.inserted.add(charPreset)
        every { settingsStore.globalPresetIdFlow } returns flowOf(0L)
        coEvery { characterDao.getCharacterById(any()) } returns CharacterEntity(id = 1, name = "Alice", presetId = 20)
        coEvery { chatDao.getChatById(any()) } returns null
        val result = repository.resolveEffectivePreset(1, 1)
        assertNotNull(result)
        assertEquals("Char only", result!!.systemPrompt)
    }

    @Test
    fun `resolveEffectivePreset isDefault merges via OR`() = runTest {
        val global = PresetEntity(id = 10, name = "Global", isDefault = true)
        val charPreset = PresetEntity(id = 20, name = "Char", isDefault = false)
        fakeDao.inserted.add(global)
        fakeDao.inserted.add(charPreset)
        every { settingsStore.globalPresetIdFlow } returns flowOf(10L)
        coEvery { characterDao.getCharacterById(any()) } returns CharacterEntity(id = 1, name = "Alice", presetId = 20)
        coEvery { chatDao.getChatById(any()) } returns null
        val result = repository.resolveEffectivePreset(1, 1)
        assertNotNull(result)
        assertTrue(result!!.isDefault)
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
    override fun getPresetsByScope(scope: String): Flow<List<PresetEntity>> = flowOf(inserted.filter { it.scope == scope })
    override suspend fun getPresetsByScopeSync(scope: String): List<PresetEntity> = inserted.filter { it.scope == scope }
}
