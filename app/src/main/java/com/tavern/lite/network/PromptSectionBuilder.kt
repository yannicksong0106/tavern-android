package com.tavern.lite.network

import com.tavern.lite.data.db.entity.CharacterEntity
import com.tavern.lite.data.db.entity.MemoryAtomEntity
import com.tavern.lite.data.db.entity.MemoryEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import com.tavern.lite.data.db.entity.WorldBookEntryEntity

internal object PromptSectionBuilder {
    private const val MEMORY_CONTENT_LIMIT = 100
    private const val TEMP_CONTENT_LIMIT = 80
    private const val CORE_MEMORY_LIMIT = 5
    private const val TEMP_MEMORY_LIMIT = 3

    private val memoryCategories = listOf(
        "fact" to "已知的用户事实",
        "emotion" to "用户的情感状态",
        "preference" to "用户的偏好",
        "event" to "重要事件与约定",
        "habit" to "用户的习惯"
    )

    fun buildStaticSystemPrompt(character: CharacterEntity, userName: String): String {
        val parts = mutableListOf<String>()
        parts += """
            [回复风格 - 基础要求]
            你正在和用户进行即时聊天，像真人一样回复：
            - 每条消息只说 1-3 句话，不要长篇大论
            - 不同想法之间用空行分隔，系统会自动拆成独立消息
            - 语气自然口语化，像朋友聊天，不要像写文章
            - 偶尔用语气词增加真实感：嗯、哈哈、欸、哦、噢
            - 不要每次都用相同的句式开头，尽量变化表达
            - 可以使用省略号、感叹号、问号表达情绪
            - 回复长度随内容自然变化，闲聊时短一点，重要话题可以稍长
            - 模仿对面用户的说话风格和用词习惯
            - 有时候一两个字的回复也很自然：嗯、好、行、是啊、确实
            - 不需要每句都完整，口语化的省略和倒装很常见
        """.trimIndent()

        val desc = replacePlaceholders(character.description, userName, character.name, character)
        if (desc.isNotBlank()) parts.add(desc)

        val personality = replacePlaceholders(character.personality, userName, character.name, character)
        if (personality.isNotBlank()) parts.add("Personality: $personality")

        val sysPrompt = character.systemPrompt
        if (!sysPrompt.isNullOrBlank()) {
            parts.add(replacePlaceholders(sysPrompt, userName, character.name, character))
        }

        return parts.joinToString("\n\n")
    }

    fun buildGroupStaticSystemPrompt(
        characters: List<CharacterEntity>,
        respondingCharacter: CharacterEntity,
        userName: String
    ): String {
        val parts = mutableListOf<String>()
        parts += """
            [群聊回复风格]
            你正在参与一个群聊对话，群里有多个角色和一个用户。
            - 你是 ${respondingCharacter.name}，请严格保持这个角色的人设
            - 每条消息只说 1-3 句话，像真人在群里聊天
            - 你的回复格式必须是：[${respondingCharacter.name}]: 你的内容
            - 不要替其他角色说话，只扮演你自己
            - 可以对其他角色的话做出回应
            - 语气自然口语化，像朋友群聊
        """.trimIndent()

        val desc = replacePlaceholders(
            respondingCharacter.description,
            userName,
            respondingCharacter.name,
            respondingCharacter
        )
        if (desc.isNotBlank()) parts.add("[你的角色描述]\n$desc")

        val personality = replacePlaceholders(
            respondingCharacter.personality,
            userName,
            respondingCharacter.name,
            respondingCharacter
        )
        if (personality.isNotBlank()) parts.add("[你的性格]\n$personality")

        val otherChars = characters.filter { it.id != respondingCharacter.id }
        if (otherChars.isNotEmpty()) {
            val otherInfo = otherChars.joinToString("\n") { char ->
                val briefDesc = replacePlaceholders(char.description, userName, char.name, char).take(200)
                "- ${char.name}: $briefDesc"
            }
            parts.add("[群聊中的其他角色]\n$otherInfo")
        }

        val sysPrompt = respondingCharacter.systemPrompt
        if (!sysPrompt.isNullOrBlank()) {
            parts.add(replacePlaceholders(sysPrompt, userName, respondingCharacter.name, respondingCharacter))
        }

        return parts.joinToString("\n\n")
    }

