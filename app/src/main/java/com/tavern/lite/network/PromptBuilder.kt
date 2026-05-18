package com.tavern.lite.network

import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity

object PromptBuilder {

    fun build(
        character: CharacterEntity,
        userMessage: String,
        chatHistory: List<MessageEntity>,
        worldBookEntries: List<WorldBookEntryEntity> = emptyList(),
        userName: String = "User",
        memories: List<MemoryEntity> = emptyList(),
        memoryAtoms: List<MemoryAtomEntity> = emptyList(),
        authorNote: AuthorNoteEntity? = null,
        persona: PersonaEntity? = null
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // Resolve effective user name: persona name > userName param
        val effectiveUserName = persona?.name?.takeIf { it.isNotBlank() } ?: userName

        // 1. 系统 prompt
        val systemPrompt = buildSystemPrompt(character, worldBookEntries, effectiveUserName, memories, memoryAtoms, persona)
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }

        // 2. 示例对话
        val exampleMessages = parseExampleDialog(character.mesExample, effectiveUserName, character.name)
        messages.addAll(exampleMessages)

        // 3. 开场白
        if (character.firstMes.isNotBlank()) {
            val firstMes = replacePlaceholders(character.firstMes, effectiveUserName, character.name)
            messages.add(ChatMessage(role = "assistant", content = firstMes))
        }

        // 4. 聊天历史
        chatHistory.forEach { msg ->
            val role = when (msg.role) {
                "user" -> "user"
                "assistant" -> "assistant"
                else -> "system"
            }
            messages.add(ChatMessage(role = role, content = msg.content))
        }

        // 4.5 Author's Note injection (at specified depth from end of history)
        if (authorNote != null && authorNote.content.isNotBlank()) {
            val noteContent = replacePlaceholders(authorNote.content, effectiveUserName, character.name)
            val insertIndex = (messages.size - authorNote.depth).coerceAtLeast(1)
            messages.add(insertIndex, ChatMessage(role = "system", content = noteContent))
        }

        // 4.6 历史后指令（post_history_instructions）
        val postHistory = character.postHistoryInstructions
        if (!postHistory.isNullOrBlank()) {
            messages.add(ChatMessage(
                role = "system",
                content = postHistory
                    .replace("{{user}}", effectiveUserName)
                    .replace("{{char}}", character.name)
            ))
        }

        // 5. 当前用户消息
        messages.add(ChatMessage(role = "user", content = userMessage))

