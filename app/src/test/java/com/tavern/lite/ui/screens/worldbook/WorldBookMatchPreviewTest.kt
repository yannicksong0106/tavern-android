package com.tavern.lite.ui.screens.worldbook

import com.tavern.lite.data.db.entity.WorldBookEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookMatchPreviewTest {

    @Test
    fun `buildKeywordMatchPreview highlights matched primary keys`() {
        val preview = buildKeywordMatchPreview(
            entry = entry(keys = """["dragon","wyrm"]"""),
            previewText = "A DRAGON appears."
        )

        assertTrue(preview.triggered)
        assertEquals(setOf("dragon"), preview.matchedPrimaryKeys)
        assertTrue(preview.matchedSecondaryKeys.isEmpty())
    }

    @Test
    fun `buildKeywordMatchPreview triggers constant entries without text match`() {
        val preview = buildKeywordMatchPreview(
            entry = entry(keys = """["dragon"]""", constant = true),
            previewText = "quiet tavern"
        )

        assertTrue(preview.triggered)
        assertTrue(preview.matchedPrimaryKeys.isEmpty())
    }

    @Test
    fun `buildKeywordMatchPreview selective AND requires primary and secondary`() {
        val worldEntry = entry(
            keys = """["dragon"]""",
            keysSecondary = """["fire"]""",
            selective = true,
            selectiveLogic = 0
        )

        assertFalse(buildKeywordMatchPreview(worldEntry, "dragon only").triggered)
        assertTrue(buildKeywordMatchPreview(worldEntry, "dragon fire").triggered)
    }

    @Test
    fun `buildKeywordMatchPreview selective OR accepts secondary only`() {
        val preview = buildKeywordMatchPreview(
            entry = entry(
                keys = """["dragon"]""",
                keysSecondary = """["fire"]""",
                selective = true,
                selectiveLogic = 1
            ),
            previewText = "fire"
        )

        assertTrue(preview.triggered)
        assertEquals(setOf("fire"), preview.matchedSecondaryKeys)
    }

    @Test
    fun `buildKeywordMatchPreview selective NOT excludes secondary matches`() {
        val worldEntry = entry(
            keys = """["dragon"]""",
            keysSecondary = """["fire"]""",
            selective = true,
            selectiveLogic = 2
        )

        assertTrue(buildKeywordMatchPreview(worldEntry, "dragon flies").triggered)
        assertFalse(buildKeywordMatchPreview(worldEntry, "dragon fire").triggered)
    }

    @Test
    fun `buildKeywordMatchPreview tolerates invalid keyword JSON`() {
        val preview = buildKeywordMatchPreview(
            entry = entry(keys = "not-json", keysSecondary = "also-bad"),
            previewText = "dragon"
        )

        assertFalse(preview.triggered)
        assertTrue(preview.primaryKeys.isEmpty())
        assertTrue(preview.secondaryKeys.isEmpty())
    }

    private fun entry(
        keys: String = "[]",
        keysSecondary: String = "[]",
        constant: Boolean = false,
        selective: Boolean = false,
        selectiveLogic: Int = 0,
    ) = WorldBookEntryEntity(
        worldBookId = 1,
        keys = keys,
        keysSecondary = keysSecondary,
        constant = constant,
        selective = selective,
        selectiveLogic = selectiveLogic
    )
}
