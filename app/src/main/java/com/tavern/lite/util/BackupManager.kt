package com.tavern.lite.util

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.tavern.lite.data.db.TavernDatabase
import com.tavern.lite.data.db.dao.AuthorNoteDao
import com.tavern.lite.data.db.dao.BgmDao
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
import com.tavern.lite.data.db.dao.SpriteDao
import com.tavern.lite.data.db.dao.SummaryDao
import com.tavern.lite.data.db.dao.WorldBookDao
import com.tavern.lite.data.model.BackupData
import com.tavern.lite.data.model.AuthorNoteBackup
import com.tavern.lite.data.model.BgmBackup
import com.tavern.lite.data.model.BranchBackup
import com.tavern.lite.data.model.CharacterPersonaBackup
import com.tavern.lite.data.model.CharacterBackup
import com.tavern.lite.data.model.ChatCharacterBackup
import com.tavern.lite.data.model.ChatBackup
import com.tavern.lite.data.model.MemoryAtomBackup
import com.tavern.lite.data.model.MemoryBackup
import com.tavern.lite.data.model.MessageBackup
import com.tavern.lite.data.model.PersonaBackup
import com.tavern.lite.data.model.PresetBackup
import com.tavern.lite.data.model.RestoreResult
import com.tavern.lite.data.model.ScriptBackup
import com.tavern.lite.data.model.SpriteBackup
import com.tavern.lite.data.model.SummaryBackup
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
    private val db: TavernDatabase,
    private val characterDao: CharacterDao,
    private val chatDao: ChatDao,
    private val chatCharacterDao: ChatCharacterDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val memoryAtomDao: MemoryAtomDao,
    private val worldBookDao: WorldBookDao,
    private val scriptDao: ScriptDao,
    private val authorNoteDao: AuthorNoteDao,
    private val personaDao: PersonaDao,
    private val presetDao: PresetDao,
    private val branchDao: BranchDao,
    private val summaryDao: SummaryDao,
    private val spriteDao: SpriteDao,
    private val bgmDao: BgmDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val currentAppVersion: String by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
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
                        tags = it.tags, worldBookId = it.worldBookId, presetId = it.presetId,
                        backgroundPath = it.backgroundPath,
                        creator = it.creator, version = it.version, spec = it.spec,
                        chattiness = it.chattiness, createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                }
            }

            val chatsDeferred = async {
                chatDao.getAllChatsSync().map {
                    ChatBackup(
                        id = it.id, characterId = it.characterId, name = it.name,
                        backgroundPath = it.backgroundPath, presetId = it.presetId, isGroup = it.isGroup,
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
                        swipeContent = it.swipeContent, swipeIndex = it.swipeIndex,
                        replyToId = it.replyToId, isPinned = it.isPinned,
                        imagePaths = it.imagePaths
                    )
                }
            }

            val chatCharactersDeferred = async {
                chatCharacterDao.getAllChatCharacters().map {
                    ChatCharacterBackup(
                        id = it.id,
                        chatId = it.chatId,
                        characterId = it.characterId,
                        displayOrder = it.displayOrder,
                        isActive = it.isActive,
                        chattiness = it.chattiness,
                        createdAt = it.createdAt
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

            val characterPersonasDeferred = async {
                personaDao.getAllCharacterPersonas().map {
                    CharacterPersonaBackup(
                        characterId = it.characterId,
                        personaId = it.personaId
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

            val branchesDeferred = async {
                branchDao.getAllBranches().map {
                    BranchBackup(
                        id = it.id,
                        chatId = it.chatId,
                        name = it.name,
                        isDefault = it.isDefault,
                        createdAt = it.createdAt
                    )
                }
            }

            val summariesDeferred = async {
                summaryDao.getAllSummaries().map {
                    SummaryBackup(
                        id = it.id,
                        chatId = it.chatId,
                        content = it.content,
                        messageRangeStart = it.messageRangeStart,
                        messageRangeEnd = it.messageRangeEnd,
                        tokenCount = it.tokenCount,
                        createdAt = it.createdAt
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

            val bgmsDeferred = async {
                bgmDao.getAllBgms().map {
                    BgmBackup(
                        id = it.id, characterId = it.characterId, name = it.name,
                        audioPath = it.audioPath, loop = it.loop, volume = it.volume,
                        emotion = it.emotion, displayOrder = it.displayOrder, createdAt = it.createdAt
                    )
                }
            }

            val authorNotesDeferred = async {
                authorNoteDao.getAllAuthorNotesSync().map {
                    AuthorNoteBackup(
                        id = it.id, characterId = it.characterId, content = it.content,
                        position = it.position, depth = it.depth, updatedAt = it.updatedAt
                    )
                }
            }

            // Await all queries and build backup data
            val backupData = BackupData(
                appVersion = currentAppVersion,
                characters = charactersDeferred.await(),
                chats = chatsDeferred.await(),
                chatCharacters = chatCharactersDeferred.await(),
                messages = messagesDeferred.await(),
                memories = memoriesDeferred.await(),
                memoryAtoms = memoryAtomsDeferred.await(),
                worldBooks = worldBooksDeferred.await(),
                worldBookEntries = worldBookEntriesDeferred.await(),
                scripts = scriptsDeferred.await(),
                personas = personasDeferred.await(),
                characterPersonas = characterPersonasDeferred.await(),
                presets = presetsDeferred.await(),
                branches = branchesDeferred.await(),
                summaries = summariesDeferred.await(),
                sprites = spritesDeferred.await(),
                bgms = bgmsDeferred.await(),
                authorNotes = authorNotesDeferred.await()
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

            // 版本兼容性检查：拒绝来自更高版本的备份
            val backupVersion = data.appVersion
            if (isVersionNewer(backupVersion, currentAppVersion)) {
                return@withContext Result.failure(
                    IllegalStateException("备份来自更高版本 ($backupVersion)，当前版本 ($currentAppVersion) 无法恢复。请先更新应用。")
                )
            }

            // 在事务中执行全部恢复，失败时自动回滚
            db.withTransaction {
            var charactersRestored = 0
            var chatsRestored = 0
            var messagesRestored = 0
            var memoriesRestored = 0
            var worldBooksRestored = 0
            var scriptsRestored = 0
            var personasRestored = 0
            var presetsRestored = 0
            var chatCharactersRestored = 0
            var characterPersonasRestored = 0
            var branchesRestored = 0
            var summariesRestored = 0
            var spritesRestored = 0
            var bgmsRestored = 0
            var authorNotesRestored = 0

            // Restore characters
            for (c in data.characters) {
                characterDao.insert(
                    com.tavern.lite.data.db.entity.CharacterEntity(
                        id = c.id, name = c.name, description = c.description,
                        personality = c.personality, firstMes = c.firstMessage,
                        mesExample = c.mesExample, avatarPath = c.avatarPath,
                        systemPrompt = c.systemPrompt, postHistoryInstructions = c.postHistoryInstructions,
                        tags = c.tags, worldBookId = c.worldBookId, presetId = c.presetId,
                        backgroundPath = c.backgroundPath,
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
                        backgroundPath = c.backgroundPath, presetId = c.presetId, isGroup = c.isGroup,
                        groupChattiness = c.groupChattiness,
                        schedulingStrategy = c.schedulingStrategy,
                        messageIntervalMs = c.messageIntervalMs,
                        createdAt = c.createdAt,
                        updatedAt = c.updatedAt
                    )
                )
                chatsRestored++
            }

            // Restore group chat membership
            for (cc in data.chatCharacters) {
                chatCharacterDao.insert(
                    com.tavern.lite.data.db.entity.ChatCharacterEntity(
                        id = cc.id,
                        chatId = cc.chatId,
                        characterId = cc.characterId,
                        displayOrder = cc.displayOrder,
                        isActive = cc.isActive,
                        chattiness = cc.chattiness,
                        createdAt = cc.createdAt
                    )
                )
                chatCharactersRestored++
            }

            // Restore branches
            for (b in data.branches) {
                branchDao.insert(
                    com.tavern.lite.data.db.entity.BranchEntity(
                        id = b.id,
                        chatId = b.chatId,
                        name = b.name,
                        isDefault = b.isDefault,
                        createdAt = b.createdAt
                    )
                )
                branchesRestored++
            }

            // Restore messages
            for (m in data.messages) {
                messageDao.insert(
                    com.tavern.lite.data.db.entity.MessageEntity(
                        id = m.id, chatId = m.chatId, role = m.role, content = m.content,
                        characterId = m.characterId, parentId = m.parentId, branchId = m.branchId,
                        isActive = m.isActive, createdAt = m.createdAt,
                        swipeContent = m.swipeContent, swipeIndex = m.swipeIndex,
                        replyToId = m.replyToId, isPinned = m.isPinned,
                        imagePaths = m.imagePaths
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

            // Restore character-persona links
            for (cp in data.characterPersonas) {
                personaDao.linkCharacterPersona(
                    com.tavern.lite.data.db.entity.CharacterPersonaEntity(
                        characterId = cp.characterId,
                        personaId = cp.personaId
                    )
                )
                characterPersonasRestored++
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

            // Restore summaries
            for (s in data.summaries) {
                summaryDao.insert(
                    com.tavern.lite.data.db.entity.SummaryEntity(
                        id = s.id,
                        chatId = s.chatId,
                        content = s.content,
                        messageRangeStart = s.messageRangeStart,
                        messageRangeEnd = s.messageRangeEnd,
                        tokenCount = s.tokenCount,
                        createdAt = s.createdAt
                    )
                )
                summariesRestored++
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

            // Restore bgms
            for (b in data.bgms) {
                bgmDao.insert(
                    com.tavern.lite.data.db.entity.BgmEntity(
                        id = b.id, characterId = b.characterId, name = b.name,
                        audioPath = b.audioPath, loop = b.loop, volume = b.volume,
                        emotion = b.emotion, displayOrder = b.displayOrder, createdAt = b.createdAt
                    )
                )
                bgmsRestored++
            }

            // Restore author notes
            for (a in data.authorNotes) {
                authorNoteDao.insertOrUpdate(
                    com.tavern.lite.data.db.entity.AuthorNoteEntity(
                        id = a.id, characterId = a.characterId, content = a.content,
                        position = a.position, depth = a.depth, updatedAt = a.updatedAt
                    )
                )
                authorNotesRestored++
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
                    chatCharactersRestored = chatCharactersRestored,
                    characterPersonasRestored = characterPersonasRestored,
                    branchesRestored = branchesRestored,
                    summariesRestored = summariesRestored,
                    spritesRestored = spritesRestored,
                    bgmsRestored = bgmsRestored,
                    authorNotesRestored = authorNotesRestored,
                    backupAppVersion = backupVersion
                )
            )
            } // db.withTransaction
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("BackupManager", "恢复失败", e)
            Result.failure(e)
        }
    }

    companion object {
        /**
         * 比较两个版本号，判断 backupVersion 是否比 currentVersion 更新。
         * 支持 "major.minor.patch" 格式，如 "1.2.8"。
         */
        @VisibleForTesting
        internal fun isVersionNewer(backupVersion: String, currentVersion: String): Boolean {
            val bParts = parseVersion(backupVersion)
            val cParts = parseVersion(currentVersion)
            for (i in 0 until maxOf(bParts.size, cParts.size)) {
                val b = bParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (b > c) return true
                if (b < c) return false
            }
            return false
        }

        @VisibleForTesting
        internal fun parseVersion(version: String): List<Int> {
            return version.split(".").mapNotNull { it.toIntOrNull() }
        }
    }
}
