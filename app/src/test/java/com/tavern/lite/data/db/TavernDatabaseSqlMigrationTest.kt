package com.tavern.lite.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TavernDatabaseSqlMigrationTest {

    @Test
    fun `migration 28 to 29 adds bgm emotion column and preserves existing rows`() {
        withSchemaDatabase(version = 28) { db ->
            insertCoreChatData(db, hasImagePaths = true)
            insertBgmWithoutEmotion(db)

            TavernDatabase.MIGRATION_28_29.migrate(db)
            val columns = getTableColumns(db, "bgms")
            assertTrue(columns.contains("emotion"))

            db.query("SELECT emotion FROM bgms WHERE id = 10").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
            }
        }
    }

    @Test
    fun `migration 27 to 29 preserves BGM rows and adds emotion column`() {
        withSchemaDatabase(version = 27) { db ->
            insertCoreChatData(db, hasImagePaths = true)
            insertBgmWithoutEmotion(db)

            TavernDatabase.MIGRATION_27_28.migrate(db)
            TavernDatabase.MIGRATION_28_29.migrate(db)

            val columns = getTableColumns(db, "bgms")
            assertTrue(columns.contains("emotion"))
            db.query(
                """
                SELECT name, audio_path, loop, volume, emotion, display_order
                FROM bgms
                WHERE id = 10
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Default theme", cursor.getString(0))
                assertEquals("/music/default.mp3", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0.7, cursor.getDouble(3), 0.001)
                assertEquals("", cursor.getString(4))
                assertEquals(3, cursor.getInt(5))
            }
        }
    }

    @Test
    fun `migration 21 to 29 preserves chat messages and applies defaults`() {
        withSchemaDatabase(version = 21) { db ->
            insertCoreChatData(db, hasImagePaths = false)

            TavernDatabase.MIGRATION_21_22.migrate(db)
            TavernDatabase.MIGRATION_22_23.migrate(db)
            TavernDatabase.MIGRATION_23_24.migrate(db)
            TavernDatabase.MIGRATION_24_25.migrate(db)
            TavernDatabase.MIGRATION_25_26.migrate(db)
            TavernDatabase.MIGRATION_26_27.migrate(db)
            TavernDatabase.MIGRATION_27_28.migrate(db)
            TavernDatabase.MIGRATION_28_29.migrate(db)

            assertTrue(getTableColumns(db, "messages").contains("image_paths"))
            assertTrue(getTableColumns(db, "bgms").contains("emotion"))
            assertTrue(getTableColumns(db, "summaries").contains("token_count"))
            assertTrue(getTableColumns(db, "sprites").contains("emotion"))

            db.query(
                """
                SELECT content, image_paths, swipe_content, swipe_index, is_pinned
                FROM messages
                WHERE id = 100
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Hello", cursor.getString(0))
                assertEquals("[]", cursor.getString(1))
                assertEquals("""["Hi again"]""", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
            }

            db.query(
                """
                SELECT scheduling_strategy, message_interval_ms, created_at, updated_at
                FROM chats
                WHERE id = 5
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("natural", cursor.getString(0))
                assertEquals(1500L, cursor.getLong(1))
                assertEquals(110L, cursor.getLong(2))
                assertEquals(120L, cursor.getLong(3))
            }
        }
    }

    @Test
    fun `migration 32 to 33 removes legacy reasoning column and preserves messages`() {
        withSchemaDatabase(version = 32) { db ->
            insertCoreChatData(db, hasImagePaths = true)
            db.execSQL("UPDATE messages SET reasoning_content = 'legacy trace', is_pinned = 1 WHERE id = 100")

            TavernDatabase.MIGRATION_32_33.migrate(db)

            val messageColumns = getTableColumns(db, "messages")
            assertFalse(messageColumns.contains("reasoning_content"))
            assertTrue(messageColumns.contains("image_paths"))
            assertTrue(getTableColumns(db, "world_book_entries").contains("automation_id"))

            db.query(
                """
                SELECT content, image_paths, swipe_content, swipe_index, is_pinned
                FROM messages
                WHERE id = 100
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Hello", cursor.getString(0))
                assertEquals("""["/files/a.png"]""", cursor.getString(1))
                assertEquals("""["Hi again"]""", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(1, cursor.getInt(4))
            }

            val profileColumns = getTableColumns(db, "api_config_profiles")
            assertTrue(profileColumns.contains("config_json"))
            assertTrue(profileColumns.contains("bound_chat_id"))
            assertTrue(getIndexNames(db, "api_config_profiles").isEmpty())
        }
    }

    private fun withSchemaDatabase(version: Int, block: (SupportSQLiteDatabase) -> Unit) {
        val helper = createSchemaDatabase(version)
        val db = helper.writableDatabase
        try {
            block(db)
        } finally {
            db.close()
            helper.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DB_NAME)
        }
    }

    private fun createSchemaDatabase(version: Int): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB_NAME)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createSchemaTables(db, version)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun createSchemaTables(db: SupportSQLiteDatabase, version: Int) {
        val schema = JSONObject(schemaFile(version).readText())
        val entities = schema.getJSONObject("database").getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            db.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, entity.getString("tableName")))
        }

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_PLACEHOLDER, tableName))
            }
        }
    }

    private fun insertCoreChatData(db: SupportSQLiteDatabase, hasImagePaths: Boolean) {
        db.execSQL(
            """
            INSERT INTO characters (
                id, name, description, personality, first_mes, mes_example, tags, chattiness,
                creator, version, spec, created_at, updated_at
            ) VALUES (
                1, 'Alice', 'A test character', 'kind', 'Hi', '', '[]', 75,
                'tester', '1.0', 'chara_card_v2', 100, 100
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO chats (
                id, character_id, name, is_group, group_chattiness, created_at, updated_at
            ) VALUES (
                5, 1, 'Main chat', 0, 60, 110, 120
            )
            """.trimIndent()
        )
        val imagePathsColumn = if (hasImagePaths) ", image_paths" else ""
        val imagePathsValue = if (hasImagePaths) """, '["/files/a.png"]'""" else ""
        db.execSQL(
            """
            INSERT INTO messages (
                id, chat_id, role, content, character_id, parent_id, branch_id, is_active,
                created_at, swipe_content, swipe_index, reply_to_id, is_pinned$imagePathsColumn
            ) VALUES (
                100, 5, 'user', 'Hello', NULL, NULL, NULL, 1,
                130, '["Hi again"]', 0, NULL, 0$imagePathsValue
            )
            """.trimIndent()
        )
    }

    private fun insertBgmWithoutEmotion(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO bgms (
                id, character_id, name, audio_path, loop, volume, display_order, created_at
            ) VALUES (
                10, 1, 'Default theme', '/music/default.mp3', 1, 0.7, 3, 200
            )
            """.trimIndent()
        )
    }

    private fun schemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.tavern.lite.data.db.TavernDatabase/$version.json"),
            File("app/schemas/com.tavern.lite.data.db.TavernDatabase/$version.json")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Room schema file for version $version was not found")
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

    private fun getIndexNames(db: SupportSQLiteDatabase, table: String): Set<String> {
        val indices = mutableSetOf<String>()
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                indices += cursor.getString(nameIndex)
            }
        }
        return indices
    }

    private companion object {
        const val TEST_DB_NAME = "tavern_sql_migration_test.db"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
    }
}
