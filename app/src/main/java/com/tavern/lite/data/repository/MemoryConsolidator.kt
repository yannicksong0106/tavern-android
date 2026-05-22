package com.tavern.lite.data.repository

import androidx.room.withTransaction
import com.tavern.lite.data.db.TavernDatabase
import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryConsolidator @Inject constructor(
    private val atomDao: MemoryAtomDao,
    private val database: TavernDatabase
) {
    // 可替换的事务执行器（默认使用 Room 事务，测试时可注入）
    internal var transactionOverride: (suspend (suspend () -> Unit) -> Unit)? = null

    private suspend fun runTransaction(block: suspend () -> Unit) {
        val override = transactionOverride
        if (override != null) {
            override(block)
        } else {
            database.withTransaction(block)
        }
    }

    companion object {
        private const val CONSOLIDATION_THRESHOLD = 50
        private const val SIMILARITY_THRESHOLD = 0.6
        private val PUNCTUATION_REGEX = Regex("[\\p{P}\\p{S}\\s]+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val STOP_WORDS = setOf(
            "的", "了", "是", "在", "我", "你", "他", "她", "它",
            "和", "与", "或", "但", "而", "也", "都", "就", "还",
            "这", "那", "有", "没", "不", "会", "能", "可以",
            "很", "非常", "特别", "最", "比较", "a", "an", "the",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can",
            "i", "you", "he", "she", "it", "we", "they"
        )
        private val EMOTION_WORDS = setOf("开心", "难过", "生气", "害怕", "喜欢", "讨厌", "感动", "焦虑", "兴奋", "失落")
        private val HABIT_WORDS = setOf("习惯", "总是", "经常", "每天", "每次", "一般", "通常", "喜欢做")
        private val PREFERENCE_WORDS = setOf("喜欢", "偏好", "最爱", "最讨厌", "喜欢的", "不喜欢")
        private val CONSOLIDATION_CATEGORIES = listOf("fact", "emotion", "preference", "event", "habit", "character_consistency")
    }

    /**
     * Insert new atoms with batch deduplication.
     * Fetches existing atoms by category once, then checks all new atoms in memory.
     * Returns the number of atoms actually inserted.
     */
    suspend fun insertWithDedup(atoms: List<MemoryAtomEntity>): Int {
        if (atoms.isEmpty()) return 0

        val characterId = atoms.first().characterId

        // Batch-fetch existing atoms grouped by category (one query per category instead of per atom)
        val categories = atoms.map { it.category }.distinct()
        val existingByCategory = categories.associateWith { category ->
            atomDao.getAtomsByCategory(characterId, category, 50)
        }
        // Pre-compute keywords for existing atoms
        val existingKeywordsCache = existingByCategory.values.flatten().associateWith {
            extractKeywords(it.content)
        }

        val toInsert = mutableListOf<MemoryAtomEntity>()
        val toSupersede = mutableListOf<Long>()
        val toTouch = mutableListOf<Long>()

        for (atom in atoms) {
            val existingAtoms = existingByCategory[atom.category] ?: emptyList()
            val result = checkDuplicate(atom, existingAtoms, existingKeywordsCache)
            when (result) {
                is DedupResult.Duplicate -> toTouch.add(result.existingId)
                is DedupResult.Supersede -> toSupersede.add(result.oldId).also { toInsert.add(atom) }
                is DedupResult.Unique -> toInsert.add(atom)
            }
        }

        // Batch DB operations wrapped in transaction for atomicity
        runTransaction {
            if (toSupersede.isNotEmpty()) {
                for (id in toSupersede) atomDao.supersede(id)
            }
            if (toTouch.isNotEmpty()) atomDao.touchAtoms(toTouch)
            for (atom in toInsert) atomDao.insert(atom)
        }

        return toInsert.size
    }

    private sealed class DedupResult {
        data class Duplicate(val existingId: Long) : DedupResult()
        data class Supersede(val oldId: Long) : DedupResult()
        data object Unique : DedupResult()
    }

    /**
     * Check if an atom duplicates any existing atom.
     * Returns Duplicate (skip), Supersede (replace old), or Unique (insert new).
     */
    private fun checkDuplicate(
        atom: MemoryAtomEntity,
        existingAtoms: List<MemoryAtomEntity>,
        keywordsCache: Map<MemoryAtomEntity, Set<String>>
    ): DedupResult {
        // Exact substring match
        val exactMatch = existingAtoms.find { it.content.contains(atom.content) || atom.content.contains(it.content) }
        if (exactMatch != null) {
            return if (atom.importance > exactMatch.importance) {
                DedupResult.Supersede(exactMatch.id)
            } else {
                DedupResult.Duplicate(exactMatch.id)
            }
        }

        // Keyword overlap
        val keywords = extractKeywords(atom.content)
        if (keywords.isEmpty()) return DedupResult.Unique

        for (existing in existingAtoms) {
            val existingKeywords = keywordsCache[existing] ?: extractKeywords(existing.content)
            val overlap = keywords.intersect(existingKeywords).size
            val similarity = overlap.toDouble() / maxOf(keywords.size, existingKeywords.size).coerceAtLeast(1)

            if (similarity >= SIMILARITY_THRESHOLD) {
                return if (atom.importance > existing.importance) {
                    DedupResult.Supersede(existing.id)
                } else {
                    DedupResult.Duplicate(existing.id)
                }
            }
        }

        return DedupResult.Unique
    }

    /**
     * Periodic consolidation: check if we need to merge and clean up.
     * Call this after every batch insertion.
     */
    suspend fun maybeConsolidate(characterId: Long) {
        val count = atomDao.getAtomCount(characterId)
        if (count >= CONSOLIDATION_THRESHOLD) {
            consolidate(characterId)
        }
    }

    /**
     * Merge related memories and remove superseded ones.
     */
    suspend fun consolidate(characterId: Long) {
        // 1. Purge superseded and expired atoms
        atomDao.purgeSuperseded(characterId)
        atomDao.purgeExpired()

        // 2. Check for conflicts within each category (temporary memories skip — they expire)
        for (category in CONSOLIDATION_CATEGORIES) {
            resolveConflicts(characterId, category)
        }
    }

    /**
     * Promote temporary memories to core if they are accessed frequently.
     */
    suspend fun promoteTemporaryIfNeeded(atom: MemoryAtomEntity) {
        if (atom.category == "temporary" && atom.accessCount >= 3) {
            val promoted = atom.copy(
                category = guessCoreCategory(atom.content),
                expiresAt = null
            )
            atomDao.supersede(atom.id)
            atomDao.insert(promoted)
        }
    }

    private fun guessCoreCategory(content: String): String = when {
        EMOTION_WORDS.any { content.contains(it) } -> "emotion"
        HABIT_WORDS.any { content.contains(it) } -> "habit"
        PREFERENCE_WORDS.any { content.contains(it) } -> "preference"
        else -> "fact"
    }

    /**
     * Resolve conflicts within a category.
     * For example, if we have "用户是学生" and later "用户已经工作了",
     * the newer one should supersede the older one.
     */
    private suspend fun resolveConflicts(characterId: Long, category: String) {
        val atoms = atomDao.getAtomsByCategory(characterId, category, 50)
        if (atoms.size <= 1) return

        // Group by keyword similarity
        val groups = groupBySimilarity(atoms)

        for (group in groups) {
            if (group.size <= 1) continue

            // Keep the most recent and most important, supersede the rest
            val sorted = group.sortedWith(
                compareByDescending<MemoryAtomEntity> { it.createdAt }
                    .thenByDescending { it.importance }
            )
            val keeper = sorted.first()

            for (i in 1 until sorted.size) {
                val candidate = sorted[i]
                // Only supersede if the keeper is clearly better
                if (keeper.importance >= candidate.importance ||
                    keeper.createdAt > candidate.createdAt
                ) {
                    atomDao.supersede(candidate.id)
                }
            }
        }
    }

    /**
     * Group atoms by keyword similarity.
     */
    private fun groupBySimilarity(atoms: List<MemoryAtomEntity>): List<List<MemoryAtomEntity>> {
        val groups = mutableListOf<MutableList<MemoryAtomEntity>>()
        val assigned = mutableSetOf<Long>()
        val keywordCache = atoms.associate { it.id to extractKeywords(it.content) }

        for (atom in atoms) {
            if (atom.id in assigned) continue

            val group = mutableListOf(atom)
            assigned.add(atom.id)

            val keywords = keywordCache[atom.id] ?: emptySet()

            for (other in atoms) {
                if (other.id in assigned) continue
                val otherKeywords = keywordCache[other.id] ?: emptySet()
                val overlap = keywords.intersect(otherKeywords).size
                val similarity = overlap.toDouble() / maxOf(keywords.size, otherKeywords.size).coerceAtLeast(1)

                if (similarity >= SIMILARITY_THRESHOLD) {
                    group.add(other)
                    assigned.add(other.id)
                }
            }

            groups.add(group)
        }

        return groups
    }

    /**
     * Extract meaningful keywords from text.
     * Removes common stop words and short words.
     */
    private fun extractKeywords(text: String): Set<String> {
        return text.replace(PUNCTUATION_REGEX, " ")
            .split(WHITESPACE_REGEX)
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()
    }
}
