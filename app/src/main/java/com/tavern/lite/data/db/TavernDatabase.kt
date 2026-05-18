package com.tavern.lite.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.dao.PersonaDao
import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.CharacterPersonaEntity
import com.tavern.lite.data.db.entity.ChatEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.PersonaEntity
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
    ],
    version = 9,
    exportSchema = false
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

    companion object {
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
