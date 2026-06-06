package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.BgmDao
import com.tavern.lite.data.db.entity.BgmEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 角色背景音乐 Repository
 */
@Singleton
class BgmRepository @Inject constructor(
    private val bgmDao: BgmDao
) {
    fun getBgmsForCharacter(characterId: Long): Flow<List<BgmEntity>> =
        bgmDao.getBgmsForCharacter(characterId)

    suspend fun getBgmsForCharacterSync(characterId: Long): List<BgmEntity> =
        bgmDao.getBgmsForCharacterSync(characterId)

    suspend fun getBgmById(id: Long): BgmEntity? =
        bgmDao.getBgmById(id)

    suspend fun getDefaultBgm(characterId: Long): BgmEntity? =
        bgmDao.getDefaultBgm(characterId)

    suspend fun getBgmByEmotion(characterId: Long, emotion: String): BgmEntity? =
        bgmDao.getBgmByEmotion(characterId, emotion)

    suspend fun getBgmForEmotion(characterId: Long, emotion: String): BgmEntity? =
        bgmDao.getBgmByEmotion(characterId, emotion) ?: bgmDao.getDefaultBgm(characterId)

    suspend fun addBgm(
        characterId: Long,
        name: String,
        audioPath: String,
        loop: Boolean = true,
        volume: Float = 0.5f,
        emotion: String = "",
        displayOrder: Int = 0
    ): Long {
        val bgm = BgmEntity(
            characterId = characterId,
            name = name,
            audioPath = audioPath,
            loop = loop,
            volume = volume,
            emotion = emotion,
            displayOrder = displayOrder
        )
        return bgmDao.insert(bgm)
    }

    suspend fun updateBgm(bgm: BgmEntity) {
        bgmDao.update(bgm)
    }

    suspend fun deleteBgm(id: Long) =
        bgmDao.deleteById(id)

    suspend fun deleteAllForCharacter(characterId: Long) =
        bgmDao.deleteAllForCharacter(characterId)

    suspend fun getBgmCount(characterId: Long): Int =
        bgmDao.getBgmCount(characterId)
}
