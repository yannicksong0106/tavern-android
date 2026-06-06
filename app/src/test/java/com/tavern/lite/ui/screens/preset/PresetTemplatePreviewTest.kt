package com.tavern.lite.ui.screens.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTemplatePreviewTest {

    @Test
    fun `buildPresetTemplatePreview replaces sample variables`() {
        val preview = buildPresetTemplatePreview(
            systemPrompt = "{{char}} talks to {{user}}.",
            postHistoryInstructions = "Remember {{scenario}}",
            authorNote = "{{persona.name}} listens."
        )

        assertEquals("Alice talks to You.", preview.systemPrompt)
        assertEquals("Remember A quiet tavern at dusk.", preview.postHistoryInstructions)
        assertEquals("You listens.", preview.authorNote)
        assertTrue(preview.hasAnyContent)
    }

    @Test
    fun `buildPresetTemplatePreview supports custom variables`() {
        val preview = buildPresetTemplatePreview(
            systemPrompt = "{{char}}/{{user}}",
            postHistoryInstructions = "",
            authorNote = "",
            variables = mapOf("char" to "Mira", "user" to "Traveler")
        )

        assertEquals("Mira/Traveler", preview.systemPrompt)
    }

    @Test
    fun `buildPresetTemplatePreview reports empty content`() {
        val preview = buildPresetTemplatePreview(
            systemPrompt = "",
            postHistoryInstructions = "  ",
            authorNote = ""
        )

        assertFalse(preview.hasAnyContent)
    }
}
