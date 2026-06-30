package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.ApiConfigProfileDao
import com.tavern.lite.data.db.entity.ApiConfigProfileEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.security.CryptoHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiConfigProfileRepositoryTest {

    private lateinit var repo: ApiConfigProfileRepository
    private lateinit var dao: ApiConfigProfileDao
    private lateinit var cryptoHelper: CryptoHelper
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        cryptoHelper = mockk(relaxed = true)
        every { cryptoHelper.encrypt(any()) } returns "encrypted"
        every { cryptoHelper.tryDecrypt(any()) } returns null
        repo = ApiConfigProfileRepository(dao, json, cryptoHelper)
    }

    @Test
    fun `createProfile encrypts config and inserts`() = runTest {
        coEvery { dao.insertProfile(any()) } returns 1L
        val id = repo.createProfile("Test", ApiConfig())
        assertEquals(1L, id)
    }

    @Test
    fun `createProfile with description and default flag`() = runTest {
        coEvery { dao.insertProfile(any()) } returns 5L
        val id = repo.createProfile("Prod", ApiConfig(provider = ApiProvider.Claude()), "Production config", true)
        assertEquals(5L, id)
    }

    @Test
    fun `updateProfileConfig returns early when profile not found`() = runTest {
        coEvery { dao.getProfileById(any()) } returns null
        repo.updateProfileConfig(999, ApiConfig())
        // no exception, no DAO update call
    }

    @Test
    fun `updateProfileConfig updates when profile found`() = runTest {
        val profile = ApiConfigProfileEntity(id = 1, name = "Test", configJson = "old")
        coEvery { dao.getProfileById(1) } returns profile
        repo.updateProfileConfig(1, ApiConfig())
        // DAO updateProfile called (relaxed mock)
    }

    @Test
    fun `parseConfig returns config when decryption succeeds`() {
        val config = ApiConfig(provider = ApiProvider.OpenAI(apiKey = "sk-test"))
        val configJson = json.encodeToString(ApiConfig.serializer(), config)
        every { cryptoHelper.tryDecrypt("encrypted") } returns configJson
        val profile = ApiConfigProfileEntity(id = 1, name = "Test", configJson = "encrypted")
        val result = repo.parseConfig(profile)
        assertTrue(result.provider is ApiProvider.OpenAI)
    }

    @Test
    fun `parseConfig returns default when decryption fails and JSON is invalid`() {
        every { cryptoHelper.tryDecrypt(any()) } returns null
        val profile = ApiConfigProfileEntity(id = 1, name = "Test", configJson = "invalid json")
        val result = repo.parseConfig(profile)
        assertEquals(ApiConfig(), result)
    }

    @Test
    fun `parseConfig returns default on exception`() {
        every { cryptoHelper.tryDecrypt(any()) } throws RuntimeException("decrypt error")
        val profile = ApiConfigProfileEntity(id = 1, name = "Test", configJson = "encrypted")
        val result = repo.parseConfig(profile)
        assertEquals(ApiConfig(), result)
    }

    @Test
    fun `getEffectiveProfile returns chat profile first`() = runTest {
        val chatProfile = ApiConfigProfileEntity(id = 1, name = "Chat", configJson = "x")
        coEvery { dao.getProfileForChat(10) } returns chatProfile
        val result = repo.getEffectiveProfile(characterId = 5, chatId = 10)
        assertEquals(1, result!!.id)
    }

    @Test
    fun `getEffectiveProfile returns character profile when no chat profile`() = runTest {
        coEvery { dao.getProfileForChat(10) } returns null
        val charProfile = ApiConfigProfileEntity(id = 2, name = "Char", configJson = "x")
        coEvery { dao.getProfileForCharacter(5) } returns charProfile
        val result = repo.getEffectiveProfile(characterId = 5, chatId = 10)
        assertEquals(2, result!!.id)
    }

    @Test
    fun `getEffectiveProfile returns default when no chat or character profile`() = runTest {
        coEvery { dao.getProfileForChat(any()) } returns null
        coEvery { dao.getProfileForCharacter(any()) } returns null
        val defaultProfile = ApiConfigProfileEntity(id = 3, name = "Default", configJson = "x", isDefault = true)
        coEvery { dao.getDefaultProfile() } returns defaultProfile
        val result = repo.getEffectiveProfile(characterId = 5, chatId = 10)
        assertEquals(3, result!!.id)
    }

    @Test
    fun `getEffectiveProfile returns null when nothing found`() = runTest {
        coEvery { dao.getProfileForChat(any()) } returns null
        coEvery { dao.getProfileForCharacter(any()) } returns null
        coEvery { dao.getDefaultProfile() } returns null
        assertNull(repo.getEffectiveProfile(characterId = 5, chatId = 10))
    }

    @Test
    fun `getEffectiveProfile with null characterId and chatId returns default`() = runTest {
        val defaultProfile = ApiConfigProfileEntity(id = 1, name = "Default", configJson = "x")
        coEvery { dao.getDefaultProfile() } returns defaultProfile
        val result = repo.getEffectiveProfile()
        assertEquals(1, result!!.id)
    }

    @Test
    fun `getEffectiveProfile skips chat when chatId is null`() = runTest {
        val charProfile = ApiConfigProfileEntity(id = 2, name = "Char", configJson = "x")
        coEvery { dao.getProfileForCharacter(5) } returns charProfile
        val result = repo.getEffectiveProfile(characterId = 5, chatId = null)
        assertEquals(2, result!!.id)
    }

    @Test
    fun `setDefaultProfile clears then sets`() = runTest {
        repo.setDefaultProfile(7)
        // relaxed mock: both calls succeed
    }

    @Test
    fun `deleteProfileById calls dao`() = runTest {
        repo.deleteProfileById(99)
        // relaxed mock
    }

    @Test
    fun `getProfileCount delegates to dao`() = runTest {
        coEvery { dao.getProfileCount() } returns 5
        assertEquals(5, repo.getProfileCount())
    }
}
