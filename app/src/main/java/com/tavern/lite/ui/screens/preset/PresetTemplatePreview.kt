package com.tavern.lite.ui.screens.preset

import com.tavern.lite.network.TemplateEngine

data class PresetTemplatePreview(
    val systemPrompt: String,
    val postHistoryInstructions: String,
    val authorNote: String,
) {
    val hasAnyContent: Boolean
        get() = systemPrompt.isNotBlank() ||
            postHistoryInstructions.isNotBlank() ||
            authorNote.isNotBlank()
}

fun buildPresetTemplatePreview(
    systemPrompt: String,
    postHistoryInstructions: String,
    authorNote: String,
    variables: Map<String, Any?> = samplePresetVariables,
): PresetTemplatePreview {
    return PresetTemplatePreview(
        systemPrompt = TemplateEngine.render(systemPrompt, variables),
        postHistoryInstructions = TemplateEngine.render(postHistoryInstructions, variables),
        authorNote = TemplateEngine.render(authorNote, variables)
    )
}

val samplePresetVariables: Map<String, Any?> = mapOf(
    "char" to "Alice",
    "user" to "You",
    "description" to "A warm and curious companion.",
    "personality" to "kind, playful, and observant",
    "scenario" to "A quiet tavern at dusk.",
    "mesExamples" to "Alice: Welcome back.\\nYou: Good to see you.",
    "system" to "",
    "persona" to mapOf(
        "name" to "You",
        "description" to "A traveler with a notebook."
    )
)
