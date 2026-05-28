package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.SpriteDao
import com.tavern.lite.data.db.entity.SpriteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpriteRepository @Inject constructor(
    private val spriteDao: SpriteDao
) {

    fun getSpritesForCharacter(characterId: Long): Flow<List<SpriteEntity>> =
        spriteDao.getSpritesForCharacter(characterId)

    suspend fun getSpritesForCharacterSync(characterId: Long): List<SpriteEntity> =
        spriteDao.getSpritesForCharacterSync(characterId)

    suspend fun getSpriteByEmotion(characterId: Long, emotion: String): SpriteEntity? =
        spriteDao.getSpriteByEmotion(characterId, emotion)

    suspend fun getSpriteById(id: Long): SpriteEntity? =
        spriteDao.getSpriteById(id)

    suspend fun getAvailableEmotions(characterId: Long): List<String> =
        spriteDao.getAvailableEmotions(characterId)

    suspend fun addSprite(
        characterId: Long,
        emotion: String,
        imagePath: String,
        displayOrder: Int = 0
    ): Long {
        return spriteDao.insert(
            SpriteEntity(
                characterId = characterId,
                emotion = emotion,
                imagePath = imagePath,
                displayOrder = displayOrder
            )
        )
    }

    suspend fun deleteSprite(id: Long) =
        spriteDao.deleteById(id)

    suspend fun deleteAllForCharacter(characterId: Long) =
        spriteDao.deleteAllForCharacter(characterId)

    suspend fun getSpriteCount(characterId: Long): Int =
        spriteDao.getSpriteCount(characterId)
}
