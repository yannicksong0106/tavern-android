package com.tavern.lite.data.db

import com.tavern.lite.util.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证数据库迁移链完整性：确保从 version 1 到 version 31 的所有迁移都存在且连续。
 *
 * 这些测试在 JVM 上运行，不依赖 Android SQLite。
 * 实际的 SQL 执行验证由 TavernDatabaseSqlMigrationTest 覆盖。
 */
class TavernDatabaseMigrationTest {

    private val mainMigrationChain = listOf(
        TavernDatabase.MIGRATION_1_8,
        TavernDatabase.MIGRATION_8_9,
        TavernDatabase.MIGRATION_9_10,
        TavernDatabase.MIGRATION_10_11,
        TavernDatabase.MIGRATION_11_12,
        TavernDatabase.MIGRATION_12_13,
        TavernDatabase.MIGRATION_13_14,
        TavernDatabase.MIGRATION_14_15,
        TavernDatabase.MIGRATION_15_16,
        TavernDatabase.MIGRATION_16_17,
        TavernDatabase.MIGRATION_17_18,
        TavernDatabase.MIGRATION_18_19,
        TavernDatabase.MIGRATION_19_20,
        TavernDatabase.MIGRATION_20_21,
        TavernDatabase.MIGRATION_21_22,
        TavernDatabase.MIGRATION_22_23,
        TavernDatabase.MIGRATION_23_24,
        TavernDatabase.MIGRATION_24_25,
        TavernDatabase.MIGRATION_25_26,
        TavernDatabase.MIGRATION_26_27,
        TavernDatabase.MIGRATION_27_28,
        TavernDatabase.MIGRATION_28_29,
        TavernDatabase.MIGRATION_29_30,
        TavernDatabase.MIGRATION_30_31,
        TavernDatabase.MIGRATION_31_32,
        TavernDatabase.MIGRATION_32_33
    )

    private val earlyEntryMigrations = listOf(
        TavernDatabase.MIGRATION_2_8,
        TavernDatabase.MIGRATION_3_8,
        TavernDatabase.MIGRATION_4_8,
        TavernDatabase.MIGRATION_5_8,
        TavernDatabase.MIGRATION_6_8,
        TavernDatabase.MIGRATION_7_8
    )

    private val allMigrations = mainMigrationChain + earlyEntryMigrations

    @Test
    fun `all migrations are non-null`() {
        allMigrations.forEach { migration ->
            assertNotNull("Migration ${migration.startVersion} -> ${migration.endVersion} should not be null", migration)
        }
    }

    @Test
    fun `migration chain covers version 1 to 33 without gaps`() {
        val chain = mutableMapOf<Int, Int>()
        for (migration in mainMigrationChain) {
            chain[migration.startVersion] = migration.endVersion
        }

        // Walk the chain from 1 to 33.
        var current = 1
        val visited = mutableListOf(current)
        while (current != 33) {
            val next = chain[current]
                ?: throw AssertionError("No migration found from version $current. Chain: $visited")
            visited.add(next)
            current = next
        }

        assertEquals("Chain should end at version 33", 33, current)
    }

    @Test
    fun `no duplicate start versions in migration chain`() {
        val startVersions = allMigrations.map { it.startVersion }
        assertEquals("Each start version should appear exactly once",
            startVersions.size, startVersions.toSet().size)
    }

    @Test
    fun `no duplicate end versions in migration chain`() {
        val endVersions = mainMigrationChain.map { it.endVersion }
        assertEquals("Each end version should appear exactly once",
            endVersions.size, endVersions.toSet().size)
    }

    @Test
    fun `all migrations have startVersion less than endVersion`() {
        allMigrations.forEach { migration ->
            assertTrue(
                "Migration startVersion (${migration.startVersion}) should be < endVersion (${migration.endVersion})",
                migration.startVersion < migration.endVersion
            )
        }
    }

    @Test
    fun `migration 1 to 8 covers early users`() {
        val migration = TavernDatabase.MIGRATION_1_8
        assertEquals(1, migration.startVersion)
        assertEquals(8, migration.endVersion)
    }

    @Test
    fun `early entry migrations cover versions 2 to 7 without destructive fallback`() {
        assertEquals((2..7).toList(), earlyEntryMigrations.map { it.startVersion })
        earlyEntryMigrations.forEach { migration ->
            assertEquals(8, migration.endVersion)
        }
    }

    @Test
    fun `final migration reaches current database version 33`() {
        val lastMigration = TavernDatabase.MIGRATION_32_33
        assertEquals(33, lastMigration.endVersion)
    }

    @Test
    fun `database version matches final migration target`() {
        // The @Database(version = 33) should match the end of the migration chain.
        val maxVersion = mainMigrationChain.maxOf { it.endVersion }
        assertEquals("Final migration target should match @Database version", 33, maxVersion)
    }

    @Test
    fun `total migration count is correct`() {
        // 1->8 jump, then step migrations from 8->9 through 32->33 = 1 + 25 = 26.
        assertEquals(26, mainMigrationChain.size)
        assertEquals(6, earlyEntryMigrations.size)
        assertEquals(32, allMigrations.size)
    }

    // ==================== BackupManager version comparison ====================

    @Test
    fun `isVersionNewer returns true when backup is newer`() {
        assertTrue(BackupManager.isVersionNewer("1.3.0", "1.2.8"))
    }

    @Test
    fun `isVersionNewer returns false when backup is older`() {
        assertFalse(BackupManager.isVersionNewer("1.2.7", "1.2.8"))
    }

    @Test
    fun `isVersionNewer returns false when versions are equal`() {
        assertFalse(BackupManager.isVersionNewer("1.2.8", "1.2.8"))
    }

    @Test
    fun `isVersionNewer handles major version difference`() {
        assertTrue(BackupManager.isVersionNewer("2.0.0", "1.9.9"))
        assertFalse(BackupManager.isVersionNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun `isVersionNewer handles different segment counts`() {
        assertTrue(BackupManager.isVersionNewer("1.3", "1.2.8"))
        assertFalse(BackupManager.isVersionNewer("1.2", "1.2.8"))
    }

    @Test
    fun `parseVersion splits correctly`() {
        assertEquals(listOf(1, 2, 8), BackupManager.parseVersion("1.2.8"))
        assertEquals(listOf(2, 0, 0), BackupManager.parseVersion("2.0.0"))
    }

    @Test
    fun `parseVersion handles non-numeric parts`() {
        assertEquals(listOf(1, 2), BackupManager.parseVersion("1.2.beta"))
    }

    @Test
    fun `parseVersion handles empty string`() {
        assertEquals(emptyList<Int>(), BackupManager.parseVersion(""))
    }
}
