package com.tavern.lite.ui.screens.preset

import com.tavern.lite.domain.port.TemplateRendererPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTemplatePreviewTest {

    private val templateRenderer = object : TemplateRendererPort {
        override fun render(template: String, variables: Map<String, Any?>): String {
            return Regex("\\{\\{\\s*([\\w.]+)\\s*}}").replace(template) { match ->
                resolveVariable(match.groupValues[1], variables)?.toString() ?: match.value
            }
        }

        override fun clearCache() = Unit

        private fun resolveVariable(path: String, variables: Map<String, Any?>): Any? {
            return path.split('.').fold(variables as Any?) { current, key ->
                (current as? Map<*, *>)?.get(key)
            }
        }
    }

    @Test
    fun `buildPresetTemplatePreview replaces sample variables`() {
        val preview = buildPresetTemplatePreview(
            systemPrompt = "{{char}} talks to {{user}}.",
            postHistoryInstructions = "Remember {{scenario}}",
            authorNote = "{{persona.name}} listens.",
            templateRenderer = templateRenderer
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
            templateRenderer = templateRenderer,
            variables = mapOf("char" to "Mira", "user" to "Traveler")
        )

        assertEquals("Mira/Traveler", preview.systemPrompt)
    }

    @Test
    fun `buildPresetTemplatePreview reports empty content`() {
        val preview = buildPresetTemplatePreview(
            systemPrompt = "",
            postHistoryInstructions = "  ",
            authorNote = "",
            templateRenderer = templateRenderer
        )

        assertFalse(preview.hasAnyContent)
    }
}
