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

    @Test
    fun `migration 33 to 34 drops redundant single-column indices and keeps composites`() {
        withRedundantIndexDatabase { db ->
            TavernDatabase.MIGRATION_33_34.migrate(db)

            // 冗余单列索引应删除
            assertIndexNotExists(db, "messages", "index_messages_chat_id")
            assertIndexNotExists(db, "messages", "index_messages_parent_id")
            assertIndexNotExists(db, "messages", "index_messages_character_id")
            assertIndexNotExists(db, "sprites", "index_sprites_character_id")

            // 复合索引 + branch_id 单列索引应保留
            assertIndexExists(db, "messages", "index_messages_branch_id")
            assertIndexExists(db, "messages", "index_messages_chat_active_created")
            assertIndexExists(db, "messages", "index_messages_chat_active_pinned")
            assertIndexExists(db, "sprites", "index_sprites_character_id_emotion")
        }
    }

    private fun withRedundantIndexDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB_NAME)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(33) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chat_id INTEGER NOT NULL, parent_id INTEGER, character_id INTEGER, branch_id INTEGER, is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, is_pinned INTEGER NOT NULL DEFAULT 0)")
                    db.execSQL("CREATE INDEX index_messages_chat_id ON messages(chat_id)")
                    db.execSQL("CREATE INDEX index_messages_parent_id ON messages(parent_id)")
                    db.execSQL("CREATE INDEX index_messages_character_id ON messages(character_id)")
                    db.execSQL("CREATE INDEX index_messages_branch_id ON messages(branch_id)")
                    db.execSQL("CREATE INDEX index_messages_chat_active_created ON messages(chat_id, is_active, created_at)")
                    db.execSQL("CREATE INDEX index_messages_chat_active_pinned ON messages(chat_id, is_active, is_pinned)")
                    db.execSQL("CREATE TABLE sprites (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, character_id INTEGER NOT NULL, emotion TEXT NOT NULL DEFAULT 'neutral', display_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX index_sprites_character_id ON sprites(character_id)")
                    db.execSQL("CREATE INDEX index_sprites_character_id_emotion ON sprites(character_id, emotion)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        try {
            block(db)
        } finally {
            db.close()
            helper.close()
            context.deleteDatabase(TEST_DB_NAME)
        }
    }

    private fun assertIndexNotExists(db: SupportSQLiteDatabase, table: String, indexName: String) {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == indexName) {
                    throw AssertionError("Index $indexName on table $table should have been dropped")
                }
            }
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
