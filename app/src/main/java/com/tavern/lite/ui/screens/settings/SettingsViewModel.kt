package com.tavern.lite.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConnectionTestState {
    data object Idle : ConnectionTestState()
    data object Testing : ConnectionTestState()
    data class Success(val reply: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiConfigStore: ApiConfigStore,
    private val chatApiService: ChatApiService,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val config: StateFlow<ApiConfig> = apiConfigStore.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    val bubbleStyle: StateFlow<BubbleStyleConfig> = settingsStore.bubbleStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BubbleStyleConfig())

    val language: StateFlow<String> = settingsStore.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    fun saveConfig(config: ApiConfig) {
        viewModelScope.launch {
            apiConfigStore.save(config)
        }
    }

    fun updateProvider(provider: ApiProvider) {
        val current = config.value
        saveConfig(current.copy(provider = provider))
    }

    fun updateTemperature(temp: Float) {
        val current = config.value
        saveConfig(current.copy(temperature = temp))
    }

    fun updateMaxTokens(tokens: Int) {
        val current = config.value
        saveConfig(current.copy(maxTokens = tokens))
    }

    fun updateContextLength(length: Int) {
        val current = config.value
        saveConfig(current.copy(contextLength = length))
    }

    fun updateTopP(value: Float) {
        val current = config.value
        saveConfig(current.copy(topP = value))
    }

    fun updateFrequencyPenalty(value: Float) {
        val current = config.value
        saveConfig(current.copy(frequencyPenalty = value))
    }

    fun updatePresencePenalty(value: Float) {
        val current = config.value
        saveConfig(current.copy(presencePenalty = value))
    }

    fun updateUserName(name: String) {
        val current = config.value
        saveConfig(current.copy(userName = name))
    }

    fun testConnection() {
        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            try {
                val testConfig = config.value.copy(maxTokens = 50)
                val messages = listOf(
                    ChatMessage(role = "user", content = "Say 'hello' in one word.")
                )
                val result = StringBuilder()
                chatApiService.streamChat(messages, testConfig).collect { chunk ->
                    result.append(chunk)
                }
                _testState.value = ConnectionTestState.Success(result.toString().take(100))
            } catch (e: Exception) {
                _testState.value = ConnectionTestState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetTestState() {
        _testState.value = ConnectionTestState.Idle
    }

    fun updateBubbleStyle(config: BubbleStyleConfig) {
        viewModelScope.launch {
            settingsStore.saveBubbleStyle(config)
        }
    }

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            settingsStore.saveLanguage(lang)
        }
    }
}
