package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tavern.lite.data.db.entity.BranchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches WHERE chat_id = :chatId ORDER BY is_default DESC, created_at ASC")
    fun getBranchesForChat(chatId: Long): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches WHERE chat_id = :chatId ORDER BY is_default DESC, created_at ASC")
    suspend fun getBranchesForChatSync(chatId: Long): List<BranchEntity>

    @Query("SELECT * FROM branches WHERE chat_id = :chatId AND is_default = 1 LIMIT 1")
    suspend fun getDefaultBranch(chatId: Long): BranchEntity?

    @Query("SELECT * FROM branches WHERE id = :id")
    suspend fun getBranchById(id: Long): BranchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(branch: BranchEntity): Long

    @Update
    suspend fun update(branch: BranchEntity)

    @Delete
    suspend fun delete(branch: BranchEntity)

    @Query("DELETE FROM branches WHERE chat_id = :chatId")
    suspend fun deleteAllForChat(chatId: Long)
}
