package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.MemoryAtomDao
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryConsolidator @Inject constructor(
    private val atomDao: MemoryAtomDao
) {

    companion object {
        private const val CONSOLIDATION_THRESHOLD = 50
        private const val SIMILARITY_THRESHOLD = 0.6
    }

    /**
     * Insert new atoms with deduplication.
     * Checks for similar existing memories before inserting.
     * Returns the number of atoms actually inserted.
     */
    suspend fun insertWithDedup(atoms: List<MemoryAtomEntity>): Int {
        var inserted = 0
        for (atom in atoms) {
            if (!isDuplicate(atom)) {
                atomDao.insert(atom)
                inserted++
            }
        }
        return inserted
    }

    /**
     * Check if a similar memory already exists.
     * Uses keyword overlap for lightweight similarity check.
     */
    private suspend fun isDuplicate(atom: MemoryAtomEntity): Boolean {
        // Check exact substring match first
        val exactMatch = atomDao.findSimilar(atom.characterId, atom.content)
        if (exactMatch != null) {
            // Update access time of existing memory
            atomDao.touchAtoms(listOf(exactMatch.id))
            return true
        }

        // Check keyword overlap
        val keywords = extractKeywords(atom.content)
        if (keywords.isEmpty()) return false

        val existingAtoms = atomDao.getAtomsByCategory(
            atom.characterId,
            atom.category,
            20
        )

        for (existing in existingAtoms) {
            val existingKeywords = extractKeywords(existing.content)
            val overlap = keywords.intersect(existingKeywords).size
            val similarity = overlap.toDouble() / maxOf(keywords.size, existingKeywords.size).coerceAtLeast(1)

            if (similarity >= SIMILARITY_THRESHOLD) {
                // If new atom is more important, supersede the old one
                if (atom.importance > existing.importance) {
                    atomDao.supersede(existing.id)
                    return false // Insert the new one
                }
                atomDao.touchAtoms(listOf(existing.id))
                return true
            }
        }

        return false
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
        // 1. Purge superseded atoms
        atomDao.purgeSuperseded(characterId)

        // 2. Check for conflicts within each category
        resolveConflicts(characterId, "user_info")
        resolveConflicts(characterId, "character_consistency")
        resolveConflicts(characterId, "commitment")
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

        for (atom in atoms) {
            if (atom.id in assigned) continue

            val group = mutableListOf(atom)
            assigned.add(atom.id)

            val keywords = extractKeywords(atom.content)

            for (other in atoms) {
                if (other.id in assigned) continue
                val otherKeywords = extractKeywords(other.content)
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
        val stopWords = setOf(
            "的", "了", "是", "在", "我", "你", "他", "她", "它",
            "和", "与", "或", "但", "而", "也", "都", "就", "还",
            "这", "那", "有", "没", "不", "会", "能", "可以",
            "很", "非常", "特别", "最", "比较", "a", "an", "the",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can",
            "i", "you", "he", "she", "it", "we", "they"
        )

        val punctuation = Regex("[\\p{P}\\p{S}\\s]+")
        return text.replace(punctuation, " ")
            .split(Regex("\\s+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()
    }
}
