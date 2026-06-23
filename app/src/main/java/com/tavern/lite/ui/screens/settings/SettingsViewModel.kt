package com.tavern.lite.ui.screens.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.data.store.TtsSettings
import com.tavern.lite.data.db.entity.ApiConfigProfileEntity
import com.tavern.lite.data.repository.ApiConfigProfileRepository
import com.tavern.lite.domain.usecase.ProfileMigrationUseCase
import com.tavern.lite.domain.usecase.TestConnectionUseCase
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.data.model.SearchEngine
import com.tavern.lite.data.model.WebSearchConfig
import com.tavern.lite.util.BackupManager
import com.tavern.lite.worker.ProactiveWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import javax.inject.Inject

sealed class BackupState {
    data object Idle : BackupState()
    data object Working : BackupState()
    data class Success(val message: String) : BackupState()
    data class Error(val message: String) : BackupState()
}

sealed class ConnectionTestState {
    data object Idle : ConnectionTestState()
    data object Testing : ConnectionTestState()
    data class Success(val reply: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiConfigStore: ApiConfigStore,
    private val testConnectionUseCase: TestConnectionUseCase,
    private val settingsStore: SettingsStore,
    private val proactiveWorkScheduler: ProactiveWorkScheduler,
    private val backupManager: BackupManager,
    private val profileMigrationUseCase: ProfileMigrationUseCase,
    private val profileRepository: ApiConfigProfileRepository
) : ViewModel() {

    val config: StateFlow<ApiConfig> = apiConfigStore.configFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    /** 所有配置档案 */
    val profiles: StateFlow<List<ApiConfigProfileEntity>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前激活的 profile ID */
    val activeProfileId: StateFlow<Long?> = apiConfigStore.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bubbleStyle: StateFlow<BubbleStyleConfig> = settingsStore.bubbleStyleFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BubbleStyleConfig())

