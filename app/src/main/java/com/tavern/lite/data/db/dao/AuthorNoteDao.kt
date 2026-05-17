package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthorNoteDao {

    @Query("SELECT * FROM author_notes WHERE character_id = :characterId LIMIT 1")
    fun getAuthorNote(characterId: Long): Flow<AuthorNoteEntity?>

    @Query("SELECT * FROM author_notes WHERE character_id = :characterId LIMIT 1")
    suspend fun getAuthorNoteSync(characterId: Long): AuthorNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(note: AuthorNoteEntity)

    @Query("DELETE FROM author_notes WHERE character_id = :characterId")
    suspend fun delete(characterId: Long)
}
