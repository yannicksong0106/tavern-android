package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthorNoteRepository @Inject constructor(
    private val authorNoteDao: AuthorNoteDao
) {
    fun getAuthorNote(characterId: Long): Flow<AuthorNoteEntity?> =
        authorNoteDao.getAuthorNote(characterId)

    suspend fun getAuthorNoteSync(characterId: Long): AuthorNoteEntity? =
        authorNoteDao.getAuthorNoteSync(characterId)

    suspend fun insertOrUpdate(note: AuthorNoteEntity) =
        authorNoteDao.insertOrUpdate(note)

    suspend fun delete(characterId: Long) =
        authorNoteDao.delete(characterId)
}
