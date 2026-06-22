package com.tavern.lite.data.db

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TavernDatabaseEarlyMigrationTest {

    @Test
    fun `migration 2 to current preserves representative early chat rows`() {
        withManualDatabase(version = 2, createSchema = ::createVersion2Schema) { db ->
            insertVersion2ChatData(db)

            migrateToCurrent(TavernDatabase.MIGRATION_2_8, db)

            assertTrue(getTableColumns(db, "messages").contains("image_paths"))
            assertTrue(getTableColumns(db, "chats").contains("scheduling_strategy"))
            assertTrue(tableExists(db, "quick_reply_sets"))
            assertTrue(tableExists(db, "quick_replies"))

            assertEquals("Alice", queryString(db, "SELECT name FROM characters WHERE id = 1"))
            assertEquals("Main chat", queryString(db, "SELECT name FROM chats WHERE id = 5"))
            assertEquals("Hello from v2", queryString(db, "SELECT content FROM messages WHERE id = 100"))
            assertEquals("[]", queryString(db, "SELECT swipe_content FROM messages WHERE id = 100"))
            assertEquals("[]", queryString(db, "SELECT image_paths FROM messages WHERE id = 100"))
            assertEquals("natural", queryString(db, "SELECT scheduling_strategy FROM chats WHERE id = 5"))
        }
    }

    @Test
    fun `migration 6 to current preserves swipe memory and script rows`() {
        withManualDatabase(version = 6, createSchema = ::createVersion6Schema) { db ->
            insertVersion6ChatData(db)

            migrateToCurrent(TavernDatabase.MIGRATION_6_8, db)

            assertTrue(tableExists(db, "author_notes"))
            assertTrue(tableExists(db, "personas"))
            assertTrue(tableExists(db, "character_personas"))

            assertEquals("""["Alt one","Alt two"]""", queryString(db, "SELECT swipe_content FROM messages WHERE id = 100"))
            assertEquals(1, queryInt(db, "SELECT swipe_index FROM messages WHERE id = 100"))
            assertEquals("Keeps a silver key", queryString(db, "SELECT content FROM memories WHERE id = 20"))
            assertEquals("{{user}}", queryString(db, "SELECT find_pattern FROM scripts WHERE id = 30"))
        }
    }

    private fun migrateToCurrent(entryMigration: Migration, db: SupportSQLiteDatabase) {
        entryMigration.migrate(db)
        listOf(
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
            TavernDatabase.MIGRATION_30_31
        ).forEach { migration ->
            migration.migrate(db)
        }
    }

    private fun createVersion2Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE characters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                personality TEXT NOT NULL DEFAULT '',
                first_mes TEXT NOT NULL DEFAULT '',
                mes_example TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE chats (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                character_id INTEGER NOT NULL,
                name TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chat_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createVersion6Schema(db: SupportSQLiteDatabase) {
        createVersion2Schema(db)
        db.execSQL("ALTER TABLE characters ADD COLUMN avatar_path TEXT")
        db.execSQL("ALTER TABLE characters ADD COLUMN system_prompt TEXT")
        db.execSQL("ALTER TABLE characters ADD COLUMN post_history_instructions TEXT")
        db.execSQL("ALTER TABLE characters ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE characters ADD COLUMN world_book_id INTEGER")
        db.execSQL("ALTER TABLE characters ADD COLUMN background_path TEXT")
        db.execSQL("ALTER TABLE characters ADD COLUMN creator TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE characters ADD COLUMN version TEXT NOT NULL DEFAULT '1.0'")
        db.execSQL("ALTER TABLE characters ADD COLUMN spec TEXT NOT NULL DEFAULT 'chara_card_v2'")
        db.execSQL("ALTER TABLE chats ADD COLUMN background_path TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN parent_id INTEGER")
        db.execSQL("ALTER TABLE messages ADD COLUMN branch_id INTEGER")
        db.execSQL("ALTER TABLE messages ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE messages ADD COLUMN swipe_content TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE messages ADD COLUMN swipe_index INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                character_id INTEGER NOT NULL,
                content TEXT NOT NULL,
                importance INTEGER NOT NULL DEFAULT 5,
                source TEXT NOT NULL DEFAULT 'manual',
                created_at INTEGER NOT NULL,
                last_accessed INTEGER NOT NULL,
                access_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE scripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                character_id INTEGER NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                comment TEXT NOT NULL DEFAULT '',
                script_type INTEGER NOT NULL DEFAULT 0,
                find_pattern TEXT NOT NULL DEFAULT '',
                replace_pattern TEXT NOT NULL DEFAULT '',
                is_regex INTEGER NOT NULL DEFAULT 1,
                case_sensitive INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    private fun insertVersion2ChatData(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO characters (
                id, name, description, personality, first_mes, mes_example, created_at, updated_at
            ) VALUES (
                1, 'Alice', 'A test character', 'kind', 'Hi', '', 100, 100
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO chats (id, character_id, name, created_at, updated_at)
            VALUES (5, 1, 'Main chat', 110, 120)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO messages (id, chat_id, role, content, created_at)
            VALUES (100, 5, 'user', 'Hello from v2', 130)
            """.trimIndent()
        )
    }

    private fun insertVersion6ChatData(db: SupportSQLiteDatabase) {
        insertVersion2ChatData(db)
        db.execSQL(
            """
            UPDATE messages
            SET swipe_content = '["Alt one","Alt two"]', swipe_index = 1
            WHERE id = 100
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memories (
                id, character_id, content, importance, source, created_at, last_accessed, access_count
            ) VALUES (
                20, 1, 'Keeps a silver key', 9, 'manual', 140, 150, 2
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO scripts (
                id, character_id, name, comment, script_type, find_pattern, replace_pattern,
                is_regex, case_sensitive, enabled, sort_order
            ) VALUES (
                30, 1, 'Alias', '', 0, '{{user}}', 'Traveler', 0, 0, 1, 0
            )
            """.trimIndent()
        )
    }

    private fun withManualDatabase(
        version: Int,
        createSchema: (SupportSQLiteDatabase) -> Unit,
        block: (SupportSQLiteDatabase) -> Unit
    ) {
        val helper = createManualDatabase(version, createSchema)
        val db = helper.writableDatabase
        try {
            block(db)
        } finally {
            db.close()
            helper.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DB_NAME)
        }
    }

    private fun createManualDatabase(
        version: Int,
        createSchema: (SupportSQLiteDatabase) -> Unit
    ): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB_NAME)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createSchema(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun getTableColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
    }

    private fun queryString(db: SupportSQLiteDatabase, sql: String): String {
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private fun queryInt(db: SupportSQLiteDatabase, sql: String): Int {
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "tavern_early_migration_test.db"
    }
}
