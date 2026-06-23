package com.tavern.lite.network

import com.tavern.lite.domain.model.PromptConfig
import com.tavern.lite.domain.port.PromptBuilderPort
import com.tavern.lite.domain.port.PromptBuildResult
import com.tavern.lite.domain.port.PromptSectionInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PromptBuilder 适配器 — 将 domain 层 PromptBuilderPort 调用委托给 network 层 PromptBuilder object
 */
@Singleton
class PromptBuilderAdapter @Inject constructor() : PromptBuilderPort {

    override fun build(config: PromptConfig): List<com.tavern.lite.domain.model.ChatMessage> {
        return PromptBuilder.build(
            character = config.character,
            userMessage = config.userMessage,
            chatHistory = config.chatHistory,
            worldBookEntries = config.worldBookEntries,
            userName = config.userName,
            memories = config.memories,
            memoryAtoms = config.memoryAtoms,
            authorNote = config.authorNote,
            persona = config.persona,
            preset = config.preset,
            imageUrls = config.imageUrls,
            summary = config.summary,
            searchResults = config.searchResults
        )
    }

    override fun buildWithSections(config: PromptConfig): PromptBuildResult {
        val (messages, sections) = PromptBuilder.buildWithSections(config)
        return PromptBuildResult(
            messages = messages,
            sections = sections.map { section ->
                PromptSectionInfo(
                    source = section.source,
                    content = section.content,
                    tokenEstimate = section.tokenEstimate,
                    priority = section.priority
                )
            }
        )
    }

    override fun buildGroupChat(config: PromptConfig): List<com.tavern.lite.domain.model.ChatMessage> {
        return PromptBuilder.buildGroupChat(
            characters = config.characters,
            respondingCharacter = config.character,
            userMessage = config.userMessage,
            chatHistory = config.chatHistory,
            characterMap = config.characterMap,
            worldBookEntries = config.worldBookEntries,
            userName = config.userName,
            memories = config.memories,
            memoryAtoms = config.memoryAtoms,
            persona = config.persona,
            authorNote = config.authorNote,
            preset = config.preset,
            imageUrls = config.imageUrls,
            summary = config.summary,
            searchResults = config.searchResults
        )
    }

    override fun buildProactive(config: PromptConfig): List<com.tavern.lite.domain.model.ChatMessage> {
        return PromptBuilder.buildProactive(
            character = config.character,
            chatHistory = config.chatHistory,
            userName = config.userName,
            persona = config.persona,
            preset = config.preset,
            summary = config.summary
        )
    }

    override fun buildGroupProactive(config: PromptConfig): List<com.tavern.lite.domain.model.ChatMessage> {
        return PromptBuilder.buildGroupProactive(
            characters = config.characters,
            respondingCharacter = config.character,
            chatHistory = config.chatHistory,
            characterMap = config.characterMap,
            userName = config.userName,
            persona = config.persona,
            preset = config.preset,
            summary = config.summary
        )
    }

    override fun invalidateCache() {
        PromptBuilder.invalidateCache()
    }
}
