package com.tavern.lite.network

import com.tavern.lite.data.db.entity.AuthorNoteEntity
import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.MessageEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.PresetEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity

object PromptBuilder {

    // 静态 prompt 缓存：key = "characterId_userName_descHash"，避免每条消息重复构建 (LRU, max 16)
    private val staticPromptCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 16
    }

    private fun getStaticPromptCacheKey(character: CharacterEntity, userName: String): String {
        val descHash = (character.description.hashCode() * 31 + character.personality.hashCode()) * 31 +
            (character.systemPrompt?.hashCode() ?: 0)
        return "${character.id}_${userName}_$descHash"
    }

    private fun getCachedStaticPrompt(character: CharacterEntity, userName: String): String {
        val key = getStaticPromptCacheKey(character, userName)
        return synchronized(staticPromptCache) {
            staticPromptCache.getOrPut(key) { buildStaticSystemPrompt(character, userName) }
        }
    }

    fun invalidateCache() {
        staticPromptCache.clear()
    }

    /**
     * 核心 prompt 构建逻辑，统一处理单聊和群聊的公共部分
     */
    private fun buildCore(config: PromptConfig): MutableList<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val effectiveUserName = config.effectiveUserName
        val respondingCharacter = if (config.isGroupChat) config.character else config.character

        // 1. 静态系统 prompt
        val staticPrompt = if (config.isGroupChat) {
            buildGroupStaticSystemPrompt(config.characters, respondingCharacter, effectiveUserName)
        } else {
            getCachedStaticPrompt(respondingCharacter, effectiveUserName)
        }
        val presetSysPrompt = config.preset?.systemPrompt?.takeIf { it.isNotBlank() }
        val combinedStatic = listOfNotNull(presetSysPrompt, staticPrompt.takeIf { it.isNotBlank() })
            .joinToString("\n\n")
        if (combinedStatic.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = combinedStatic))
        }

        // 2. 示例对话
        val exampleMessages = parseExampleDialog(respondingCharacter.mesExample, effectiveUserName, respondingCharacter.name)
        messages.addAll(exampleMessages)

        // 3. 开场白
        if (config.isGroupChat) {
            for (char in config.characters) {
                if (char.firstMes.isNotBlank()) {
                    val firstMes = replacePlaceholders(char.firstMes, effectiveUserName, char.name, char, config.persona)
                    messages.add(ChatMessage(role = "assistant", content = "[${char.name}]: $firstMes"))
                }
            }
        } else if (respondingCharacter.firstMes.isNotBlank()) {
            val firstMes = replacePlaceholders(respondingCharacter.firstMes, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
            messages.add(ChatMessage(role = "assistant", content = firstMes))
        }

        // 4. 聊天历史
        config.chatHistory.forEach { msg ->
            when {
                config.isGroupChat && msg.role == "assistant" -> {
                    val charName = msg.characterId?.let { config.characterMap[it]?.name }
                    val content = if (charName != null) "[$charName]: ${msg.content}" else msg.content
                    messages.add(ChatMessage(role = "assistant", content = content))
                }
                else -> {
                    val role = when (msg.role) {
                        "user" -> "user"
                        "assistant" -> "assistant"
                        else -> "system"
                    }
                    messages.add(ChatMessage(role = role, content = msg.content))
                }
            }
        }

        // 4.5 摘要注入
        if (!config.summary.isNullOrBlank()) {
            messages.add(ChatMessage(
                role = "system",
                content = "[对话摘要 — 以下是之前对话的要点总结]\n${config.summary}"
            ))
        }

        // 4.6 搜索结果注入
        if (config.searchResults.isNotEmpty()) {
            val searchText = config.searchResults.joinToString("\n\n") { result ->
                "标题: ${result.title}\n摘要: ${result.snippet}\n来源: ${result.url}"
            }
            messages.add(ChatMessage(
                role = "system",
                content = "[Web Search Results — 以下是网络搜索结果，请基于这些信息回答用户问题]\n$searchText"
            ))
        }

        // 5. 动态上下文（世界书 + 记忆 + 人格）
        val dynamicContext = buildDynamicContext(respondingCharacter, config.worldBookEntries, effectiveUserName, config.memories, config.memoryAtoms, config.persona)
        if (dynamicContext.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = dynamicContext))
        }

        // 5.5 Author's Note 注入
        if (config.authorNote != null && config.authorNote.content.isNotBlank()) {
            val noteContent = replacePlaceholders(config.authorNote.content, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
            val insertIndex = (messages.size - config.authorNote.depth).coerceAtLeast(1)
            messages.add(insertIndex, ChatMessage(role = "system", content = noteContent))
        }

        // 5.5.1 预设 Author Note
        val presetAuthorNote = config.preset?.authorNote?.takeIf { it.isNotBlank() }
        if (presetAuthorNote != null) {
            val noteContent = replacePlaceholders(presetAuthorNote, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
            val insertIndex = (messages.size - 1).coerceAtLeast(1)
            messages.add(insertIndex, ChatMessage(role = "system", content = noteContent))
        }

        // 5.6 历史后指令（仅单聊）
        if (!config.isGroupChat) {
            val postHistory = config.preset?.postHistoryInstructions?.takeIf { it.isNotBlank() }
                ?: respondingCharacter.postHistoryInstructions
            if (!postHistory.isNullOrBlank()) {
                messages.add(ChatMessage(
                    role = "system",
                    content = replacePlaceholders(postHistory, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
                ))
            }
        }

        return messages
    }

    fun build(
        character: CharacterEntity,
        userMessage: String,
        chatHistory: List<MessageEntity>,
        worldBookEntries: List<WorldBookEntryEntity> = emptyList(),
        userName: String = "User",
        memories: List<MemoryEntity> = emptyList(),
        memoryAtoms: List<MemoryAtomEntity> = emptyList(),
        authorNote: AuthorNoteEntity? = null,
        persona: PersonaEntity? = null,
        preset: PresetEntity? = null,
        imageUrls: List<String> = emptyList(),
        summary: String? = null,
        searchResults: List<WebSearchResult> = emptyList()
    ): List<ChatMessage> {
        val config = PromptConfig(
            character = character,
            userMessage = userMessage,
            chatHistory = chatHistory,
            worldBookEntries = worldBookEntries,
            userName = userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona,
            preset = preset,
            imageUrls = imageUrls,
            summary = summary,
            searchResults = searchResults
        )
        val messages = buildCore(config)
        // 当前用户消息（支持 multimodal 图片附件）
        messages.add(ChatMessage(role = "user", content = userMessage, imageUrls = imageUrls))
        return messages
    }

    /**
     * 构建静态系统 prompt（回复风格 + 角色描述 + 性格 + 角色系统 prompt）。
     * 这些内容在同一角色的连续对话中几乎不变，放在消息数组最前面可最大化 API 缓存命中率。
     */
    private fun buildStaticSystemPrompt(
        character: CharacterEntity,
        userName: String
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
        val desc = replacePlaceholders(character.description, userName, character.name, character)
        if (desc.isNotBlank()) parts.add(desc)

        // 性格
        val personality = replacePlaceholders(character.personality, userName, character.name, character)
        if (personality.isNotBlank()) parts.add("Personality: $personality")

        // 角色系统 prompt
        val sysPrompt = character.systemPrompt
        if (!sysPrompt.isNullOrBlank()) {
            parts.add(replacePlaceholders(sysPrompt, userName, character.name, character))
        }

        return parts.joinToString("\n\n")
    }

    /**
     * 构建动态上下文（世界书 + 记忆 + 用户人格）。
     * 这些内容每轮对话可能变化（世界书按关键词匹配、记忆每 10 条提取一次），
     * 放在聊天历史之后，避免破坏缓存前缀。
     */
    private fun buildDynamicContext(
        character: CharacterEntity,
        worldBookEntries: List<WorldBookEntryEntity>,
        userName: String,
        memories: List<MemoryEntity> = emptyList(),
        memoryAtoms: List<MemoryAtomEntity> = emptyList(),
        persona: PersonaEntity? = null
    ): String {
        val parts = mutableListOf<String>()

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
            val bio = replacePlaceholders(persona.biography, userName, character.name, character, persona)
            parts.add("[User Persona: ${persona.name}]\n$bio")
        }

        return parts.joinToString("\n\n")
    }

    private const val MEMORY_CONTENT_LIMIT = 100
    private const val TEMP_CONTENT_LIMIT = 80
    private const val CORE_MEMORY_LIMIT = 5
    private const val TEMP_MEMORY_LIMIT = 3

    private val MEMORY_CATEGORIES = listOf(
        "fact" to "已知的用户事实",
        "emotion" to "用户的情感状态",
        "preference" to "用户的偏好",
        "event" to "重要事件与约定",
        "habit" to "用户的习惯"
    )

    private fun formatMemoryAtoms(atoms: List<MemoryAtomEntity>, charName: String): String {
        if (atoms.isEmpty()) return ""

        // 单次遍历分组，避免多次 filter 扫描
        val grouped = atoms.groupBy { it.category }
        val parts = mutableListOf<String>()

        // 角色核心人设 — 最高优先级
        grouped["character_consistency"]?.let { list ->
            val lines = list.sortedByDescending { it.importance }
                .take(CORE_MEMORY_LIMIT)
                .joinToString("\n") { "- ${it.content.take(MEMORY_CONTENT_LIMIT)}" }
            parts.add("[$charName 的核心人设 — 必须严格遵守]\n$lines")
        }

        // 核心记忆分类
        for ((category, title) in MEMORY_CATEGORIES) {
            grouped[category]?.let { list ->
                val lines = list.sortedByDescending { it.importance }
                    .take(CORE_MEMORY_LIMIT)
                    .joinToString("\n") { "- ${it.content.take(MEMORY_CONTENT_LIMIT)}" }
                parts.add("[$title]\n$lines")
            }
        }

        // 临时记忆最后注入
        grouped["temporary"]?.let { list ->
            val lines = list.sortedByDescending { it.importance }
                .take(TEMP_MEMORY_LIMIT)
                .joinToString("\n") { "- ${it.content.take(TEMP_CONTENT_LIMIT)}" }
            parts.add("[当前对话上下文]\n$lines")
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

    /**
     * Replace template variables using Handlebars engine.
     * Supports {{user}}, {{char}}, {{persona}}, {{description}}, {{personality}}, etc.
     * Also supports {{#if ...}}, {{#each ...}}, {{#unless ...}} conditional blocks.
     */
    private fun replacePlaceholders(
        text: String,
        userName: String,
        charName: String,
        character: CharacterEntity? = null,
        persona: PersonaEntity? = null
    ): String {
        val variables = mutableMapOf<String, Any?>(
            "user" to userName,
            "char" to charName
        )
        character?.let {
            variables["description"] = it.description
            variables["personality"] = it.personality
            variables["firstMessage"] = it.firstMes
            variables["mesExamples"] = it.mesExample
        }
        persona?.let {
            variables["persona"] = it.name
            variables["personaDescription"] = it.biography
        }
        return TemplateEngine.render(text, variables)
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
        authorNote: AuthorNoteEntity? = null,
        preset: PresetEntity? = null,
        imageUrls: List<String> = emptyList(),
        summary: String? = null,
        searchResults: List<WebSearchResult> = emptyList()
    ): List<ChatMessage> {
        val config = PromptConfig(
            character = respondingCharacter,
            userMessage = userMessage,
            chatHistory = chatHistory,
            worldBookEntries = worldBookEntries,
            userName = userName,
            memories = memories,
            memoryAtoms = memoryAtoms,
            authorNote = authorNote,
            persona = persona,
            preset = preset,
            imageUrls = imageUrls,
            summary = summary,
            searchResults = searchResults,
            characters = characters,
            characterMap = characterMap,
            isGroupChat = true
        )
        val messages = buildCore(config)
        // 当前用户消息
        messages.add(ChatMessage(role = "user", content = userMessage, imageUrls = imageUrls))
        return messages
    }

    /**
     * 构建群聊静态系统 prompt（群聊风格 + 角色描述 + 性格 + 其他角色简介）。
     */
    private fun buildGroupStaticSystemPrompt(
        characters: List<CharacterEntity>,
        respondingCharacter: CharacterEntity,
        userName: String
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
        val desc = replacePlaceholders(respondingCharacter.description, userName, respondingCharacter.name, respondingCharacter)
        if (desc.isNotBlank()) parts.add("[你的角色描述]\n$desc")

        val personality = replacePlaceholders(respondingCharacter.personality, userName, respondingCharacter.name, respondingCharacter)
        if (personality.isNotBlank()) parts.add("[你的性格]\n$personality")

        // Other characters' brief info
        val otherChars = characters.filter { it.id != respondingCharacter.id }
        if (otherChars.isNotEmpty()) {
            val otherInfo = otherChars.joinToString("\n") { char ->
                val briefDesc = replacePlaceholders(char.description, userName, char.name, char).take(200)
                "- ${char.name}: $briefDesc"
            }
            parts.add("[群聊中的其他角色]\n$otherInfo")
        }

        // Responding character's system prompt
        val sysPrompt = respondingCharacter.systemPrompt
        if (!sysPrompt.isNullOrBlank()) {
            parts.add(replacePlaceholders(sysPrompt, userName, respondingCharacter.name, respondingCharacter))
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
        persona: PersonaEntity? = null,
        preset: PresetEntity? = null,
        summary: String? = null
    ): List<ChatMessage> {
        val config = PromptConfig(
            character = character,
            chatHistory = chatHistory,
            userName = userName,
            persona = persona,
            preset = preset,
            summary = summary,
            isProactive = true
        )
        val messages = buildCore(config)

        // 主动对话指令（插入到历史之后、用户消息之前）
        val proactiveIndex = messages.size
        messages.add(proactiveIndex, ChatMessage(
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
        persona: PersonaEntity? = null,
        preset: PresetEntity? = null,
        summary: String? = null
    ): List<ChatMessage> {
        val config = PromptConfig(
            character = respondingCharacter,
            chatHistory = chatHistory,
            userName = userName,
            persona = persona,
            preset = preset,
            summary = summary,
            characters = characters,
            characterMap = characterMap,
            isGroupChat = true,
            isProactive = true
        )
        val messages = buildCore(config)

        // 主动发言指令
        val proactiveIndex = messages.size
        messages.add(proactiveIndex, ChatMessage(
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

        // 添加一个空的 user 消息触发回复
        messages.add(ChatMessage(role = "user", content = "..."))

        return messages
    }
}