        return messages
    }

    private fun buildSystemPrompt(
        character: CharacterEntity,
        worldBookEntries: List<WorldBookEntryEntity>,
        userName: String,
        memories: List<MemoryEntity> = emptyList(),
        memoryAtoms: List<MemoryAtomEntity> = emptyList(),
        persona: PersonaEntity? = null
    ): String {
        val parts = mutableListOf<String>()

        // 回复风格指引（活人感基线，角色卡 systemPrompt 可覆盖）
        parts.add("""[回复风格 — 基础要求]
你正在和用户进行即时聊天，像真人发微信一样回复：
- 每条消息只说 1-3 句话，不要长篇大论
- 不同的想法之间用空行分隔（系统会自动拆成独立消息）
- 语气自然口语化，像朋友聊天，不要像写文章
- 偶尔用语气词（嗯、哈哈、哎、诶、哦）增加真实感
- 不要每次都用相同的句式开头
- 可以用省略号、感叹号表达情绪
- 回复长度随对话内容自然变化，闲聊时短一些，重要话题可以长一些
- 模仿对面用户的说话风格和用词习惯""".trimIndent())

        // 角色描述
        val desc = character.description
            .replace("{{user}}", userName)
            .replace("{{char}}", character.name)
        if (desc.isNotBlank()) parts.add(desc)

        // 性格
        val personality = character.personality
            .replace("{{user}}", userName)
            .replace("{{char}}", character.name)
        if (personality.isNotBlank()) parts.add("Personality: $personality")

        // 世界书条目
        if (worldBookEntries.isNotEmpty()) {
            val worldInfo = worldBookEntries.joinToString("\n") { entry ->
                val comment = entry.comment.ifBlank { "World Info" }
                "[$comment]\n${entry.content}"
            }
            parts.add(worldInfo)
        }

        // 记忆注入（新版结构化记忆优先）
        if (memoryAtoms.isNotEmpty()) {
            val atomText = formatMemoryAtoms(memoryAtoms, character.name)
            if (atomText.isNotBlank()) parts.add(atomText)
        } else if (memories.isNotEmpty()) {
            // Fallback to legacy memories
            val memoryText = memories.joinToString("\n") { "- ${it.content}" }
            parts.add("[Memory]\n$memoryText")
        }

        // 用户角色注入
        if (persona != null && persona.biography.isNotBlank()) {
            val bio = persona.biography
                .replace("{{user}}", userName)
                .replace("{{char}}", character.name)
            parts.add("[User Persona: ${persona.name}]\n$bio")
        }

        // 系统 prompt
        val sysPrompt = character.systemPrompt
        if (!sysPrompt.isNullOrBlank()) {
            parts.add(sysPrompt.replace("{{user}}", userName).replace("{{char}}", character.name))
        }

        return parts.joinToString("\n\n")
    }

    private fun formatMemoryAtoms(atoms: List<MemoryAtomEntity>, charName: String): String {
        val parts = mutableListOf<String>()

        // Character consistency is ALWAYS injected (人设不能崩)
        val characterAtoms = atoms.filter { it.category == "character_consistency" }
        if (characterAtoms.isNotEmpty()) {
            val lines = characterAtoms.joinToString("\n") { "- ${it.content}" }
            parts.add("[${charName} 的核心人设 — 必须严格遵守]\n$lines")
        }

        // Commitments
        val commitmentAtoms = atoms.filter { it.category == "commitment" }
        if (commitmentAtoms.isNotEmpty()) {
            val lines = commitmentAtoms.joinToString("\n") { "- ${it.content}" }
            parts.add("[承诺与约定]\n$lines")
        }

        // User info
        val userAtoms = atoms.filter { it.category == "user_info" }
        if (userAtoms.isNotEmpty()) {
            val lines = userAtoms.joinToString("\n") { "- ${it.content}" }
            parts.add("[已知的用户信息]\n$lines")
        }

        // Relationships
        val relationAtoms = atoms.filter { it.category == "relationship" }
        if (relationAtoms.isNotEmpty()) {
            val lines = relationAtoms.joinToString("\n") { "- ${it.content}" }
            parts.add("[人物关系]\n$lines")
        }

        // Events
        val eventAtoms = atoms.filter { it.category == "event" }
        if (eventAtoms.isNotEmpty()) {
            val lines = eventAtoms.joinToString("\n") { "- ${it.content}" }
            parts.add("[重要事件]\n$lines")
        }

        return parts.joinToString("\n\n")
    }

    private fun parseExampleDialog(
        mesExample: String,
        userName: String,
        charName: String
    ): List<ChatMessage> {
        if (mesExample.isBlank()) return emptyList()

        val messages = mutableListOf<ChatMessage>()
        val blocks = mesExample.split("<START>")

        for (block in blocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty()) continue

            val lines = trimmed.lines()
            for (line in lines) {
                val l = line.trim()
                if (l.isEmpty()) continue

                val replaced = replacePlaceholders(l, userName, charName)
                when {
                    replaced.startsWith("${userName}:") ->
                        messages.add(ChatMessage(role = "user", content = replaced.removePrefix("${userName}:").trim()))
                    replaced.startsWith("${charName}:") ->
                        messages.add(ChatMessage(role = "assistant", content = replaced.removePrefix("${charName}:").trim()))
                }
            }
        }

        return messages
    }

    private fun replacePlaceholders(text: String, userName: String, charName: String): String {
        return text
            .replace("{{user}}", userName)
            .replace("{{char}}", charName)
    }
}
