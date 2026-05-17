package com.tavern.lite.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.dao.PersonaDao
import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.CharacterPersonaEntity
import com.tavern.lite.data.db.entity.ChatEntity
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
        ScriptEntity::class,
        AuthorNoteEntity::class,
        PersonaEntity::class,
        CharacterPersonaEntity::class,
    ],
    version = 8,
    exportSchema = false
)
abstract class TavernDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun memoryDao(): MemoryDao
    abstract fun scriptDao(): ScriptDao
    abstract fun authorNoteDao(): AuthorNoteDao
    abstract fun personaDao(): PersonaDao
}
