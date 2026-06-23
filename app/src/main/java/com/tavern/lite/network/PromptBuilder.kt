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
            staticPromptCache.getOrPut(key) { PromptSectionBuilder.buildStaticSystemPrompt(character, userName) }
        }
    }

    fun invalidateCache() {
        staticPromptCache.clear()
    }

    /**
     * 构建带 section 追踪的 prompt
     * 返回 Pair<List<ChatMessage>, List<PromptSection>>
     * 第一个是消息列表，第二个是 section 列表（用于追踪来源和 token 估算）
     */
    fun buildWithSections(config: PromptConfig): Pair<List<ChatMessage>, List<PromptSection>> {
        val messages = mutableListOf<ChatMessage>()
        val sections = mutableListOf<PromptSection>()
        val effectiveUserName = config.effectiveUserName
        val respondingCharacter = if (config.isGroupChat) config.character else config.character

        // 1. 静态系统 prompt
        val staticPrompt = if (config.isGroupChat) {
            PromptSectionBuilder.buildGroupStaticSystemPrompt(config.characters, respondingCharacter, effectiveUserName)
        } else {
            getCachedStaticPrompt(respondingCharacter, effectiveUserName)
        }
        val presetSysPrompt = config.preset?.systemPrompt?.takeIf { it.isNotBlank() }
        val combinedStatic = listOfNotNull(presetSysPrompt, staticPrompt.takeIf { it.isNotBlank() })
            .joinToString("\n\n")
        if (combinedStatic.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = combinedStatic))
            sections.add(PromptSection.create(PromptSource.SYSTEM, combinedStatic))
        }

        // 2. 示例对话
        val exampleMessages = PromptSectionBuilder.parseExampleDialog(respondingCharacter.mesExample, effectiveUserName, respondingCharacter.name)
        if (exampleMessages.isNotEmpty()) {
            messages.addAll(exampleMessages)
            val exampleText = exampleMessages.joinToString("\n") { it.content }
            sections.add(PromptSection.create(PromptSource.EXAMPLE_DIALOG, exampleText))
        }

        // 3. 开场白
        if (config.isGroupChat) {
            for (char in config.characters) {
                if (char.firstMes.isNotBlank()) {
                    val firstMes = PromptSectionBuilder.replacePlaceholders(char.firstMes, effectiveUserName, char.name, char, config.persona)
                    messages.add(ChatMessage(role = "assistant", content = "[${char.name}]: $firstMes"))
                }
            }
        } else if (respondingCharacter.firstMes.isNotBlank()) {
            val firstMes = PromptSectionBuilder.replacePlaceholders(respondingCharacter.firstMes, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
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
        if (config.chatHistory.isNotEmpty()) {
            sections.add(PromptSection.create(PromptSource.CHAT_HISTORY, "[Chat History]"))
        }

        // 4.5 摘要注入
        if (!config.summary.isNullOrBlank()) {
            messages.add(ChatMessage(
                role = "system",
                content = "[Summary — 以下是之前的对话摘要，请基于此继续对话]\n${config.summary}"
            ))
            sections.add(PromptSection.create(PromptSource.SUMMARY, "[Summary] ${config.summary}"))
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
            sections.add(PromptSection.create(PromptSource.SEARCH, "[Web Search Results] $searchText"))
        }

        // 5. 动态上下文（世界书 + 记忆 + 人格）
        val dynamicSections = PromptSectionBuilder.buildDynamicContextSections(respondingCharacter, config.worldBookEntries, effectiveUserName, config.memories, config.memoryAtoms, config.persona)
        for (section in dynamicSections) {
            if (section.content.isNotBlank()) {
                messages.add(ChatMessage(role = "system", content = section.content))
                sections.add(section)
            }
        }

        // 5.5 Author's Note 注入
        if (config.authorNote != null && config.authorNote.content.isNotBlank()) {
            val noteContent = PromptSectionBuilder.replacePlaceholders(config.authorNote.content, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
            val insertIndex = (messages.size - config.authorNote.depth).coerceAtLeast(1)
            messages.add(insertIndex, ChatMessage(role = "system", content = noteContent))
            sections.add(PromptSection.create(PromptSource.AUTHOR_NOTE, noteContent))
        }

        // 5.5.1 预设 Author Note
        val presetAuthorNote = config.preset?.authorNote?.takeIf { it.isNotBlank() }
        if (presetAuthorNote != null) {
            val noteContent = PromptSectionBuilder.replacePlaceholders(presetAuthorNote, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
            val insertIndex = (messages.size - 1).coerceAtLeast(1)
            messages.add(insertIndex, ChatMessage(role = "system", content = noteContent))
            sections.add(PromptSection.create(PromptSource.AUTHOR_NOTE, "[Preset Author Note] $noteContent"))
        }

        // 5.6 历史后指令（仅单聊）
        if (!config.isGroupChat) {
            val postHistory = config.preset?.postHistoryInstructions?.takeIf { it.isNotBlank() }
                ?: respondingCharacter.postHistoryInstructions
            if (!postHistory.isNullOrBlank()) {
                val replacedPostHistory = PromptSectionBuilder.replacePlaceholders(postHistory, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
                messages.add(ChatMessage(role = "system", content = replacedPostHistory))
                sections.add(PromptSection.create(PromptSource.PRESET, "[Post History Instructions] $replacedPostHistory"))
            }
        }

        return Pair(messages, sections)
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
            PromptSectionBuilder.buildGroupStaticSystemPrompt(config.characters, respondingCharacter, effectiveUserName)
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
        val exampleMessages = PromptSectionBuilder.parseExampleDialog(respondingCharacter.mesExample, effectiveUserName, respondingCharacter.name)
        messages.addAll(exampleMessages)

        // 3. 开场白
        if (config.isGroupChat) {
            for (char in config.characters) {
                if (char.firstMes.isNotBlank()) {
                    val firstMes = PromptSectionBuilder.replacePlaceholders(char.firstMes, effectiveUserName, char.name, char, config.persona)
                    messages.add(ChatMessage(role = "assistant", content = "[${char.name}]: $firstMes"))
                }
            }
        } else if (respondingCharacter.firstMes.isNotBlank()) {
            val firstMes = PromptSectionBuilder.replacePlaceholders(respondingCharacter.firstMes, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
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
        val dynamicContext = PromptSectionBuilder.buildDynamicContext(respondingCharacter, config.worldBookEntries, effectiveUserName, config.memories, config.memoryAtoms, config.persona)
        if (dynamicContext.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = dynamicContext))
        }

        // 5.5 Author's Note 注入
        if (config.authorNote != null && config.authorNote.content.isNotBlank()) {
            val noteContent = PromptSectionBuilder.replacePlaceholders(config.authorNote.content, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
            val insertIndex = (messages.size - config.authorNote.depth).coerceAtLeast(1)
            messages.add(insertIndex, ChatMessage(role = "system", content = noteContent))
        }

        // 5.5.1 预设 Author Note
        val presetAuthorNote = config.preset?.authorNote?.takeIf { it.isNotBlank() }
        if (presetAuthorNote != null) {
            val noteContent = PromptSectionBuilder.replacePlaceholders(presetAuthorNote, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
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
                    content = PromptSectionBuilder.replacePlaceholders(postHistory, effectiveUserName, respondingCharacter.name, respondingCharacter, config.persona)
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
