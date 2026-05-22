package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.entity.ScriptEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScriptRepositoryTest {

    private lateinit var repository: ScriptRepository
    private lateinit var fakeDao: FakeScriptDao

    @Before
    fun setup() {
        fakeDao = FakeScriptDao()
        repository = ScriptRepository(fakeDao)
    }

    @Test
    fun `applyScripts with no scripts returns original text`() = runTest {
        val result = repository.applyScripts(1, "Hello world", 0)
        assertEquals("Hello world", result)
    }

    @Test
    fun `applyScripts with regex replacement`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Remove brackets",
                scriptType = 0,
                findPattern = "\\[.*?\\]",
                replacePattern = "",
                isRegex = true,
                enabled = true
            )
        )
        val result = repository.applyScripts(1, "Hello [note] world", 0)
        assertEquals("Hello  world", result)
    }

    @Test
    fun `applyScripts with literal replacement`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Replace word",
                scriptType = 0,
                findPattern = "hello",
                replacePattern = "hi",
                isRegex = false,
                enabled = true
            )
        )
        val result = repository.applyScripts(1, "hello world", 0)
        assertEquals("hi world", result)
    }

    @Test
    fun `applyScripts respects scriptType filter`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "User only",
                scriptType = 0,
                findPattern = "test",
                replacePattern = "replaced",
                isRegex = false,
                enabled = true
            )
        )
        // Should apply to type 0
        val result0 = repository.applyScripts(1, "test message", 0)
        assertEquals("replaced message", result0)

        // Should NOT apply to type 1
        val result1 = repository.applyScripts(1, "test message", 1)
        assertEquals("test message", result1)
    }

    @Test
    fun `applyScripts with type 2 applies to both`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Both",
                scriptType = 2,
                findPattern = "foo",
                replacePattern = "bar",
                isRegex = false,
                enabled = true
            )
        )
        val result0 = repository.applyScripts(1, "foo test", 0)
        val result1 = repository.applyScripts(1, "foo test", 1)
        assertEquals("bar test", result0)
        assertEquals("bar test", result1)
    }

    @Test
    fun `applyScripts with case insensitive literal`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Case insensitive",
                scriptType = 0,
                findPattern = "hello",
                replacePattern = "hi",
                isRegex = false,
                caseSensitive = false,
                enabled = true
            )
        )
        val result = repository.applyScripts(1, "HELLO world", 0)
        assertEquals("hi world", result)
    }

    @Test
    fun `applyScripts with case sensitive literal`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Case sensitive",
                scriptType = 0,
                findPattern = "hello",
                replacePattern = "hi",
                isRegex = false,
                caseSensitive = true,
                enabled = true
            )
        )
        val result = repository.applyScripts(1, "HELLO world", 0)
        assertEquals("HELLO world", result)
    }

    @Test
    fun `applyScripts handles invalid regex gracefully`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Bad regex",
                scriptType = 0,
                findPattern = "[invalid",
                replacePattern = "",
                isRegex = true,
                enabled = true
            )
        )
        val result = repository.applyScripts(1, "test [invalid regex", 0)
        assertEquals("test [invalid regex", result)
    }

    @Test
    fun `applyScripts applies multiple scripts in order`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "First",
                scriptType = 0,
                findPattern = "a",
                replacePattern = "b",
                isRegex = false,
                enabled = true,
                sortOrder = 1
            ),
            ScriptEntity(
                id = 2,
                characterId = 1,
                name = "Second",
                scriptType = 0,
                findPattern = "b",
                replacePattern = "c",
                isRegex = false,
                enabled = true,
                sortOrder = 2
            )
        )
        val result = repository.applyScripts(1, "a", 0)
        assertEquals("c", result)
    }

    @Test
    fun `applyScripts with regex capture groups`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Swap words",
                scriptType = 0,
                findPattern = "(\\w+) (\\w+)",
                replacePattern = "$2 $1",
                isRegex = true,
                enabled = true
            )
        )
        val result = repository.applyScripts(1, "hello world", 0)
        assertEquals("world hello", result)
    }

    @Test
    fun `applyScripts skips disabled scripts`() = runTest {
        fakeDao.scripts = listOf(
            ScriptEntity(
                id = 1,
                characterId = 1,
                name = "Disabled",
                scriptType = 0,
                findPattern = "test",
                replacePattern = "replaced",
                isRegex = false,
                enabled = false
            )
        )
        val result = repository.applyScripts(1, "test message", 0)
        assertEquals("test message", result)
    }
}

private class FakeScriptDao : ScriptDao {
    var scripts: List<ScriptEntity> = emptyList()

    override fun getScriptsForCharacter(characterId: Long) =
        throw UnsupportedOperationException("Not used in unit test")

    override suspend fun getEnabledScripts(characterId: Long) =
        scripts.filter { it.enabled }

    override suspend fun insertScript(script: ScriptEntity) =
        throw UnsupportedOperationException("Not used in unit test")

    override suspend fun updateScript(script: ScriptEntity) =
        throw UnsupportedOperationException("Not used in unit test")

    override suspend fun deleteScript(script: ScriptEntity) =
        throw UnsupportedOperationException("Not used in unit test")

    override suspend fun deleteAllForCharacter(characterId: Long) =
        throw UnsupportedOperationException("Not used in unit test")

    override suspend fun getAllScripts(): List<ScriptEntity> = scripts
}
