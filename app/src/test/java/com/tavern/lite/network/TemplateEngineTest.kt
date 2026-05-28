package com.tavern.lite.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEngineTest {

    @Test
    fun `render replaces simple variables`() {
        val result = TemplateEngine.render(
            "Hello {{user}}, I am {{char}}",
            mapOf("user" to "Bob", "char" to "Alice")
        )
        assertEquals("Hello Bob, I am Alice", result)
    }

    @Test
    fun `render returns plain text when no template syntax`() {
        val plain = "No templates here"
        assertEquals(plain, TemplateEngine.render(plain, emptyMap()))
    }

    @Test
    fun `render handles blank template`() {
        assertEquals("", TemplateEngine.render("", emptyMap()))
    }

    @Test
    fun `render supports if block for non-empty value`() {
        val result = TemplateEngine.render(
            "{{#if persona}}Persona: {{persona}}{{/if}}",
            mapOf("persona" to "Alex")
        )
        assertEquals("Persona: Alex", result)
    }

    @Test
    fun `render omits if block for empty string`() {
        val result = TemplateEngine.render(
            "{{#if persona}}Persona: {{persona}}{{/if}}",
            mapOf("persona" to "")
        )
        assertEquals("", result)
    }

    @Test
    fun `render omits if block for null value`() {
        val result = TemplateEngine.render(
            "{{#if persona}}Has persona{{/if}}",
            mapOf("persona" to null)
        )
        assertEquals("", result)
    }

    @Test
    fun `render supports unless block`() {
        val result = TemplateEngine.render(
            "{{#unless persona}}No persona set{{/unless}}",
            mapOf("persona" to "")
        )
        assertEquals("No persona set", result)
    }

    @Test
    fun `render supports each block`() {
        val result = TemplateEngine.render(
            "{{#each items}}- {{this}}\n{{/each}}",
            mapOf("items" to listOf("apple", "banana"))
        )
        assertTrue(result.contains("- apple"))
        assertTrue(result.contains("- banana"))
    }

    @Test
    fun `render falls back to simple replacement on parse error`() {
        // Malformed template should not crash, just do simple replacement
        val result = TemplateEngine.render(
            "{{user}} says {{invalid",
            mapOf("user" to "Bob")
        )
        assertTrue(result.contains("Bob"))
    }

    @Test
    fun `render handles multiple variables in one template`() {
        val result = TemplateEngine.render(
            "{{user}} talks to {{char}} about {{description}}",
            mapOf("user" to "Bob", "char" to "Alice", "description" to "dragons")
        )
        assertEquals("Bob talks to Alice about dragons", result)
    }

    @Test
    fun `clearCache does not throw`() {
        TemplateEngine.clearCache()
        // Should still work after cache clear
        val result = TemplateEngine.render("{{user}}", mapOf("user" to "Test"))
        assertEquals("Test", result)
    }
}
