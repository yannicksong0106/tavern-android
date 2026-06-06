package com.tavern.lite.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TavernDatabaseIndexMigrationTest {

    @Test
    fun `migration 29 to 30 adds performance indices`() {
        withSchemaDatabase(version = 29) { db ->
            TavernDatabase.MIGRATION_29_30.migrate(db)

            assertIndexExists(db, "chats", "index_chats_character_id_updated_at")
            assertIndexExists(db, "chats", "index_chats_is_group_updated_at")
            assertIndexExists(db, "chat_characters", "index_chat_characters_chat_id_is_active_display_order")
            assertIndexExists(db, "bgms", "index_bgms_character_id_emotion_display_order")
            assertIndexExists(db, "sprites", "index_sprites_character_id_emotion")
            assertIndexExists(db, "summaries", "index_summaries_chat_id_created_at")
            assertIndexExists(db, "branches", "index_branches_chat_id_is_default_created_at")
            assertIndexExists(db, "scripts", "index_scripts_character_id_enabled_sort_order_id")
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
                    createVersion29Tables(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun createVersion29Tables(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE chats (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, character_id INTEGER NOT NULL, is_group INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE chat_characters (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chat_id INTEGER NOT NULL, character_id INTEGER NOT NULL, is_active INTEGER NOT NULL DEFAULT 1, display_order INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE bgms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, character_id INTEGER NOT NULL, emotion TEXT NOT NULL DEFAULT '', display_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sprites (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, character_id INTEGER NOT NULL, emotion TEXT NOT NULL DEFAULT 'neutral', display_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE summaries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chat_id INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE branches (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chat_id INTEGER NOT NULL, is_default INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE scripts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, character_id INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, sort_order INTEGER NOT NULL DEFAULT 0)")
    }

    private fun assertIndexExists(db: SupportSQLiteDatabase, table: String, indexName: String) {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == indexName) {
                    return
                }
            }
        }
        throw AssertionError("Expected index $indexName on table $table")
    }

    private companion object {
        const val TEST_DB_NAME = "tavern_index_migration_test.db"
    }
}
