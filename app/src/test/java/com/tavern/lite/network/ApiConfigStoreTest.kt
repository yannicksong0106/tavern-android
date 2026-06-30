package com.tavern.lite.network

import android.content.Context
import com.tavern.lite.data.db.dao.ApiConfigProfileDao
import com.tavern.lite.data.db.entity.ApiConfigProfileEntity
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.security.CryptoHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiConfigStoreTest {

    private lateinit var store: ApiConfigStore
    private lateinit var profileDao: ApiConfigProfileDao
    private lateinit var cryptoHelper: CryptoHelper

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setup() {
        profileDao = mockk(relaxed = true)
        cryptoHelper = mockk(relaxed = true)
        every { cryptoHelper.tryDecrypt(any()) } returns null
        store = ApiConfigStore(
            context = mockk<Context>(relaxed = true),
            json = json,
            cryptoHelper = cryptoHelper,
            profileDao = profileDao
        )
    }

    @Test
    fun `readConfig uses default profile when no active profile is set`() = runTest {
        val expectedConfig = ApiConfig(
            provider = ApiProvider.OpenAI(
                baseUrl = "http://10.0.2.2:18080/v1",
                apiKey = "sk-smoke",
                model = "smoke-model"
            )
        )
        every { profileDao.getDefaultProfileFlow() } returns flowOf(profile(expectedConfig))

        val result = store.readConfig()

        assertTrue(result.provider is ApiProvider.OpenAI)
        val provider = result.provider as ApiProvider.OpenAI
        assertEquals("http://10.0.2.2:18080/v1", provider.baseUrl)
        assertEquals("smoke-model", provider.model)
    }

    @Test
    fun `readConfig falls back to default profile when active profile is missing`() = runTest {
        val expectedConfig = ApiConfig(
            provider = ApiProvider.OpenAI(
                baseUrl = "http://10.0.2.2:18080/v1",
                apiKey = "sk-smoke",
                model = "smoke-model"
            )
        )
        every { profileDao.getProfileByIdFlow(99L) } returns flowOf(null)
        coEvery { profileDao.getDefaultProfile() } returns profile(expectedConfig)

        store.setActiveProfile(99L)
        val result = store.readConfig()

        assertEquals("smoke-model", (result.provider as ApiProvider.OpenAI).model)
    }

    @Test
    fun `save writes to default profile when no active profile is set`() = runTest {
        val existingProfile = profile(ApiConfig(), id = 7L)
        val config = ApiConfig(
            provider = ApiProvider.OpenAI(
                baseUrl = "http://10.0.2.2:18080/v1",
                apiKey = "sk-smoke",
                model = "smoke-model"
            )
        )
        every { cryptoHelper.encrypt(any()) } returns "encrypted-config"
        coEvery { profileDao.getDefaultProfile() } returns existingProfile
        coEvery { profileDao.getProfileById(7L) } returns existingProfile

        store.save(config)

        coVerify {
            profileDao.updateProfile(match {
                it.id == 7L && it.configJson == "encrypted-config"
            })
        }
    }

    private fun profile(config: ApiConfig, id: Long = 1L): ApiConfigProfileEntity {
        return ApiConfigProfileEntity(
            id = id,
            name = "Default",
            configJson = json.encodeToString(ApiConfig.serializer(), config),
            isDefault = true
        )
    }
}
