package com.tavern.lite.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.tavern.lite.data.db.TavernDatabase
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatCharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.PersonaDao
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.dao.PresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TavernDatabase {
        return Room.databaseBuilder(
            context,
            TavernDatabase::class.java,
            "tavern_db"
        ).addMigrations(
            TavernDatabase.MIGRATION_1_8,
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
            TavernDatabase.MIGRATION_19_20
        ).fallbackToDestructiveMigrationFrom(2, 3, 4, 5, 6, 7)
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideCharacterDao(db: TavernDatabase): CharacterDao = db.characterDao()

    @Provides
    fun provideChatDao(db: TavernDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: TavernDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideWorldBookDao(db: TavernDatabase): WorldBookDao = db.worldBookDao()

    @Provides
    fun provideMemoryDao(db: TavernDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideMemoryAtomDao(db: TavernDatabase): MemoryAtomDao = db.memoryAtomDao()

    @Provides
    fun provideChatCharacterDao(db: TavernDatabase): ChatCharacterDao = db.chatCharacterDao()

    @Provides
    fun provideScriptDao(db: TavernDatabase): ScriptDao = db.scriptDao()

    @Provides
    fun provideAuthorNoteDao(db: TavernDatabase): AuthorNoteDao = db.authorNoteDao()

    @Provides
    fun providePersonaDao(db: TavernDatabase): PersonaDao = db.personaDao()

    @Provides
    fun providePresetDao(db: TavernDatabase): PresetDao = db.presetDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // reasoning 模型 (DeepSeek-R1 等) 可能长时间无输出
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    @Provides
    @Singleton
    fun provideMarkwon(@ApplicationContext context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
