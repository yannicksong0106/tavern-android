package com.tavern.lite.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.BranchDao
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatCharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.dao.PersonaDao
import com.tavern.lite.data.db.dao.PresetDao
import com.tavern.lite.data.db.dao.QuickReplyDao
import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.dao.BgmDao
import com.tavern.lite.data.db.dao.SpriteDao
import com.tavern.lite.data.db.dao.SummaryDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.BgmEntity
import com.tavern.lite.data.db.entity.BranchEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.CharacterPersonaEntity
import com.tavern.lite.data.db.entity.ChatCharacterEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.db.entity.QuickReplyEntity
import com.tavern.lite.data.db.entity.QuickReplySetEntity
import com.tavern.lite.data.db.entity.ScriptEntity
import com.tavern.lite.data.db.entity.SpriteEntity
import com.tavern.lite.data.db.entity.SummaryEntity
import com.tavern.lite.data.db.entity.WorldBookEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity

@Database(
    entities = [
        CharacterEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        WorldBookEntity::class,
        WorldBookEntryEntity::class,
        MemoryEntity::class,
        MemoryAtomEntity::class,
        ScriptEntity::class,
        AuthorNoteEntity::class,
        PersonaEntity::class,
        CharacterPersonaEntity::class,
        ChatCharacterEntity::class,
        PresetEntity::class,
        BranchEntity::class,
        SummaryEntity::class,
        SpriteEntity::class,
        BgmEntity::class,
        QuickReplySetEntity::class,
        QuickReplyEntity::class,
    ],
    version = 31,
    exportSchema = true
)
abstract class TavernDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryAtomDao(): MemoryAtomDao
    abstract fun scriptDao(): ScriptDao
    abstract fun authorNoteDao(): AuthorNoteDao
    abstract fun personaDao(): PersonaDao
    abstract fun chatCharacterDao(): ChatCharacterDao
    abstract fun presetDao(): PresetDao
    abstract fun branchDao(): BranchDao
    abstract fun summaryDao(): SummaryDao
    abstract fun spriteDao(): SpriteDao
    abstract fun bgmDao(): BgmDao
    abstract fun quickReplyDao(): QuickReplyDao

    companion object {
        /**
         * 迁移 1→8：重建 version 8 的完整 schema。
         * 适用于 v1.0.0-beta1 到 v1.0.1-debug 期间的早期用户。
         * 使用 IF NOT EXISTS 确保安全。
         */
        val MIGRATION_1_8 = object : Migration(1, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS characters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        personality TEXT NOT NULL DEFAULT '',
                        first_mes TEXT NOT NULL DEFAULT '',
                        mes_example TEXT NOT NULL DEFAULT '',
                        avatar_path TEXT,
                        system_prompt TEXT,
                        post_history_instructions TEXT,
                        tags TEXT NOT NULL DEFAULT '[]',
                        world_book_id INTEGER,
                        background_path TEXT,
                        creator TEXT NOT NULL DEFAULT '',
                        version TEXT NOT NULL DEFAULT '1.0',
                        spec TEXT NOT NULL DEFAULT 'chara_card_v2',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        name TEXT,
                        background_path TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                normalizeVersion8Columns(db)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_character_id ON chats(character_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        parent_id INTEGER,
                        branch_id INTEGER,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        swipe_content TEXT NOT NULL DEFAULT '[]',
                        swipe_index INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_id ON messages(chat_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_parent_id ON messages(parent_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS world_books (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS world_book_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        world_book_id INTEGER NOT NULL,
                        uid INTEGER NOT NULL DEFAULT 0,
                        comment TEXT NOT NULL DEFAULT '',
                        keys TEXT NOT NULL DEFAULT '[]',
                        keys_secondary TEXT NOT NULL DEFAULT '[]',
                        content TEXT NOT NULL DEFAULT '',
                        constant INTEGER NOT NULL DEFAULT 0,
                        position INTEGER NOT NULL DEFAULT 0,
                        order_val INTEGER NOT NULL DEFAULT 100,
                        probability INTEGER NOT NULL DEFAULT 100,
                        depth INTEGER NOT NULL DEFAULT 4,
                        disabled INTEGER NOT NULL DEFAULT 0,
                        selective INTEGER NOT NULL DEFAULT 0,
                        selective_logic INTEGER NOT NULL DEFAULT 0,
                        exclude_recursion INTEGER NOT NULL DEFAULT 0,
                        prevent_recursion INTEGER NOT NULL DEFAULT 0,
                        "group" TEXT NOT NULL DEFAULT '',
                        group_override INTEGER NOT NULL DEFAULT 0,
                        group_weight INTEGER NOT NULL DEFAULT 100,
                        FOREIGN KEY (world_book_id) REFERENCES world_books(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_world_book_entries_world_book_id ON world_book_entries(world_book_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        importance INTEGER NOT NULL DEFAULT 5,
                        source TEXT NOT NULL DEFAULT 'manual',
                        created_at INTEGER NOT NULL,
                        last_accessed INTEGER NOT NULL,
                        access_count INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_character_id ON memories(character_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scripts (
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
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scripts_character_id ON scripts(character_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS author_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        position TEXT NOT NULL DEFAULT 'after_an',
                        depth INTEGER NOT NULL DEFAULT 4,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_author_notes_character_id ON author_notes(character_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS personas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        biography TEXT NOT NULL DEFAULT '',
                        avatar_path TEXT,
                        is_default INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS character_personas (
                        character_id INTEGER NOT NULL,
                        persona_id INTEGER NOT NULL,
                        PRIMARY KEY(character_id, persona_id),
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE,
                        FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_character_personas_character_id ON character_personas(character_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_character_personas_persona_id ON character_personas(persona_id)")
                normalizeVersion8Columns(db)
            }
        }

        val MIGRATION_2_8 = legacyVersionTo8Migration(2)
        val MIGRATION_3_8 = legacyVersionTo8Migration(3)
        val MIGRATION_4_8 = legacyVersionTo8Migration(4)
        val MIGRATION_5_8 = legacyVersionTo8Migration(5)
        val MIGRATION_6_8 = legacyVersionTo8Migration(6)
        val MIGRATION_7_8 = legacyVersionTo8Migration(7)

        private fun legacyVersionTo8Migration(startVersion: Int): Migration {
            return object : Migration(startVersion, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    MIGRATION_1_8.migrate(db)
                }
            }
        }

        private fun normalizeVersion8Columns(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "characters", "description", "description TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "characters", "personality", "personality TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "characters", "first_mes", "first_mes TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "characters", "mes_example", "mes_example TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "characters", "avatar_path", "avatar_path TEXT")
            addColumnIfMissing(db, "characters", "system_prompt", "system_prompt TEXT")
            addColumnIfMissing(db, "characters", "post_history_instructions", "post_history_instructions TEXT")
            addColumnIfMissing(db, "characters", "tags", "tags TEXT NOT NULL DEFAULT '[]'")
            addColumnIfMissing(db, "characters", "world_book_id", "world_book_id INTEGER")
            addColumnIfMissing(db, "characters", "background_path", "background_path TEXT")
            addColumnIfMissing(db, "characters", "creator", "creator TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "characters", "version", "version TEXT NOT NULL DEFAULT '1.0'")
            addColumnIfMissing(db, "characters", "spec", "spec TEXT NOT NULL DEFAULT 'chara_card_v2'")
            addColumnIfMissing(db, "characters", "created_at", "created_at INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "characters", "updated_at", "updated_at INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "chats", "character_id", "character_id INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "chats", "name", "name TEXT")
            addColumnIfMissing(db, "chats", "background_path", "background_path TEXT")
            addColumnIfMissing(db, "chats", "created_at", "created_at INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "chats", "updated_at", "updated_at INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "messages", "chat_id", "chat_id INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "messages", "role", "role TEXT NOT NULL DEFAULT 'user'")
            addColumnIfMissing(db, "messages", "content", "content TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "messages", "parent_id", "parent_id INTEGER")
            addColumnIfMissing(db, "messages", "branch_id", "branch_id INTEGER")
            addColumnIfMissing(db, "messages", "is_active", "is_active INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "messages", "created_at", "created_at INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "messages", "swipe_content", "swipe_content TEXT NOT NULL DEFAULT '[]'")
            addColumnIfMissing(db, "messages", "swipe_index", "swipe_index INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "world_books", "name", "name TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "world_books", "description", "description TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "world_books", "created_at", "created_at INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_books", "updated_at", "updated_at INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "world_book_entries", "world_book_id", "world_book_id INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "uid", "uid INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "comment", "comment TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "world_book_entries", "keys", "keys TEXT NOT NULL DEFAULT '[]'")
            addColumnIfMissing(db, "world_book_entries", "keys_secondary", "keys_secondary TEXT NOT NULL DEFAULT '[]'")
            addColumnIfMissing(db, "world_book_entries", "content", "content TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "world_book_entries", "constant", "constant INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "position", "position INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "order_val", "order_val INTEGER NOT NULL DEFAULT 100")
            addColumnIfMissing(db, "world_book_entries", "probability", "probability INTEGER NOT NULL DEFAULT 100")
            addColumnIfMissing(db, "world_book_entries", "depth", "depth INTEGER NOT NULL DEFAULT 4")
            addColumnIfMissing(db, "world_book_entries", "disabled", "disabled INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "selective", "selective INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "selective_logic", "selective_logic INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "exclude_recursion", "exclude_recursion INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "prevent_recursion", "prevent_recursion INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "group", "\"group\" TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "world_book_entries", "group_override", "group_override INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "world_book_entries", "group_weight", "group_weight INTEGER NOT NULL DEFAULT 100")

            addColumnIfMissing(db, "memories", "character_id", "character_id INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "memories", "content", "content TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "memories", "importance", "importance INTEGER NOT NULL DEFAULT 5")
            addColumnIfMissing(db, "memories", "source", "source TEXT NOT NULL DEFAULT 'manual'")
            addColumnIfMissing(db, "memories", "created_at", "created_at INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "memories", "last_accessed", "last_accessed INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "memories", "access_count", "access_count INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "scripts", "character_id", "character_id INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "scripts", "name", "name TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "scripts", "comment", "comment TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "scripts", "script_type", "script_type INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "scripts", "find_pattern", "find_pattern TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "scripts", "replace_pattern", "replace_pattern TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "scripts", "is_regex", "is_regex INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "scripts", "case_sensitive", "case_sensitive INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "scripts", "enabled", "enabled INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "scripts", "sort_order", "sort_order INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "author_notes", "character_id", "character_id INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "author_notes", "content", "content TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "author_notes", "position", "position TEXT NOT NULL DEFAULT 'after_an'")
            addColumnIfMissing(db, "author_notes", "depth", "depth INTEGER NOT NULL DEFAULT 4")
            addColumnIfMissing(db, "author_notes", "updated_at", "updated_at INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "personas", "name", "name TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "personas", "biography", "biography TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "personas", "avatar_path", "avatar_path TEXT")
            addColumnIfMissing(db, "personas", "is_default", "is_default INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "personas", "created_at", "created_at INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "character_personas", "character_id", "character_id INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "character_personas", "persona_id", "persona_id INTEGER NOT NULL DEFAULT 0")
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            columnDefinition: String
        ) {
            if (!tableExists(db, table) || columnExists(db, table, column)) return
            db.execSQL("ALTER TABLE $table ADD COLUMN $columnDefinition")
        }

        private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table)
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加健谈度字段
                db.execSQL("ALTER TABLE characters ADD COLUMN chattiness INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE chat_characters ADD COLUMN chattiness INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE chats ADD COLUMN group_chattiness INTEGER NOT NULL DEFAULT 50")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        system_prompt TEXT NOT NULL DEFAULT '',
                        post_history_instructions TEXT NOT NULL DEFAULT '',
                        author_note TEXT NOT NULL DEFAULT '',
                        is_default INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add is_group column to chats table
                db.execSQL("ALTER TABLE chats ADD COLUMN is_group INTEGER NOT NULL DEFAULT 0")
                // Add character_id column to messages table
                db.execSQL("ALTER TABLE messages ADD COLUMN character_id INTEGER")
                // Create index on messages.character_id
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_character_id ON messages(character_id)")
                // Create chat_characters junction table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_characters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        character_id INTEGER NOT NULL,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_characters_chat_id ON chat_characters(chat_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_characters_character_id ON chat_characters(character_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_characters_chat_id_character_id ON chat_characters(chat_id, character_id)")
                // Backfill: for existing chats, add the character_id to chat_characters
                db.execSQL("""
                    INSERT INTO chat_characters (chat_id, character_id, display_order, is_active, created_at)
                    SELECT id, character_id, 0, 1, created_at FROM chats
                """.trimIndent())
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_id INTEGER")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_active_created ON messages(chat_id, is_active, created_at)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remap legacy categories to new ones
                db.execSQL("UPDATE memory_atoms SET category = 'fact' WHERE category = 'user_info'")
                db.execSQL("UPDATE memory_atoms SET category = 'fact' WHERE category = 'relationship'")
                db.execSQL("UPDATE memory_atoms SET category = 'event' WHERE category = 'commitment'")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加复合索引，优化记忆库查询
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_superseded ON memory_atoms(character_id, superseded)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_superseded_category ON memory_atoms(character_id, superseded, category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_superseded_source ON memory_atoms(character_id, superseded, source)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加 pinned messages 查询索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_active_pinned ON messages(chat_id, is_active, is_pinned)")
                // 添加群聊查询索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_is_group ON chats(is_group)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // chats.updated_at 索引：getRecentChats ORDER BY updated_at DESC
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_updated_at ON chats(updated_at)")
                // memory_atoms 排序索引：getTopAtoms ORDER BY importance DESC, last_accessed DESC
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_sort ON memory_atoms(character_id, superseded, importance DESC, last_accessed DESC)")
                // world_book_entries 复合索引：getActiveEntries WHERE disabled = 0
                db.execSQL("CREATE INDEX IF NOT EXISTS index_world_book_entries_active ON world_book_entries(world_book_id, disabled)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // memory_atoms 分类+优先级复合索引：getAtomsByCategory / getTopAtoms / getCharacterConsistencyAtoms
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_category_importance ON memory_atoms(character_id, superseded, category, importance DESC)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // branches 表：分支元数据
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS branches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        is_default INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_branches_chat_id ON branches(chat_id)")

                // presets 增加 scope 字段
                db.execSQL("ALTER TABLE presets ADD COLUMN scope TEXT NOT NULL DEFAULT 'global'")

                // characters 增加 preset_id 字段
                db.execSQL("ALTER TABLE characters ADD COLUMN preset_id INTEGER")

                // chats 增加 preset_id 字段
                db.execSQL("ALTER TABLE chats ADD COLUMN preset_id INTEGER")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // branch_id 索引：分支切换查询优化
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_branch_id ON messages(branch_id)")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // messages 增加 image_paths 字段（图片附件）
                db.execSQL("ALTER TABLE messages ADD COLUMN image_paths TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS summaries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        message_range_start INTEGER NOT NULL,
                        message_range_end INTEGER NOT NULL,
                        token_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_summaries_chat_id ON summaries(chat_id)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN scheduling_strategy TEXT NOT NULL DEFAULT 'natural'")
                db.execSQL("ALTER TABLE chats ADD COLUMN message_interval_ms INTEGER NOT NULL DEFAULT 1500")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sprites (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        emotion TEXT NOT NULL DEFAULT 'neutral',
                        image_path TEXT NOT NULL,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sprites_character_id ON sprites(character_id)")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bgms (
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
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bgms_character_id ON bgms(character_id)")
            }
        }

        /**
         * 迁移 27→28：为所有表添加 @ColumnInfo(defaultValue) 对应的 SQL DEFAULT 约束。
         * 解决 Room schema validation 失败问题：entity 声明了 defaultValue 但实际 DB 缺少 DEFAULT。
         * 通过 重建表 → 复制数据 → 重命名 的方式安全迁移。
         */
        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. characters — 添加 description/personality/first_mes/mes_example/tags/creator/version/spec/chattiness DEFAULT
                db.execSQL("ALTER TABLE characters RENAME TO _characters_old")
                db.execSQL("""
                    CREATE TABLE characters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        personality TEXT NOT NULL DEFAULT '',
                        first_mes TEXT NOT NULL DEFAULT '',
                        mes_example TEXT NOT NULL DEFAULT '',
                        avatar_path TEXT,
                        system_prompt TEXT,
                        post_history_instructions TEXT,
                        tags TEXT NOT NULL DEFAULT '[]',
                        world_book_id INTEGER,
                        preset_id INTEGER,
                        background_path TEXT,
                        chattiness INTEGER NOT NULL DEFAULT 50,
                        creator TEXT NOT NULL DEFAULT '',
                        version TEXT NOT NULL DEFAULT '1.0',
                        spec TEXT NOT NULL DEFAULT 'chara_card_v2',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO characters (
                        id,
                        name,
                        description,
                        personality,
                        first_mes,
                        mes_example,
                        avatar_path,
                        system_prompt,
                        post_history_instructions,
                        tags,
                        world_book_id,
                        preset_id,
                        background_path,
                        chattiness,
                        creator,
                        version,
                        spec,
                        created_at,
                        updated_at
                    )
                    SELECT
                        id,
                        name,
                        description,
                        personality,
                        first_mes,
                        mes_example,
                        avatar_path,
                        system_prompt,
                        post_history_instructions,
                        tags,
                        world_book_id,
                        preset_id,
                        background_path,
                        chattiness,
                        creator,
                        version,
                        spec,
                        created_at,
                        updated_at
                    FROM _characters_old
                """.trimIndent())
                db.execSQL("DROP TABLE _characters_old")

                // 2. chats — 添加 is_group/group_chattiness/scheduling_strategy/message_interval_ms DEFAULT
                db.execSQL("ALTER TABLE chats RENAME TO _chats_old")
                db.execSQL("""
                    CREATE TABLE chats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        name TEXT,
                        is_group INTEGER NOT NULL DEFAULT 0,
                        group_chattiness INTEGER NOT NULL DEFAULT 50,
                        background_path TEXT,
                        preset_id INTEGER,
                        scheduling_strategy TEXT NOT NULL DEFAULT 'natural',
                        message_interval_ms INTEGER NOT NULL DEFAULT 1500,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO chats (
                        id,
                        character_id,
                        name,
                        is_group,
                        group_chattiness,
                        background_path,
                        preset_id,
                        scheduling_strategy,
                        message_interval_ms,
                        created_at,
                        updated_at
                    )
                    SELECT
                        id,
                        character_id,
                        name,
                        is_group,
                        group_chattiness,
                        background_path,
                        preset_id,
                        scheduling_strategy,
                        message_interval_ms,
                        created_at,
                        updated_at
                    FROM _chats_old
                """.trimIndent())
                db.execSQL("DROP TABLE _chats_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_character_id ON chats(character_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_is_group ON chats(is_group)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_updated_at ON chats(updated_at)")

                // 3. messages — 添加 is_active DEFAULT
                db.execSQL("ALTER TABLE messages RENAME TO _messages_old")
                db.execSQL("""
                    CREATE TABLE messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        character_id INTEGER,
                        parent_id INTEGER,
                        branch_id INTEGER,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        swipe_content TEXT NOT NULL DEFAULT '[]',
                        swipe_index INTEGER NOT NULL DEFAULT 0,
                        reply_to_id INTEGER,
                        is_pinned INTEGER NOT NULL DEFAULT 0,
                        image_paths TEXT NOT NULL DEFAULT '[]',
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO messages (
                        id,
                        chat_id,
                        role,
                        content,
                        character_id,
                        parent_id,
                        branch_id,
                        is_active,
                        created_at,
                        swipe_content,
                        swipe_index,
                        reply_to_id,
                        is_pinned,
                        image_paths
                    )
                    SELECT
                        id,
                        chat_id,
                        role,
                        content,
                        character_id,
                        parent_id,
                        branch_id,
                        is_active,
                        created_at,
                        swipe_content,
                        swipe_index,
                        reply_to_id,
                        is_pinned,
                        image_paths
                    FROM _messages_old
                """.trimIndent())
                db.execSQL("DROP TABLE _messages_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_id ON messages(chat_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_parent_id ON messages(parent_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_character_id ON messages(character_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_branch_id ON messages(branch_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_active_created ON messages(chat_id, is_active, created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chat_active_pinned ON messages(chat_id, is_active, is_pinned)")

                // 4. memory_atoms — 添加 importance/source/superseded/access_count DEFAULT
                db.execSQL("ALTER TABLE memory_atoms RENAME TO _memory_atoms_old")
                db.execSQL("""
                    CREATE TABLE memory_atoms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL,
                        importance INTEGER NOT NULL DEFAULT 5,
                        source TEXT NOT NULL DEFAULT 'llm',
                        source_chat_id INTEGER,
                        source_message_id INTEGER,
                        superseded INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        last_accessed INTEGER NOT NULL,
                        access_count INTEGER NOT NULL DEFAULT 0,
                        expires_at INTEGER,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO memory_atoms (
                        id,
                        character_id,
                        content,
                        category,
                        importance,
                        source,
                        source_chat_id,
                        source_message_id,
                        superseded,
                        created_at,
                        last_accessed,
                        access_count,
                        expires_at
                    )
                    SELECT
                        id,
                        character_id,
                        content,
                        category,
                        importance,
                        source,
                        source_chat_id,
                        source_message_id,
                        superseded,
                        created_at,
                        last_accessed,
                        access_count,
                        expires_at
                    FROM _memory_atoms_old
                """.trimIndent())
                db.execSQL("DROP TABLE _memory_atoms_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id ON memory_atoms(character_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_category ON memory_atoms(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_category ON memory_atoms(character_id, category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_superseded ON memory_atoms(character_id, superseded)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_superseded_category ON memory_atoms(character_id, superseded, category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_superseded_source ON memory_atoms(character_id, superseded, source)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_superseded_category_importance ON memory_atoms(character_id, superseded, category, importance DESC)")

                // 5. memories — 添加 importance/source/access_count DEFAULT
                db.execSQL("ALTER TABLE memories RENAME TO _memories_old")
                db.execSQL("""
                    CREATE TABLE memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        importance INTEGER NOT NULL DEFAULT 5,
                        source TEXT NOT NULL DEFAULT 'manual',
                        created_at INTEGER NOT NULL,
                        last_accessed INTEGER NOT NULL,
                        access_count INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO memories (
                        id,
                        character_id,
                        content,
                        importance,
                        source,
                        created_at,
                        last_accessed,
                        access_count
                    )
                    SELECT
                        id,
                        character_id,
                        content,
                        importance,
                        source,
                        created_at,
                        last_accessed,
                        access_count
                    FROM _memories_old
                """.trimIndent())
                db.execSQL("DROP TABLE _memories_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_character_id ON memories(character_id)")

                // 6. scripts — 添加全部 DEFAULT
                db.execSQL("ALTER TABLE scripts RENAME TO _scripts_old")
                db.execSQL("""
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
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO scripts (
                        id,
                        character_id,
                        name,
                        comment,
                        script_type,
                        find_pattern,
                        replace_pattern,
                        is_regex,
                        case_sensitive,
                        enabled,
                        sort_order
                    )
                    SELECT
                        id,
                        character_id,
                        name,
                        comment,
                        script_type,
                        find_pattern,
                        replace_pattern,
                        is_regex,
                        case_sensitive,
                        enabled,
                        sort_order
                    FROM _scripts_old
                """.trimIndent())
                db.execSQL("DROP TABLE _scripts_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scripts_character_id ON scripts(character_id)")

                // 7. world_books — 添加 description DEFAULT
                db.execSQL("ALTER TABLE world_books RENAME TO _world_books_old")
                db.execSQL("""
                    CREATE TABLE world_books (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO world_books (
                        id,
                        name,
                        description,
                        created_at,
                        updated_at
                    )
                    SELECT
                        id,
                        name,
                        description,
                        created_at,
                        updated_at
                    FROM _world_books_old
                """.trimIndent())
                db.execSQL("DROP TABLE _world_books_old")

                // 8. world_book_entries — 添加全部 DEFAULT + active index
                db.execSQL("ALTER TABLE world_book_entries RENAME TO _world_book_entries_old")
                db.execSQL("""
                    CREATE TABLE world_book_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        world_book_id INTEGER NOT NULL,
                        uid INTEGER NOT NULL DEFAULT 0,
                        comment TEXT NOT NULL DEFAULT '',
                        keys TEXT NOT NULL DEFAULT '[]',
                        keys_secondary TEXT NOT NULL DEFAULT '[]',
                        content TEXT NOT NULL DEFAULT '',
                        constant INTEGER NOT NULL DEFAULT 0,
                        position INTEGER NOT NULL DEFAULT 0,
                        order_val INTEGER NOT NULL DEFAULT 100,
                        probability INTEGER NOT NULL DEFAULT 100,
                        depth INTEGER NOT NULL DEFAULT 4,
                        disabled INTEGER NOT NULL DEFAULT 0,
                        selective INTEGER NOT NULL DEFAULT 0,
                        selective_logic INTEGER NOT NULL DEFAULT 0,
                        exclude_recursion INTEGER NOT NULL DEFAULT 0,
                        prevent_recursion INTEGER NOT NULL DEFAULT 0,
                        "group" TEXT NOT NULL DEFAULT '',
                        group_override INTEGER NOT NULL DEFAULT 0,
                        group_weight INTEGER NOT NULL DEFAULT 100,
                        FOREIGN KEY (world_book_id) REFERENCES world_books(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO world_book_entries (
                        id,
                        world_book_id,
                        uid,
                        comment,
                        keys,
                        keys_secondary,
                        content,
                        constant,
                        position,
                        order_val,
                        probability,
                        depth,
                        disabled,
                        selective,
                        selective_logic,
                        exclude_recursion,
                        prevent_recursion,
                        "group",
                        group_override,
                        group_weight
                    )
                    SELECT
                        id,
                        world_book_id,
                        uid,
                        comment,
                        keys,
                        keys_secondary,
                        content,
                        constant,
                        position,
                        order_val,
                        probability,
                        depth,
                        disabled,
                        selective,
                        selective_logic,
                        exclude_recursion,
                        prevent_recursion,
                        "group",
                        group_override,
                        group_weight
                    FROM _world_book_entries_old
                """.trimIndent())
                db.execSQL("DROP TABLE _world_book_entries_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_world_book_entries_world_book_id ON world_book_entries(world_book_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_world_book_entries_active ON world_book_entries(world_book_id, disabled)")

                // 9. author_notes — 添加 content DEFAULT
                db.execSQL("ALTER TABLE author_notes RENAME TO _author_notes_old")
                db.execSQL("""
                    CREATE TABLE author_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        position TEXT NOT NULL DEFAULT 'after_an',
                        depth INTEGER NOT NULL DEFAULT 4,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO author_notes (
                        id,
                        character_id,
                        content,
                        position,
                        depth,
                        updated_at
                    )
                    SELECT
                        id,
                        character_id,
                        content,
                        position,
                        depth,
                        updated_at
                    FROM _author_notes_old
                """.trimIndent())
                db.execSQL("DROP TABLE _author_notes_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_author_notes_character_id ON author_notes(character_id)")

                // 10. personas — 添加 biography/is_default DEFAULT
                db.execSQL("ALTER TABLE personas RENAME TO _personas_old")
                db.execSQL("""
                    CREATE TABLE personas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        biography TEXT NOT NULL DEFAULT '',
                        avatar_path TEXT,
                        is_default INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO personas (
                        id,
                        name,
                        biography,
                        avatar_path,
                        is_default,
                        created_at
                    )
                    SELECT
                        id,
                        name,
                        biography,
                        avatar_path,
                        is_default,
                        created_at
                    FROM _personas_old
                """.trimIndent())
                db.execSQL("DROP TABLE _personas_old")

                // 11. chat_characters — 添加 display_order/is_active/chattiness DEFAULT
                db.execSQL("ALTER TABLE chat_characters RENAME TO _chat_characters_old")
                db.execSQL("""
                    CREATE TABLE chat_characters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        character_id INTEGER NOT NULL,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        chattiness INTEGER NOT NULL DEFAULT 50,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO chat_characters (
                        id,
                        chat_id,
                        character_id,
                        display_order,
                        is_active,
                        chattiness,
                        created_at
                    )
                    SELECT
                        id,
                        chat_id,
                        character_id,
                        display_order,
                        is_active,
                        chattiness,
                        created_at
                    FROM _chat_characters_old
                """.trimIndent())
                db.execSQL("DROP TABLE _chat_characters_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_characters_chat_id ON chat_characters(chat_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_characters_character_id ON chat_characters(character_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_characters_chat_id_character_id ON chat_characters(chat_id, character_id)")

                // 12. presets — 添加 description/system_prompt/post_history_instructions/author_note/is_default/scope DEFAULT
                db.execSQL("ALTER TABLE presets RENAME TO _presets_old")
                db.execSQL("""
                    CREATE TABLE presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        system_prompt TEXT NOT NULL DEFAULT '',
                        post_history_instructions TEXT NOT NULL DEFAULT '',
                        author_note TEXT NOT NULL DEFAULT '',
                        is_default INTEGER NOT NULL DEFAULT 0,
                        scope TEXT NOT NULL DEFAULT 'global',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO presets (
                        id,
                        name,
                        description,
                        system_prompt,
                        post_history_instructions,
                        author_note,
                        is_default,
                        scope,
                        created_at,
                        updated_at
                    )
                    SELECT
                        id,
                        name,
                        description,
                        system_prompt,
                        post_history_instructions,
                        author_note,
                        is_default,
                        scope,
                        created_at,
                        updated_at
                    FROM _presets_old
                """.trimIndent())
                db.execSQL("DROP TABLE _presets_old")

                // 13. branches — 添加 name/is_default DEFAULT
                db.execSQL("ALTER TABLE branches RENAME TO _branches_old")
                db.execSQL("""
                    CREATE TABLE branches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        is_default INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO branches (
                        id,
                        chat_id,
                        name,
                        is_default,
                        created_at
                    )
                    SELECT
                        id,
                        chat_id,
                        name,
                        is_default,
                        created_at
                    FROM _branches_old
                """.trimIndent())
                db.execSQL("DROP TABLE _branches_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_branches_chat_id ON branches(chat_id)")

                // 14. summaries — 添加 token_count DEFAULT
                db.execSQL("ALTER TABLE summaries RENAME TO _summaries_old")
                db.execSQL("""
                    CREATE TABLE summaries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chat_id INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        message_range_start INTEGER NOT NULL,
                        message_range_end INTEGER NOT NULL,
                        token_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO summaries (
                        id,
                        chat_id,
                        content,
                        message_range_start,
                        message_range_end,
                        token_count,
                        created_at
                    )
                    SELECT
                        id,
                        chat_id,
                        content,
                        message_range_start,
                        message_range_end,
                        token_count,
                        created_at
                    FROM _summaries_old
                """.trimIndent())
                db.execSQL("DROP TABLE _summaries_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_summaries_chat_id ON summaries(chat_id)")

                // 15. sprites — 添加 emotion/display_order DEFAULT
                db.execSQL("ALTER TABLE sprites RENAME TO _sprites_old")
                db.execSQL("""
                    CREATE TABLE sprites (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        emotion TEXT NOT NULL DEFAULT 'neutral',
                        image_path TEXT NOT NULL,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO sprites (
                        id,
                        character_id,
                        emotion,
                        image_path,
                        display_order,
                        created_at
                    )
                    SELECT
                        id,
                        character_id,
                        emotion,
                        image_path,
                        display_order,
                        created_at
                    FROM _sprites_old
                """.trimIndent())
                db.execSQL("DROP TABLE _sprites_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sprites_character_id ON sprites(character_id)")

                // 16. bgms — 添加 name/loop/volume/display_order DEFAULT
                db.execSQL("ALTER TABLE bgms RENAME TO _bgms_old")
                db.execSQL("""
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
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO bgms (
                        id,
                        character_id,
                        name,
                        audio_path,
                        loop,
                        volume,
                        display_order,
                        created_at
                    )
                    SELECT
                        id,
                        character_id,
                        name,
                        audio_path,
                        loop,
                        volume,
                        display_order,
                        created_at
                    FROM _bgms_old
                """.trimIndent())
                db.execSQL("DROP TABLE _bgms_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bgms_character_id ON bgms(character_id)")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // bgms 添加 emotion 列：支持按情感选择 BGM
                db.execSQL("ALTER TABLE bgms ADD COLUMN emotion TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Phase 6.1: add composite indices for high-frequency filtered + ordered queries.
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_character_id_updated_at ON chats(character_id, updated_at DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_is_group_updated_at ON chats(is_group, updated_at DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_characters_chat_id_is_active_display_order ON chat_characters(chat_id, is_active, display_order)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bgms_character_id_display_order_created_at ON bgms(character_id, display_order, created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bgms_character_id_emotion_display_order ON bgms(character_id, emotion, display_order)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sprites_character_id_display_order_created_at ON sprites(character_id, display_order, created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sprites_character_id_emotion ON sprites(character_id, emotion)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_summaries_chat_id_created_at ON summaries(chat_id, created_at DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_branches_chat_id_is_default_created_at ON branches(chat_id, is_default DESC, created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scripts_character_id_enabled_sort_order_id ON scripts(character_id, enabled, sort_order, id)")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS quick_reply_sets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        scope TEXT NOT NULL DEFAULT 'global',
                        character_id INTEGER,
                        chat_id INTEGER,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS quick_replies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        set_id INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        script TEXT NOT NULL,
                        icon TEXT,
                        automation_id TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        requires_confirmation INTEGER NOT NULL DEFAULT 0,
                        allow_auto_run INTEGER NOT NULL DEFAULT 0,
                        can_send_messages INTEGER NOT NULL DEFAULT 0,
                        can_trigger_generation INTEGER NOT NULL DEFAULT 0,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (set_id) REFERENCES quick_reply_sets(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_reply_sets_scope_character_id_chat_id_enabled_display_order ON quick_reply_sets(scope, character_id, chat_id, enabled, display_order)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_replies_set_id ON quick_replies(set_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_replies_set_id_enabled_display_order ON quick_replies(set_id, enabled, display_order)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_atoms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        character_id INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL,
                        importance INTEGER NOT NULL DEFAULT 5,
                        source TEXT NOT NULL DEFAULT 'llm',
                        source_chat_id INTEGER,
                        source_message_id INTEGER,
                        superseded INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        last_accessed INTEGER NOT NULL,
                        access_count INTEGER NOT NULL DEFAULT 0,
                        expires_at INTEGER,
                        FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id ON memory_atoms(character_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_category ON memory_atoms(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_atoms_character_id_category ON memory_atoms(character_id, category)")
            }
        }
    }
}
