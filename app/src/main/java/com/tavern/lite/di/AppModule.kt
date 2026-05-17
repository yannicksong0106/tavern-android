package com.tavern.lite.di

import android.content.Context
import androidx.room.Room
import com.tavern.lite.data.db.TavernDatabase
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.PersonaDao
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
        ).fallbackToDestructiveMigration().build()
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
    fun provideScriptDao(db: TavernDatabase): ScriptDao = db.scriptDao()

    @Provides
    fun provideAuthorNoteDao(db: TavernDatabase): AuthorNoteDao = db.authorNoteDao()

    @Provides
    fun providePersonaDao(db: TavernDatabase): PersonaDao = db.personaDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // 长时间流式需要更长超时
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
}
