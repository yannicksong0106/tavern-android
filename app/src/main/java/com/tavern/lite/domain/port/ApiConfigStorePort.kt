package com.tavern.lite.domain.port

import com.tavern.lite.data.model.ApiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ApiConfigStorePort {
    val configFlow: Flow<ApiConfig>
    val activeProfileId: StateFlow<Long?>

    suspend fun save(config: ApiConfig)
    fun setActiveProfile(profileId: Long?)
}