    val language: StateFlow<String> = settingsStore.languageFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val backgroundProactive: StateFlow<Boolean> = settingsStore.backgroundProactiveFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val ttsSettings: StateFlow<TtsSettings> = settingsStore.ttsSettingsFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TtsSettings())

    val webSearchConfig: StateFlow<WebSearchConfig> = settingsStore.webSearchConfigFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WebSearchConfig())

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private var _lastBackupFile: File? = null

    // Cache clearing state
    private val _cacheCleared = MutableStateFlow(false)
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    // Storage sizes (mutable to trigger recomposition)
    private val _storageSizes = MutableStateFlow(Triple(0L, 0L, 0L))
    val storageSizes: StateFlow<Triple<Long, Long, Long>> = _storageSizes.asStateFlow()

    private val _pendingConfig = MutableSharedFlow<ApiConfig>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            // 执行配置档案迁移（首次升级时将 DataStore 配置迁移到 Room）
            val migrated = profileMigrationUseCase.migrateIfNeeded()
            if (migrated) {
                // 迁移完成后，设置默认 profile 为激活状态
                val defaultProfile = profileRepository.getDefaultProfile()
                if (defaultProfile != null) {
                    apiConfigStore.setActiveProfile(defaultProfile.id)
                }
            } else {
                // 已迁移过，激活默认 profile
                val defaultProfile = profileRepository.getDefaultProfile()
                if (defaultProfile != null) {
                    apiConfigStore.setActiveProfile(defaultProfile.id)
                }
            }
        }
        viewModelScope.launch {
            _pendingConfig.debounce(300).collect { config ->
                apiConfigStore.save(config)
            }
        }
        refreshStorageSizes()
    }

    fun saveConfig(config: ApiConfig) {
        _pendingConfig.tryEmit(config)
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

    fun updateReadTimeout(seconds: Long) {
        val current = config.value
        saveConfig(current.copy(readTimeoutSeconds = seconds))
    }

    fun updateUserName(name: String) {
        val current = config.value
        saveConfig(current.copy(userName = name))
    }

    fun testConnection() {
        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            try {
                val result = testConnectionUseCase(config.value)
                _testState.value = ConnectionTestState.Success(result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _testState.value = ConnectionTestState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetTestState() {
        _testState.value = ConnectionTestState.Idle
    }

    // ========== 配置档案管理 ==========

    /**
     * 切换到指定 profile
     */
    fun switchProfile(profileId: Long) {
        viewModelScope.launch {
            apiConfigStore.setActiveProfile(profileId)
        }
    }

    /**
     * 创建新配置档案
     */
    fun createProfile(name: String, description: String = "") {
        viewModelScope.launch {
            val currentConfig = config.value
            val newId = profileRepository.createProfile(
                name = name,
                config = currentConfig,
                description = description,
                isDefault = false
            )
            // 切换到新创建的 profile
            apiConfigStore.setActiveProfile(newId)
        }
    }

    /**
     * 删除配置档案
     */
    fun deleteProfile(profile: ApiConfigProfileEntity) {
        viewModelScope.launch {
            val currentActiveId = apiConfigStore.activeProfileId.value
            profileRepository.deleteProfile(profile)
            // 如果删除的是当前激活的 profile，切换到默认 profile
            if (currentActiveId == profile.id) {
                val defaultProfile = profileRepository.getDefaultProfile()
                apiConfigStore.setActiveProfile(defaultProfile?.id)
            }
        }
    }

    /**
     * 设置默认档案
     */
    fun setDefaultProfile(profileId: Long) {
        viewModelScope.launch {
            profileRepository.setDefaultProfile(profileId)
        }
    }

    /**
     * 更新档案名称
     */
    fun updateProfileName(profile: ApiConfigProfileEntity, newName: String) {
        viewModelScope.launch {
            profileRepository.updateProfile(profile.copy(name = newName))
        }
    }

    fun updateBubbleStyle(config: BubbleStyleConfig) {
        viewModelScope.launch {
            settingsStore.saveBubbleStyle(config)
        }
    }

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            settingsStore.saveLanguage(lang)
            // DataStore 写入完成后再触发 Activity 重建，避免竞态条件
            val localeList = when (lang) {
                "zh" -> LocaleListCompat.forLanguageTags("zh")
                "en" -> LocaleListCompat.forLanguageTags("en")
                "ja" -> LocaleListCompat.forLanguageTags("ja")
                "ko" -> LocaleListCompat.forLanguageTags("ko")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    fun updateBackgroundProactive(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.saveBackgroundProactive(enabled)
            if (enabled) {
                proactiveWorkScheduler.schedule()
            } else {
                proactiveWorkScheduler.cancel()
            }
        }
    }

    fun updateTtsSettings(settings: TtsSettings) {
        viewModelScope.launch {
            settingsStore.saveTtsSettings(settings)
        }
    }

    fun updateWebSearchConfig(config: WebSearchConfig) {
        viewModelScope.launch {
            settingsStore.saveWebSearchConfig(config)
        }
    }

    fun backupData() {
        viewModelScope.launch {
            _backupState.value = BackupState.Working
            backupManager.backup().fold(
                onSuccess = { file ->
                    _lastBackupFile = file
                    _backupState.value = BackupState.Success(file.name)
                },
                onFailure = { e ->
                    _backupState.value = BackupState.Error(e.message ?: "Unknown error")
                }
            )
        }
    }

    fun restoreData(inputStream: InputStream) {
        viewModelScope.launch {
            _backupState.value = BackupState.Working
            backupManager.restore(inputStream).fold(
                onSuccess = { result ->
                    _backupState.value = BackupState.Success(
                        "chars=${result.charactersRestored}, chats=${result.chatsRestored}, msgs=${result.messagesRestored}"
                    )
                },
                onFailure = { e ->
                    _backupState.value = BackupState.Error(e.message ?: "Unknown error")
                }
            )
        }
    }

    fun getLastBackupFile(): File? = _lastBackupFile

    fun resetBackupState() {
        _backupState.value = BackupState.Idle
    }

    // Storage management
    fun getDatabaseSize(): Long {
        val dbFile = context.getDatabasePath("tavern_db")
        return if (dbFile.exists()) dbFile.length() else 0
    }

    fun getCacheSize(): Long {
        return calculateDirSize(context.cacheDir)
    }

    fun getBackupSize(): Long {
        val backupDir = File(context.cacheDir, "backups")
        return if (backupDir.exists()) calculateDirSize(backupDir) else 0
    }

    fun clearCache() {
        viewModelScope.launch {
            context.cacheDir.deleteRecursively()
            _cacheCleared.value = true
            refreshStorageSizes()
            // Reset after a delay
            kotlinx.coroutines.delay(2000)
            _cacheCleared.value = false
        }
    }

    fun refreshStorageSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageSizes.value = Triple(
                getDatabaseSize(),
                getCacheSize(),
                getBackupSize()
            )
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) calculateDirSize(file) else file.length()
            }
        } else {
            size = dir.length()
        }
        return size
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    