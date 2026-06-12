package com.tavern.lite.ui.screens.chat.manager

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.model.GroupSchedulingStrategy
import kotlin.random.Random

internal class GroupRespondingCharacterSelector(
    private val random: Random = Random.Default
) {
    private var roundRobinIndex = 0

    fun select(
        characters: List<CharacterEntity>,
        schedulingStrategy: GroupSchedulingStrategy,
        chattinessByCharacterId: Map<Long, Int>
    ): List<CharacterEntity> {
        if (characters.isEmpty()) return emptyList()
        return when (schedulingStrategy) {
            GroupSchedulingStrategy.NATURAL -> selectNatural(characters, chattinessByCharacterId)
            GroupSchedulingStrategy.LIST_ORDER -> characters
            GroupSchedulingStrategy.ROUND_ROBIN -> selectRoundRobin(characters)
        }
    }

    private fun selectNatural(
        characters: List<CharacterEntity>,
        chattinessByCharacterId: Map<Long, Int>
    ): List<CharacterEntity> {
        return characters.filter { character ->
            val chattiness = chattinessByCharacterId[character.id] ?: character.chattiness
            val responseChance = 0.5 + (chattiness / 100.0) * 0.5
            random.nextDouble() < responseChance
        }.ifEmpty { listOf(characters.random(random)) }
    }

    private fun selectRoundRobin(characters: List<CharacterEntity>): List<CharacterEntity> {
        val character = characters[roundRobinIndex % characters.size]
        roundRobinIndex = (roundRobinIndex + 1) % characters.size
        return listOf(character)
    }
}
