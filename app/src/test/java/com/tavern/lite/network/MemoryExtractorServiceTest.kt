package com.tavern.lite.network

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryExtractorServiceTest {

    private lateinit var service: MemoryExtractorService

    @Before
    fun setup() {
        service = MemoryExtractorService(OkHttpClient())
    }

    @Test
    fun `shouldExtract returns true every 10 messages`() {
        assertFalse(service.shouldExtract(0))
        assertFalse(service.shouldExtract(5))
        assertTrue(service.shouldExtract(10))
        assertFalse(service.shouldExtract(15))
        assertTrue(service.shouldExtract(20))
        assertTrue(service.shouldExtract(30))
    }

    @Test
    fun `extractQuickFacts extracts name from Chinese text`() {
        val facts = service.extractQuickFacts(1, "我叫小明，今年20岁", 1, 1)
        assertTrue(facts.any { it.content.contains("小明") })
        assertTrue(facts.any { it.category == "fact" })
    }

    @Test
    fun `extractQuickFacts extracts age`() {
        val facts = service.extractQuickFacts(1, "我今年25岁", 1, 1)
        assertTrue(facts.any { it.content.contains("25") })
    }

    @Test
    fun `extractQuickFacts extracts preference`() {
        val facts = service.extractQuickFacts(1, "我非常喜欢猫", 1, 1)
        assertTrue(facts.any { it.content.contains("喜欢猫") })
    }

    @Test
    fun `extractQuickFacts extracts dislike`() {
        val facts = service.extractQuickFacts(1, "我讨厌下雨天。", 1, 1)
        assertTrue(facts.any { it.content.contains("讨厌") || it.content.contains("下雨") })
    }

    @Test
    fun `extractQuickFacts extracts commitment`() {
        val facts = service.extractQuickFacts(1, "我答应明天来看你。", 1, 1)
        assertTrue(facts.any { it.category == "event" })
    }

    @Test
    fun `extractQuickFacts returns empty for trivial message`() {
        val facts = service.extractQuickFacts(1, "好的", 1, 1)
        assertTrue(facts.isEmpty())
    }

    @Test
    fun `extractQuickFacts sets correct characterId`() {
        val facts = service.extractQuickFacts(42, "我叫小红", 1, 1)
        assertTrue(facts.all { it.characterId == 42L })
    }

    @Test
    fun `extractQuickFacts uses regex source`() {
        val facts = service.extractQuickFacts(1, "我叫小明", 1, 1)
        assertTrue(facts.all { it.source == "regex" })
    }
}
