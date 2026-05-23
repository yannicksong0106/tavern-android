package com.tavern.lite.worker

import android.content.Context
import android.util.Log
import com.tavern.lite.util.cleanCharacterPrefix
import java.io.IOException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tavern.lite.data.db.dao.ChatCharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.repository.ChatRepository
import com.tavern.lite.data.repository.PersonaRepository
import com.tavern.lite.data.repository.ScriptRepository
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.network.ChatApiService
import com.tavern.lite.network.ChatMessage
import com.tavern.lite.network.PromptBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class BackgroundProactiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatDao: ChatDao,
    private val characterDao: CharacterDao,
    private val chatCharacterDao: ChatCharacterDao,
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val scriptRepository: ScriptRepository,
    private val chatApiService: ChatApiService,
    private val apiConfigStore: ApiConfigStore,
    private val settingsStore: SettingsStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // 1. 检查开关
        val enabled = settingsStore.backgroundProactiveFlow.first()
        if (!enabled) return Result.success()

        // 2. 获取最近活跃聊天（最多 10 个）
        val recentChats = chatDao.getRecentChats(10)
        if (recentChats.isEmpty()) return Result.success()

        // 3. 随机选择一个聊天
        val chat = recentChats.random()

        return try {
            if (chat.isGroup) {
                processGroupChat(chat.id)
            } else {
                processSingleChat(chat.id, chat.characterId)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("BackgroundProactive", "Proactive message failed", e)
            when (e) {
                is IOException -> Result.retry()
                else -> Result.success()
            }
        }
    }

    private suspend fun processSingleChat(chatId: Long, characterId: Long) {
        val character = characterDao.getCharacterById(characterId) ?: return
        val chattiness = character.chattiness.coerceIn(0, 100)
        if (chattiness <= 0) return

        // 按健谈度概率决定是否发言
        val probability = chattiness / 100.0
        if (random.nextDouble() > probability) return

        val config = apiConfigStore.configFlow.first()
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        if (chatHistory.isEmpty()) return

        val persona = personaRepository.getEffectivePersona(characterId)

        val promptMessages = PromptBuilder.buildProactive(
            character = character,
            chatHistory = chatHistory.reversed(),
            userName = config.userName,
            persona = persona
        )

        var responseBuffer = ""
        try {
            chatApiService.streamChat(promptMessages, config).collect { chunk ->
                responseBuffer += chunk
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("BackgroundProactive", "Single chat stream failed", e)
            return
        }

        if (responseBuffer.isBlank()) return

        val cleanContent = responseBuffer.cleanCharacterPrefix(character.name)
        if (cleanContent.isNotBlank()) {
            val processedReply = scriptRepository.applyScripts(characterId, cleanContent, 1)
            val finalContent = if (processedReply != cleanContent) processedReply else cleanContent
            chatRepository.sendMessage(chatId, finalContent, "assistant")
        }
    }

    private suspend fun processGroupChat(chatId: Long) {
        val characters = chatCharacterDao.getCharacterEntitiesForChatSync(chatId)
        if (characters.isEmpty()) return

        // 按健谈度加权选择一个角色
        val selected = selectCharacterByChattiness(characters) ?: return

        val config = apiConfigStore.configFlow.first()
        val chatHistory = chatRepository.getRecentMessages(chatId, config.contextLength)
        if (chatHistory.isEmpty()) return

        val persona = personaRepository.getEffectivePersona(selected.id)
        val characterMap = characters.associateBy { it.id }

        val promptMessages = PromptBuilder.buildGroupProactive(
            characters = characters,
            respondingCharacter = selected,
            chatHistory = chatHistory.reversed(),
            characterMap = characterMap,
            userName = config.userName,
            persona = persona
        )

        var fullResponse = ""
        try {
            chatApiService.streamChat(promptMessages, config).collect { chunk ->
                fullResponse += chunk
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("BackgroundProactive", "Group chat stream failed", e)
            return
        }

        val cleanContent = fullResponse.cleanCharacterPrefix(selected.name)
        if (cleanContent.isNotBlank()) {
            val processedReply = scriptRepository.applyScripts(selected.id, cleanContent, 1)
            val finalContent = if (processedReply != cleanContent) processedReply else cleanContent
            chatRepository.sendMessage(chatId, finalContent, "assistant", selected.id)
        }
    }

    private fun selectCharacterByChattiness(characters: List<CharacterEntity>): CharacterEntity? {
        val totalWeight = characters.sumOf { it.chattiness.coerceIn(0, 100) }
        if (totalWeight <= 0) return characters.random()

        var random = random.nextDouble() * totalWeight
        for (char in characters) {
            random -= char.chattiness.coerceIn(0, 100)
            if (random <= 0) return char
        }
        return characters.last()
    }

    companion object {
        private val random = kotlin.random.Random.Default
    }
}