    fun buildDynamicContext(
        character: CharacterEntity,
        worldBookEntries: List<WorldBookEntryEntity>,
        userName: String,
        memories: List<MemoryEntity> = emptyList(),
        memoryAtoms: List<MemoryAtomEntity> = emptyList(),
        persona: PersonaEntity? = null
    ): String {
        val parts = mutableListOf<String>()

        if (worldBookEntries.isNotEmpty()) {
            val worldInfo = worldBookEntries.joinToString("\n") { entry ->
                val comment = entry.comment.ifBlank { "World Info" }
                "[$comment]\n${entry.content}"
            }
            parts.add(worldInfo)
        }

        if (memoryAtoms.isNotEmpty()) {
            val atomText = formatMemoryAtoms(memoryAtoms, character.name)
            if (atomText.isNotBlank()) parts.add(atomText)
        } else if (memories.isNotEmpty()) {
            val memoryText = memories.joinToString("\n") { "- ${it.content}" }
            parts.add("[Memory]\n$memoryText")
        }

        if (persona != null && persona.biography.isNotBlank()) {
            val bio = replacePlaceholders(persona.biography, userName, character.name, character, persona)
            parts.add("[User Persona: ${persona.name}]\n$bio")
        }

        return parts.joinToString("\n\n")
    }

    fun buildDynamicContextSections(
        character: CharacterEntity,
        worldBookEntries: List<WorldBookEntryEntity>,
        userName: String,
        memories: List<MemoryEntity> = emptyList(),
        memoryAtoms: List<MemoryAtomEntity> = emptyList(),
        persona: PersonaEntity? = null
    ): List<PromptSection> {
        val sections = mutableListOf<PromptSection>()

        if (worldBookEntries.isNotEmpty()) {
            val worldInfo = worldBookEntries.joinToString("\n") { entry ->
                val comment = entry.comment.ifBlank { "World Info" }
                "[$comment]\n${entry.content}"
            }
            sections.add(PromptSection.create(PromptSource.WORLD_BOOK, worldInfo))
        }

        if (memoryAtoms.isNotEmpty()) {
            val grouped = memoryAtoms.groupBy { it.category }

            grouped["character_consistency"]?.let { list ->
                val text = "[$character.name 的核心人设 — 必须严格遵守]\n${formatMemoryLines(list, MEMORY_CONTENT_LIMIT)}"
                sections.add(PromptSection.create(PromptSource.CHARACTER_CONSISTENCY, text))
            }

            val otherCategories = memoryAtoms.filter { it.category != "character_consistency" }
            if (otherCategories.isNotEmpty()) {
                val atomText = formatMemoryAtoms(otherCategories, character.name)
                if (atomText.isNotBlank()) {
                    sections.add(PromptSection.create(PromptSource.MEMORY, atomText))
                }
            }
        } else if (memories.isNotEmpty()) {
            val memoryText = memories.joinToString("\n") { "- ${it.content}" }
            sections.add(PromptSection.create(PromptSource.MEMORY, "[Memory]\n$memoryText"))
        }

        if (persona != null && persona.biography.isNotBlank()) {
            val bio = replacePlaceholders(persona.biography, userName, character.name, character, persona)
            sections.add(PromptSection.create(PromptSource.PERSONA, "[User Persona: ${persona.name}]\n$bio"))
        }

        return sections
    }

    fun parseExampleDialog(mesExample: String, userName: String, charName: String): List<ChatMessage> {
        if (mesExample.isBlank()) return emptyList()

        val messages = mutableListOf<ChatMessage>()
        for (block in mesExample.split("<START>")) {
            for (line in block.trim().lines()) {
                val replaced = replacePlaceholders(line.trim(), userName, charName)
                when {
                    replaced.startsWith("$userName:") ->
                        messages.add(ChatMessage(role = "user", content = replaced.removePrefix("$userName:").trim()))
                    replaced.startsWith("$charName:") ->
                        messages.add(ChatMessage(role = "assistant", content = replaced.removePrefix("$charName:").trim()))
                }
            }
        }
        return messages
    }

    fun replacePlaceholders(
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

    private fun formatMemoryAtoms(atoms: List<MemoryAtomEntity>, charName: String): String {
        if (atoms.isEmpty()) return ""

        val grouped = atoms.groupBy { it.category }
        val parts = mutableListOf<String>()

        grouped["character_consistency"]?.let { list ->
            parts.add("[$charName 的核心人设 — 必须严格遵守]\n${formatMemoryLines(list, MEMORY_CONTENT_LIMIT)}")
        }

        for ((category, title) in memoryCategories) {
            grouped[category]?.let { list ->
                parts.add("[$title]\n${formatMemoryLines(list, MEMORY_CONTENT_LIMIT)}")
            }
        }

        grouped["temporary"]?.let { list ->
            parts.add("[当前对话上下文]\n${formatMemoryLines(list, TEMP_CONTENT_LIMIT, TEMP_MEMORY_LIMIT)}")
        }

        return parts.joinToString("\n\n")
    }

    private fun formatMemoryLines(
        atoms: List<MemoryAtomEntity>,
        contentLimit: Int,
        takeCount: Int = CORE_MEMORY_LIMIT
    ): String = atoms.sortedByDescending { it.importance }
        .take(takeCount)
        .joinToString("\n") { "- ${it.content.take(contentLimit)}" }
}
