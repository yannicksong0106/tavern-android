package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthorNoteRepositoryTest {

    private lateinit var repository: AuthorNoteRepository
    private lateinit var fakeDao: FakeAuthorNoteDao

    @Before
    fun setup() {
        fakeDao = FakeAuthorNoteDao()
        repository = AuthorNoteRepository(fakeDao)
    }

    @Test
    fun `getAuthorNote returns flow of note`() = runTest {
        val note = AuthorNoteEntity(id = 1, characterId = 10, content = "Test note")
        fakeDao.notes[10L] = note

        val flow = repository.getAuthorNote(10)
        flow.collect { result ->
            assertNotNull(result)
            assertEquals("Test note", result!!.content)
        }
    }

    @Test
    fun `getAuthorNoteSync returns note`() = runTest {
        val note = AuthorNoteEntity(id = 1, characterId = 10, content = "Sync note")
        fakeDao.notes[10L] = note

        val result = repository.getAuthorNoteSync(10)
        assertNotNull(result)
        assertEquals("Sync note", result!!.content)
    }

    @Test
    fun `getAuthorNoteSync returns null when not found`() = runTest {
        val result = repository.getAuthorNoteSync(999)
        assertNull(result)
    }

    @Test
    fun `insertOrUpdate calls dao`() = runTest {
        val note = AuthorNoteEntity(id = 1, characterId = 10, content = "New note")
        repository.insertOrUpdate(note)
        assertEquals(note, fakeDao.upsertedNote)
    }

    @Test
    fun `delete calls dao with characterId`() = runTest {
        repository.delete(10)
        assertEquals(10L, fakeDao.deletedCharacterId)
    }
}

private class FakeAuthorNoteDao : AuthorNoteDao {
    val notes = mutableMapOf<Long, AuthorNoteEntity>()
    var upsertedNote: AuthorNoteEntity? = null
    var deletedCharacterId: Long? = null

    override fun getAuthorNote(characterId: Long): Flow<AuthorNoteEntity?> =
        flowOf(notes[characterId])

    override suspend fun getAuthorNoteSync(characterId: Long): AuthorNoteEntity? =
        notes[characterId]

    override suspend fun insertOrUpdate(note: AuthorNoteEntity) {
        upsertedNote = note
        notes[note.characterId] = note
    }

    override suspend fun delete(characterId: Long) {
        deletedCharacterId = characterId
        notes.remove(characterId)
    }

    override suspend fun getAllAuthorNotesSync(): List<AuthorNoteEntity> =
        notes.values.toList()
}
