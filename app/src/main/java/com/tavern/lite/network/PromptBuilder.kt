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
                content = replacePlaceholders(postHistory, effectiveUserName, character.name)
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
- 不要每次都用相同的句式开头，变换表达方式
- 可以用省略号、感叹号、问号表达情绪
- 回复长度随对话内容自然变化，闲聊时短一些，重要话题可以长一些
- 模仿对面用户的说话风格和用词习惯
- 有时候一两个字的回应也很自然（嗯、好、行、是啊、确实）
- 不需要每句都完整，口语化的省略和倒装很常见""".trimIndent())

        // 角色描述
        val desc = replacePlaceholders(character.description, userName, character.name)
        if (desc.isNotBlank()) parts.add(desc)

        // 性格
        val personality = replacePlaceholders(character.personality, userName, character.name)
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
            val bio = replacePlaceholders(persona.biography, userName, character.name)
            parts.add("[User Persona: ${persona.name}]\n$bio")
        }

        // 系统 prompt
        val sysPrompt = character.systemPrompt
        if (!sysPrompt.isNullOrBlank()) {
            parts.add(replacePlaceholders(sysPrompt, userName, character.name))
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

        // 其他分类：category -> 中文标题
        val categories = listOf(
            "commitment" to "承诺与约定",
            "user_info" to "已知的用户信息",
            "relationship" to "人物关系",
            "event" to "重要事件"
        )
        for ((category, title) in categories) {
            val filtered = atoms.filter { it.category == category }
            if (filtered.isNotEmpty()) {
                val lines = filtered.joinToString("\n") { "- ${it.content}" }
                parts.add("[$title]\n$lines")
            }
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

    /**
     * Build prompt for group chat with multiple characters.
     * The responding character's messages are "assistant", others are context.
     */
    fun buildGroupChat(
        characters: List<CharacterEntity>,
        respondingCharacter: CharacterEntity,
        userMessage: String,
        chatHistory: List<MessageEntity>,
        characterMap: Map<Long, CharacterEntity>,
        worldBookEntries: List<WorldBookEntryEntity> = emptyList(),
        userName: String = "User",
        memories: List<MemoryEntity> = emptyList(),
        memoryAtoms: List<MemoryAtomEntity> = emptyList(),
        persona: PersonaEntity? = null,
        authorNote: AuthorNoteEntity? = null
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        val effectiveUserName = persona?.name?.takeIf { it.isNotBlank() } ?: userName

        // 1. System prompt with group chat context
        val systemPrompt = buildGroupSystemPrompt(
            characters, respondingCharacter, worldBookEntries, effectiveUserName,
            memories, memoryAtoms, persona
        )
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }

        // 2. Example dialog for responding character
        val exampleMessages = parseExampleDialog(respondingCharacter.mesExample, effectiveUserName, respondingCharacter.name)
        messages.addAll(exampleMessages)

        // 3. Opening messages from all characters (firstMes)
        for (char in characters) {
            if (char.firstMes.isNotBlank()) {
                val firstMes = replacePlaceholders(char.firstMes, effectiveUserName, char.name)
                messages.add(ChatMessage(
                    role = "assistant",
                    content = "[${char.name}]: $firstMes"
                ))
            }
        }

        // 4. Chat history with character attribution
        chatHistory.forEach { msg ->
            when (msg.role) {
                "user" -> messages.add(ChatMessage(role = "user", content = msg.content))
                "assistant" -> {
                    val charName = msg.characterId?.let { characterMap[it]?.name }
                    val content = if (charName != null) "[$charName]: ${msg.content}" else msg.content
                    messages.add(ChatMessage(role = "assistant", content = content))
                }
                "system" -> messages.add(ChatMessage(role = "system", content = msg.content))
            }
        }

        // 4.5 Author's Note injection
        if (authorNote != null && authorNote.content.isNotBlank()) {
            val noteContent = replacePlaceholders(authorNote.content, effectiveUserName, respondingCharacter.name)
            val insertIndex = (messages.size - authorNote.depth).coerceAtLeast(1)
            messages.add(insertIndex, ChatMessage(role = "system", content = noteContent))
        }

        // 5. Current user message
        messages.add(ChatMessage(role = "user", content = userMessage))

        return messages
    }

    private fun buildGroupSystemPrompt(
        characters: List<CharacterEntity>,
        respondingCharacter: CharacterEntity,
        worldBookEntries: List<WorldBookEntryEntity>,
        userName: String,
        memories: List<MemoryEntity>,
        memoryAtoms: List<MemoryAtomEntity>,
        persona: PersonaEntity?
    ): String {
        val parts = mutableListOf<String>()

        // Group chat style guide
        parts.add("""[群聊回复风格]
