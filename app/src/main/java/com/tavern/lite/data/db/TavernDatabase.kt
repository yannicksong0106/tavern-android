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
import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
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
import com.tavern.lite.data.db.entity.ScriptEntity
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
    ],
    version = 23,
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
            }
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
