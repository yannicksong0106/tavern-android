package com.tavern.lite.domain.usecase

import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.repository.ApiConfigProfileRepository
import com.tavern.lite.domain.port.LegacyConfigReaderPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileMigrationUseCaseTest {

    private lateinit var useCase: ProfileMigrationUseCase
    private lateinit var legacyReader: LegacyConfigReaderPort
    private lateinit var profileRepo: ApiConfigProfileRepository

    @Before
    fun setup() {
        legacyReader = mockk(relaxed = true)
        profileRepo = mockk(relaxed = true)
        useCase = ProfileMigrationUseCase(legacyReader, profileRepo)
    }

    @Test
    fun `migrateIfNeeded returns false when profiles already exist`() = runTest {
        coEvery { profileRepo.getProfileCount() } returns 3
        assertFalse(useCase.migrateIfNeeded())
        coVerify(exactly = 0) { profileRepo.createProfile(any(), any(), any(), any()) }
    }

    @Test
    fun `migrateIfNeeded returns true and creates profile when no profiles exist`() = runTest {
        coEvery { profileRepo.getProfileCount() } returns 0
        coEvery { legacyReader.readConfig() } returns ApiConfig()
        assertTrue(useCase.migrateIfNeeded())
        coVerify(exactly = 1) { profileRepo.createProfile(any(), any(), any(), true) }
    }

    @Test
    fun `migrateIfNeeded reads config from legacy reader`() = runTest {
        coEvery { profileRepo.getProfileCount() } returns 0
        val config = ApiConfig(userName = "TestUser")
        coEvery { legacyReader.readConfig() } returns config
        useCase.migrateIfNeeded()
        coVerify { profileRepo.createProfile(any(), config, any(), any()) }
    }
}