你正在参与一个群聊对话，群里有多个角色和一个用户。
- 你是 ${respondingCharacter.name}，请严格保持这个角色的人设
- 每条消息只说 1-3 句话，像真人在群里聊天
- 你的回复格式必须是：[${respondingCharacter.name}]: 你的内容
- 不要替其他角色说话，只扮演你自己
- 可以对其他角色说的话做出回应
- 语气自然口语化，像朋友群聊""".trimIndent())

        // Responding character's full details (priority)
        val desc = replacePlaceholders(respondingCharacter.description, userName, respondingCharacter.name)
        if (desc.isNotBlank()) parts.add("[你的角色描述]\n$desc")

        val personality = replacePlaceholders(respondingCharacter.personality, userName, respondingCharacter.name)
        if (personality.isNotBlank()) parts.add("[你的性格]\n$personality")

        // Other characters' brief info
        val otherChars = characters.filter { it.id != respondingCharacter.id }
        if (otherChars.isNotEmpty()) {
            val otherInfo = otherChars.joinToString("\n") { char ->
                val briefDesc = replacePlaceholders(char.description, userName, char.name).take(200)
                "- ${char.name}: $briefDesc"
            }
            parts.add("[群聊中的其他角色]\n$otherInfo")
        }

        // World book entries
        if (worldBookEntries.isNotEmpty()) {
            val worldInfo = worldBookEntries.joinToString("\n") { entry ->
                val comment = entry.comment.ifBlank { "World Info" }
                "[$comment]\n${entry.content}"
            }
            parts.add(worldInfo)
        }

        // Memories
        if (memoryAtoms.isNotEmpty()) {
            val atomText = formatMemoryAtoms(memoryAtoms, respondingCharacter.name)
            if (atomText.isNotBlank()) parts.add(atomText)
        } else if (memories.isNotEmpty()) {
            val memoryText = memories.joinToString("\n") { "- ${it.content}" }
            parts.add("[Memory]\n$memoryText")
        }

        // User persona
        if (persona != null && persona.biography.isNotBlank()) {
            val bio = replacePlaceholders(persona.biography, userName, respondingCharacter.name)
            parts.add("[User Persona: ${persona.name}]\n$bio")
        }

        // Responding character's system prompt
        val sysPrompt = respondingCharacter.systemPrompt
        if (!sysPrompt.isNullOrBlank()) {
            parts.add(replacePlaceholders(sysPrompt, userName, respondingCharacter.name))
        }

        return parts.joinToString("\n\n")
    }

    /**
     * Build prompt for proactive single chat dialogue.
     * Used when the character wants to extend the conversation after a round ends.
     */
    fun buildProactive(
        character: CharacterEntity,
        chatHistory: List<MessageEntity>,
        userName: String = "User",
        persona: PersonaEntity? = null
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val effectiveUserName = persona?.name?.takeIf { it.isNotBlank() } ?: userName

        // 系统 prompt
        val systemPrompt = buildSystemPrompt(character, emptyList(), effectiveUserName, emptyList(), emptyList(), persona)
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }

        // 主动对话指令
        messages.add(ChatMessage(
            role = "system",
            content = """[主动对话指令]
对话刚刚结束，现在请你主动延伸话题，自然地继续聊天。
要求：
- 像真人一样自然搭话，不要生硬
- 可以：提问、分享感受、联想到相关话题、开玩笑、回忆之前的内容
- 不要：重复上一轮的内容、说"还有什么想聊的"这类话
- 内容简短自然（1-2 句话）
- 保持角色人设"""
        ))

        // 聊天历史
        chatHistory.forEach { msg ->
            val role = when (msg.role) {
                "user" -> "user"
                "assistant" -> "assistant"
                else -> "system"
            }
            messages.add(ChatMessage(role = role, content = msg.content))
        }

        // 添加一个空的 user 消息触发回复
        messages.add(ChatMessage(role = "user", content = "..."))

        return messages
    }

    /**
     * Build prompt for proactive group chat dialogue.
     * Used when a character wants to interject in a group chat.
     */
    fun buildGroupProactive(
        characters: List<CharacterEntity>,
        respondingCharacter: CharacterEntity,
        chatHistory: List<MessageEntity>,
        characterMap: Map<Long, CharacterEntity>,
        userName: String = "User",
        persona: PersonaEntity? = null
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val effectiveUserName = persona?.name?.takeIf { it.isNotBlank() } ?: userName

        // 群聊系统 prompt
        val systemPrompt = buildGroupSystemPrompt(
            characters, respondingCharacter, emptyList(), effectiveUserName,
            emptyList(), emptyList(), persona
        )
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }

        // 主动发言指令
        messages.add(ChatMessage(
            role = "system",
            content = """[主动发言指令]
群聊刚刚有对话，现在请你自然地插话。
要求：
- 像真人群聊一样自然接话、吐槽、提问、互动
- 不是每条消息都需要回复，选择你感兴趣的话题
- 内容简短（1-2 句话），像群聊风格
- 保持角色人设
- 可以回应其他角色说的话"""
        ))

        // 聊天历史
        chatHistory.forEach { msg ->
            when (msg.role) {
                "user" -> messages.add(ChatMessage(role = "user", content = msg.content))
                "assistant" -> {
                    val charName = msg.characterId?.let { characterMap[it]?.name }
                    val content = if (charName != null) "[$charName]: ${msg.content}" else msg.content
                    messages.add(ChatMessage(role = "assistant", content = content))
                }
                else -> messages.add(ChatMessage(role = "system", content = msg.content))
            }
        }

        // 添加一个空的 user 消息触发回复
        messages.add(ChatMessage(role = "user", content = "..."))

        return messages
    }
}
