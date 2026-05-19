package com.tavern.lite.domain.usecase

import com.tavern.lite.data.db.entity.CharacterEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveDialogueUseCase @Inject constructor() {

    // 主动对话冷却机制：characterId -> last proactive timestamp
    private val lastProactiveTime = mutableMapOf<Long, Long>()

    /**
     * 判断是否应该触发单聊主动对话
     * @return 延迟毫秒数，null 表示不触发
     */
    fun shouldScheduleProactive(chattiness: Int): Long? {
        if (chattiness <= 0) return null
        val probability = chattiness / 100.0
        if (Math.random() > probability) return null
        return 2000L + (Math.random() * 2000).toLong()
    }

    /**
     * 判断是否应该触发群聊主动对话
     * @return 延迟毫秒数，null 表示不触发
     */
    fun shouldScheduleGroupProactive(characters: List<CharacterEntity>): Long? {
        if (characters.isEmpty()) return null
        val maxChattiness = characters.maxOf { it.chattiness }
        if (maxChattiness <= 0) return null

        val probability = 0.3 + (maxChattiness / 100.0) * 0.2 // 30%-50%
        if (Math.random() > probability) return null
        return 1000L + (Math.random() * 2000).toLong()
    }

    /**
     * 选择下一个主动发言的角色（按健谈度加权随机，带冷却机制）
     */
    fun selectNextProactiveCharacter(characters: List<CharacterEntity>): CharacterEntity? {
        val now = System.currentTimeMillis()
        val cooldownMs = 30_000L // 30 秒冷却

        val available = characters.filter { char ->
            val lastTime = lastProactiveTime[char.id] ?: 0
            now - lastTime > cooldownMs
        }

        if (available.isEmpty()) return null

        val totalWeight = available.sumOf { it.chattiness }
        if (totalWeight <= 0) return available.random()

        var random = Math.random() * totalWeight
        for (char in available) {
            random -= char.chattiness
            if (random <= 0) {
                lastProactiveTime[char.id] = now
                return char
            }
        }

        val selected = available.last()
        lastProactiveTime[selected.id] = now
        return selected
    }

    /**
     * 处理 @ 消息：检测 @ 角色名 并返回匹配的角色
     * @return 匹配的角色和清理后的消息内容，null 表示不是 @ 消息
     */
    fun parseAtMention(content: String, characters: List<CharacterEntity>): Pair<CharacterEntity, String>? {
        val match = AT_MENTION_PATTERN.find(content) ?: return null
        val mentionedName = match.groupValues[1]
        val mentionedChar = characters.find {
            it.name.equals(mentionedName, ignoreCase = true)
        } ?: return null

        val cleanContent = content.replaceFirst(AT_MENTION_PATTERN, "").trim()
        return mentionedChar to cleanContent
    }

    /**
     * 检查触发条件并选择下一个群聊主动发言角色
     * @return 选中的角色，null 表示不触发
     */
    fun selectGroupProactiveCharacter(characters: List<CharacterEntity>): CharacterEntity? {
        if (shouldScheduleGroupProactive(characters) == null) return null
        return selectNextProactiveCharacter(characters)
    }

    companion object {
        // 预编译的 @ 提及正则
        private val AT_MENTION_PATTERN = Regex("@(\\S+?)(?:\\s|$)")
    }
}
