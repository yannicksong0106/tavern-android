package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tavern.lite.data.db.entity.ApiConfigProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiConfigProfileDao {

    @Query("SELECT * FROM api_config_profiles ORDER BY priority ASC, name ASC")
    fun getAllProfiles(): Flow<List<ApiConfigProfileEntity>>

    @Query("SELECT * FROM api_config_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ApiConfigProfileEntity?

    @Query("SELECT * FROM api_config_profiles WHERE id = :id")
    fun getProfileByIdFlow(id: Long): Flow<ApiConfigProfileEntity?>

    @Query("SELECT * FROM api_config_profiles WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ApiConfigProfileEntity?

    @Query("SELECT * FROM api_config_profiles WHERE is_default = 1 LIMIT 1")
    fun getDefaultProfileFlow(): Flow<ApiConfigProfileEntity?>

    @Query("SELECT * FROM api_config_profiles WHERE bound_character_id = :characterId ORDER BY priority ASC LIMIT 1")
    suspend fun getProfileForCharacter(characterId: Long): ApiConfigProfileEntity?

    @Query("SELECT * FROM api_config_profiles WHERE bound_chat_id = :chatId ORDER BY priority ASC LIMIT 1")
    suspend fun getProfileForChat(chatId: Long): ApiConfigProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ApiConfigProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ApiConfigProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ApiConfigProfileEntity)

    @Query("DELETE FROM api_config_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("UPDATE api_config_profiles SET is_default = 0 WHERE is_default = 1")
    suspend fun clearDefaultProfile()

    @Query("UPDATE api_config_profiles SET is_default = 1 WHERE id = :id")
    suspend fun setDefaultProfile(id: Long)

    /**
     * 原子地切换默认档案：清除旧默认 + 设置新默认在同一事务提交。
     * 避免两次独立写入之间被进程杀死留下零默认，或并发调用留下双默认。
     */
    @Transaction
    suspend fun switchDefaultProfile(id: Long) {
        clearDefaultProfile()
        setDefaultProfile(id)
    }

    @Query("SELECT COUNT(*) FROM api_config_profiles")
    suspend fun getProfileCount(): Int
}
