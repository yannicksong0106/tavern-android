package com.tavern.lite.data.db

import android.content.Context
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
class TavernDatabaseSqlMigrationTest {

    @Test
    fun `migration 28 to 29 adds bgm emotion column and preserves existing rows`() {
        val helper = createVersion28Database()
        val db = helper.writableDatabase
        try {
            TavernDatabase.MIGRATION_28_29.migrate(db)

            val columns = getTableColumns(db, "bgms")
            assertTrue(columns.contains("emotion"))

            db.query("SELECT emotion FROM bgms WHERE id = 10").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
            }
        } finally {
            db.close()
            helper.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DB_NAME)
        }
    }

    private fun createVersion28Database(): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB_NAME)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(28) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE characters (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            personality TEXT NOT NULL DEFAULT '',
                            first_mes TEXT NOT NULL DEFAULT '',
                            mes_example TEXT NOT NULL DEFAULT '',
                            tags TEXT NOT NULL DEFAULT '[]',
                            chattiness INTEGER NOT NULL DEFAULT 50,
                            creator TEXT NOT NULL DEFAULT '',
                            version TEXT NOT NULL DEFAULT '1.0',
                            spec TEXT NOT NULL DEFAULT 'chara_card_v2',
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE bgms (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            character_id INTEGER NOT NULL,
                            name TEXT NOT NULL DEFAULT '',
                            audio_path TEXT NOT NULL,
                            loop INTEGER NOT NULL DEFAULT 1,
                            volume REAL NOT NULL DEFAULT 0.5,
                            display_order INTEGER NOT NULL DEFAULT 0,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_bgms_character_id ON bgms(character_id)")
                    db.execSQL(
                        """
                        INSERT INTO characters (
                            id, name, created_at, updated_at
                        ) VALUES (
                            1, 'Alice', 100, 100
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO bgms (
                            id, character_id, name, audio_path, loop, volume, display_order, created_at
                        ) VALUES (
                            10, 1, 'Default theme', '/music/default.mp3', 1, 0.7, 0, 200
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config)
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

    private companion object {
        const val TEST_DB_NAME = "tavern_migration_28_29_test.db"
    }
}
