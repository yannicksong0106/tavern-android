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
class QuickReplyMigrationTest {

    @Test
    fun `migration 30 to 31 creates quick reply tables and indices`() {
        withSchemaDatabase(version = 30) { db ->
            TavernDatabase.MIGRATION_30_31.migrate(db)

            assertTableExists(db, "quick_reply_sets")
            assertTableExists(db, "quick_replies")
            assertIndexExists(
                db,
                "quick_reply_sets",
                "index_quick_reply_sets_scope_character_id_chat_id_enabled_display_order"
            )
            assertIndexExists(db, "quick_replies", "index_quick_replies_set_id")
            assertIndexExists(db, "quick_replies", "index_quick_replies_set_id_enabled_display_order")
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
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, tableName: String) {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
            if (cursor.moveToFirst()) return
        }
        throw AssertionError("Expected table $tableName")
    }

    private fun assertIndexExists(db: SupportSQLiteDatabase, table: String, indexName: String) {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == indexName) return
            }
        }
        throw AssertionError("Expected index $indexName on table $table")
    }

    private companion object {
        const val TEST_DB_NAME = "quick_reply_migration_test.db"
    }
}
