package com.tavern.lite.util

import android.content.Context
import android.util.Log
import com.tavern.lite.data.db.dao.CharacterDao
import com.tavern.lite.data.db.dao.ChatDao
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.dao.MemoryDao
import com.tavern.lite.data.db.dao.MessageDao
import com.tavern.lite.data.db.dao.PersonaDao
import com.tavern.lite.data.db.dao.PresetDao
import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.dao.SpriteDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.model.BackupData
import com.tavern.lite.data.model.CharacterBackup
import com.tavern.lite.data.model.ChatBackup
import com.tavern.lite.data.model.MemoryAtomBackup
import com.tavern.lite.data.model.MemoryBackup
import com.tavern.lite.data.model.MessageBackup
import com.tavern.lite.data.model.PersonaBackup
import com.tavern.lite.data.model.PresetBackup
import com.tavern.lite.data.model.RestoreResult
import com.tavern.lite.data.model.ScriptBackup
import com.tavern.lite.data.model.SpriteBackup
import com.tavern.lite.data.model.WorldBookBackup
import com.tavern.lite.data.model.WorldBookEntryBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterDao: CharacterDao,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val memoryAtomDao: MemoryAtomDao,
    private val worldBookDao: WorldBookDao,
    private val scriptDao: ScriptDao,
    private val personaDao: PersonaDao,
    private val presetDao: PresetDao,
    private val spriteDao: SpriteDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun backup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Run all DB queries in parallel for faster backup
            val charactersDeferred = async {
                characterDao.getAllCharactersSync().map {
                    CharacterBackup(
                        id = it.id, name = it.name, description = it.description,
                        personality = it.personality, firstMessage = it.firstMes,
                        mesExample = it.mesExample, avatarPath = it.avatarPath,
                        systemPrompt = it.systemPrompt, postHistoryInstructions = it.postHistoryInstructions,
                        tags = it.tags, worldBookId = it.worldBookId, backgroundPath = it.backgroundPath,
                        creator = it.creator, version = it.version, spec = it.spec,
                        chattiness = it.chattiness, createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                }
            }

            val chatsDeferred = async {
                chatDao.getAllChatsSync().map {
                    ChatBackup(
                        id = it.id, characterId = it.characterId, name = it.name,
                        backgroundPath = it.backgroundPath, isGroup = it.isGroup,
                        groupChattiness = it.groupChattiness,
                        schedulingStrategy = it.schedulingStrategy,
                        messageIntervalMs = it.messageIntervalMs,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }
            }

            val messagesDeferred = async {
                messageDao.getAllMessages().map {
                    MessageBackup(
                        id = it.id, chatId = it.chatId, role = it.role, content = it.content,
                        characterId = it.characterId, parentId = it.parentId, branchId = it.branchId,
                        isActive = it.isActive, createdAt = it.createdAt,
                        swipeContent = it.swipeContent, swipeIndex = it.swipeIndex
                    )
                }
            }

            val memoriesDeferred = async {
                memoryDao.getAllMemories().map {
                    MemoryBackup(
                        id = it.id, characterId = it.characterId, content = it.content,
                        importance = it.importance, source = it.source, createdAt = it.createdAt,
                        lastAccessed = it.lastAccessed, accessCount = it.accessCount
                    )
                }
            }

            val memoryAtomsDeferred = async {
                memoryAtomDao.getAllMemoryAtoms().map {
                    MemoryAtomBackup(
                        id = it.id, characterId = it.characterId, content = it.content,
                        category = it.category, importance = it.importance, source = it.source,
                        sourceChatId = it.sourceChatId, sourceMessageId = it.sourceMessageId,
                        superseded = it.superseded, createdAt = it.createdAt,
                        lastAccessed = it.lastAccessed, accessCount = it.accessCount,
                        expiresAt = it.expiresAt
                    )
                }
            }

            val worldBooksDeferred = async {
                worldBookDao.getAllWorldBooksSync().map {
                    WorldBookBackup(
                        id = it.id, name = it.name, description = it.description,
                        createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                }
            }

            val worldBookEntriesDeferred = async {
                worldBookDao.getAllEntriesSync().map {
                    WorldBookEntryBackup(
                        id = it.id, worldBookId = it.worldBookId, uid = it.uid,
                        comment = it.comment, keys = it.keys, keysSecondary = it.keysSecondary,
                        content = it.content, constant = it.constant, position = it.position,
                        orderVal = it.orderVal, probability = it.probability, depth = it.depth,
                        disabled = it.disabled, selective = it.selective,
                        selectiveLogic = it.selectiveLogic, excludeRecursion = it.excludeRecursion,
                        preventRecursion = it.preventRecursion, group = it.group,
                        groupOverride = it.groupOverride, groupWeight = it.groupWeight
                    )
                }
            }

            val scriptsDeferred = async {
                scriptDao.getAllScripts().map {
                    ScriptBackup(
                        id = it.id, characterId = it.characterId, name = it.name,
                        comment = it.comment, scriptType = it.scriptType,
                        findPattern = it.findPattern, replacePattern = it.replacePattern,
                        isRegex = it.isRegex, caseSensitive = it.caseSensitive,
                        enabled = it.enabled, sortOrder = it.sortOrder
                    )
                }
            }

            val personasDeferred = async {
                personaDao.getAllPersonasSync().map {
                    PersonaBackup(
                        id = it.id, name = it.name, biography = it.biography,
                        avatarPath = it.avatarPath, isDefault = it.isDefault,
                        createdAt = it.createdAt
                    )
                }
            }

            val presetsDeferred = async {
                presetDao.getAllPresetsSync().map {
                    PresetBackup(
                        id = it.id, name = it.name, description = it.description,
                        systemPrompt = it.systemPrompt, postHistoryInstructions = it.postHistoryInstructions,
                        authorNote = it.authorNote, isDefault = it.isDefault,
                        createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                }
            }

            val spritesDeferred = async {
                spriteDao.getAllSprites().map {
                    SpriteBackup(
                        id = it.id, characterId = it.characterId, emotion = it.emotion,
                        imagePath = it.imagePath, displayOrder = it.displayOrder,
                        createdAt = it.createdAt
                    )
                }
            }

            // Await all queries and build backup data
            val backupData = BackupData(
                characters = charactersDeferred.await(),
                chats = chatsDeferred.await(),
                messages = messagesDeferred.await(),
                memories = memoriesDeferred.await(),
                memoryAtoms = memoryAtomsDeferred.await(),
                worldBooks = worldBooksDeferred.await(),
                worldBookEntries = worldBookEntriesDeferred.await(),
                scripts = scriptsDeferred.await(),
                personas = personasDeferred.await(),
                presets = presetsDeferred.await(),
                sprites = spritesDeferred.await()
            )

            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(backupDir, "tavern_backup_${System.currentTimeMillis()}.json")
            file.writeText(json.encodeToString(BackupData.serializer(), backupData))

            Result.success(file)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("BackupManager", "备份失败", e)
            Result.failure(e)
        }
    }

    suspend fun restore(inputStream: InputStream): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            val text = inputStream.bufferedReader().use { it.readText() }
            val data = json.decodeFromString(BackupData.serializer(), text)

            var charactersRestored = 0
            var chatsRestored = 0
            var messagesRestored = 0
            var memoriesRestored = 0
            var worldBooksRestored = 0
            var scriptsRestored = 0
            var personasRestored = 0
            var presetsRestored = 0
            var spritesRestored = 0

            // Restore characters
            for (c in data.characters) {
                characterDao.insert(
                    com.tavern.lite.data.db.entity.CharacterEntity(
                        id = c.id, name = c.name, description = c.description,
                        personality = c.personality, firstMes = c.firstMessage,
                        mesExample = c.mesExample, avatarPath = c.avatarPath,
                        systemPrompt = c.systemPrompt, postHistoryInstructions = c.postHistoryInstructions,
                        tags = c.tags, worldBookId = c.worldBookId, backgroundPath = c.backgroundPath,
                        creator = c.creator, version = c.version, spec = c.spec,
                        chattiness = c.chattiness, createdAt = c.createdAt, updatedAt = c.updatedAt
                    )
                )
                charactersRestored++
            }

            // Restore chats
            for (c in data.chats) {
                chatDao.insert(
                    com.tavern.lite.data.db.entity.ChatEntity(
                        id = c.id, characterId = c.characterId, name = c.name,
                        backgroundPath = c.backgroundPath, isGroup = c.isGroup,
                        groupChattiness = c.groupChattiness,
                        schedulingStrategy = c.schedulingStrategy,
                        messageIntervalMs = c.messageIntervalMs,
                        createdAt = c.createdAt,
                        updatedAt = c.updatedAt
                    )
                )
                chatsRestored++
            }

            // Restore messages
            for (m in data.messages) {
                messageDao.insert(
                    com.tavern.lite.data.db.entity.MessageEntity(
                        id = m.id, chatId = m.chatId, role = m.role, content = m.content,
                        characterId = m.characterId, parentId = m.parentId, branchId = m.branchId,
                        isActive = m.isActive, createdAt = m.createdAt,
                        swipeContent = m.swipeContent, swipeIndex = m.swipeIndex
                    )
                )
                messagesRestored++
            }

            // Restore memories
            for (m in data.memories) {
                memoryDao.insert(
                    com.tavern.lite.data.db.entity.MemoryEntity(
                        id = m.id, characterId = m.characterId, content = m.content,
                        importance = m.importance, source = m.source, createdAt = m.createdAt,
                        lastAccessed = m.lastAccessed, accessCount = m.accessCount
                    )
                )
                memoriesRestored++
            }

            // Restore memory atoms
            for (a in data.memoryAtoms) {
                memoryAtomDao.insert(
                    com.tavern.lite.data.db.entity.MemoryAtomEntity(
                        id = a.id, characterId = a.characterId, content = a.content,
                        category = a.category, importance = a.importance, source = a.source,
                        sourceChatId = a.sourceChatId, sourceMessageId = a.sourceMessageId,
                        superseded = a.superseded, createdAt = a.createdAt,
                        lastAccessed = a.lastAccessed, accessCount = a.accessCount,
                        expiresAt = a.expiresAt
                    )
                )
            }

            // Restore world books
            for (wb in data.worldBooks) {
                worldBookDao.insertWorldBook(
                    com.tavern.lite.data.db.entity.WorldBookEntity(
                        id = wb.id, name = wb.name, description = wb.description,
                        createdAt = wb.createdAt, updatedAt = wb.updatedAt
                    )
                )
                worldBooksRestored++
            }

            // Restore world book entries
            for (e in data.worldBookEntries) {
                worldBookDao.insertEntry(
                    com.tavern.lite.data.db.entity.WorldBookEntryEntity(
                        id = e.id, worldBookId = e.worldBookId, uid = e.uid,
                        comment = e.comment, keys = e.keys, keysSecondary = e.keysSecondary,
                        content = e.content, constant = e.constant, position = e.position,
                        orderVal = e.orderVal, probability = e.probability, depth = e.depth,
                        disabled = e.disabled, selective = e.selective,
                        selectiveLogic = e.selectiveLogic, excludeRecursion = e.excludeRecursion,
                        preventRecursion = e.preventRecursion, group = e.group,
                        groupOverride = e.groupOverride, groupWeight = e.groupWeight
                    )
                )
            }

            // Restore scripts
            for (s in data.scripts) {
                scriptDao.insertScript(
                    com.tavern.lite.data.db.entity.ScriptEntity(
                        id = s.id, characterId = s.characterId, name = s.name,
                        comment = s.comment, scriptType = s.scriptType,
                        findPattern = s.findPattern, replacePattern = s.replacePattern,
                        isRegex = s.isRegex, caseSensitive = s.caseSensitive,
                        enabled = s.enabled, sortOrder = s.sortOrder
                    )
                )
                scriptsRestored++
            }

            // Restore personas
            for (p in data.personas) {
                personaDao.insert(
                    com.tavern.lite.data.db.entity.PersonaEntity(
                        id = p.id, name = p.name, biography = p.biography,
                        avatarPath = p.avatarPath, isDefault = p.isDefault,
                        createdAt = p.createdAt
                    )
                )
                personasRestored++
            }

            // Restore presets
            for (p in data.presets) {
                presetDao.insertPreset(
                    com.tavern.lite.data.db.entity.PresetEntity(
                        id = p.id, name = p.name, description = p.description,
                        systemPrompt = p.systemPrompt, postHistoryInstructions = p.postHistoryInstructions,
                        authorNote = p.authorNote, isDefault = p.isDefault,
                        createdAt = p.createdAt, updatedAt = p.updatedAt
                    )
                )
                presetsRestored++
            }

            // Restore sprites
            for (s in data.sprites) {
                spriteDao.insert(
                    com.tavern.lite.data.db.entity.SpriteEntity(
                        id = s.id, characterId = s.characterId, emotion = s.emotion,
                        imagePath = s.imagePath, displayOrder = s.displayOrder,
                        createdAt = s.createdAt
                    )
                )
                spritesRestored++
            }

            Result.success(
                RestoreResult(
                    charactersRestored = charactersRestored,
                    chatsRestored = chatsRestored,
                    messagesRestored = messagesRestored,
                    memoriesRestored = memoriesRestored,
                    worldBooksRestored = worldBooksRestored,
                    scriptsRestored = scriptsRestored,
                    personasRestored = personasRestored,
                    presetsRestored = presetsRestored,
                    spritesRestored = spritesRestored
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("BackupManager", "恢复失败", e)
            Result.failure(e)
        }
    }
}
