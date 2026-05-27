package com.tavern.lite.worker

import android.content.Context
import android.util.Log
import java.io.IOException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tavern.lite.data.db.dao.ChatCharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.domain.usecase.ProactiveMessageUseCase
import com.tavern.lite.network.ApiConfigStore
import com.tavern.lite.util.ChatActiveTracker
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
    private val proactiveMessageUseCase: ProactiveMessageUseCase,
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

        // 3. 随机选择一个聊天（跳过用户正在前台操作的聊天）
        val availableChats = recentChats.filter { !ChatActiveTracker.isActive(it.id) }
        if (availableChats.isEmpty()) return Result.success()
        val chat = availableChats.random()

        return try {
            val config = apiConfigStore.configFlow.first()
            if (chat.isGroup) {
                processGroupChat(chat.id, config)
            } else {
                processSingleChat(chat.id, chat.characterId, config)
            }
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("BackgroundProactive", "Proactive message failed", e)
            when (e) {
                is IOException -> Result.retry()
                else -> Result.success()
            }
        }
    }

    private suspend fun processSingleChat(chatId: Long, characterId: Long, config: com.tavern.lite.data.model.ApiConfig) {
        val character = characterDao.getCharacterById(characterId) ?: return
        val chattiness = character.chattiness.coerceIn(0, 100)
        if (chattiness <= 0) return

        // 按健谈度概率决定是否发言
        val probability = chattiness / 100.0
        if (random.nextDouble() > probability) return

        proactiveMessageUseCase.sendProactiveMessage(chatId, character, config)
    }

    private suspend fun processGroupChat(chatId: Long, config: com.tavern.lite.data.model.ApiConfig) {
        val characters = chatCharacterDao.getCharacterEntitiesForChatSync(chatId)
        if (characters.isEmpty()) return

        // 按健谈度加权选择一个角色
        val selected = selectCharacterByChattiness(characters) ?: return

        proactiveMessageUseCase.sendProactiveGroupMessage(chatId, characters, selected, config)
    }

    private fun selectCharacterByChattiness(characters: List<CharacterEntity>): CharacterEntity? =
        selectByChattiness(characters)

    companion object {
        private val random = kotlin.random.Random.Default

        /**
         * 按健谈度加权选择一个角色。纯函数，可独立测试。
         */
        @androidx.annotation.VisibleForTesting
        internal fun selectByChattiness(
            characters: List<CharacterEntity>,
            rng: kotlin.random.Random = random
        ): CharacterEntity? {
            if (characters.isEmpty()) return null
            val totalWeight = characters.sumOf { it.chattiness.coerceIn(0, 100) }
            if (totalWeight <= 0) return characters.random(rng)

            var remaining = rng.nextDouble() * totalWeight
            for (char in characters) {
                remaining -= char.chattiness.coerceIn(0, 100)
                if (remaining <= 0) return char
            }
            return characters.last()
        }
    }
}
