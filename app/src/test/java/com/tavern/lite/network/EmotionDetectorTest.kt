package com.tavern.lite.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmotionDetectorTest {

    private lateinit var detector: EmotionDetector

    @Before
    fun setup() {
        detector = EmotionDetector()
    }

    // ==================== Empty / blank ====================

    @Test
    fun `blank text returns neutral`() {
        assertEquals("neutral", detector.detectEmotion(""))
        assertEquals("neutral", detector.detectEmotion("   "))
    }

    // ==================== Emoji detection (highest priority) ====================

    @Test
    fun `emoji happy detected`() {
        assertEquals("happy", detector.detectEmotion("好的 😊"))
    }

    @Test
    fun `emoji sad detected`() {
        assertEquals("sad", detector.detectEmotion("我知道了 😢"))
    }

    @Test
    fun `emoji love detected`() {
        assertEquals("love", detector.detectEmotion("谢谢你 ❤️"))
    }

    @Test
    fun `emoji angry detected`() {
        assertEquals("angry", detector.detectEmotion("太过分了 😠"))
    }

    // ==================== Action pattern detection ====================

    @Test
    fun `action pattern sad - sigh`() {
        assertEquals("sad", detector.detectEmotion("*sighs deeply* It's fine"))
    }

    @Test
    fun `action pattern happy - smile`() {
        assertEquals("happy", detector.detectEmotion("*smiles warmly* Sure"))
    }

    @Test
    fun `action pattern angry - clench`() {
        assertEquals("angry", detector.detectEmotion("*clenches fists* What did you say?"))
    }

    @Test
    fun `action pattern scared - tremble`() {
        assertEquals("scared", detector.detectEmotion("*trembles in fear* Don't come closer"))
    }

    @Test
    fun `action pattern embarrassed - blush`() {
        assertEquals("embarrassed", detector.detectEmotion("*blushes* Don't say that"))
    }

    @Test
    fun `action pattern english - sigh`() {
        assertEquals("sad", detector.detectEmotion("*sighs* It's fine"))
    }

    @Test
    fun `action pattern english - smile`() {
        assertEquals("happy", detector.detectEmotion("*smiles* Sure"))
    }

    // ==================== Chinese keyword detection ====================

    @Test
    fun `chinese happy keyword`() {
        assertEquals("happy", detector.detectEmotion("我今天很开心"))
    }

    @Test
    fun `chinese sad keyword`() {
        assertEquals("sad", detector.detectEmotion("听到这个消息我很难过"))
    }

    @Test
    fun `chinese angry keyword`() {
        assertEquals("angry", detector.detectEmotion("我非常生气"))
    }

    @Test
    fun `chinese surprised keyword`() {
        assertEquals("surprised", detector.detectEmotion("这太让人惊讶了"))
    }

    @Test
    fun `chinese scared keyword`() {
        assertEquals("scared", detector.detectEmotion("我感到害怕"))
    }

    @Test
    fun `chinese love keyword`() {
        assertEquals("love", detector.detectEmotion("我很喜欢你"))
    }

    // ==================== English keyword detection ====================

    @Test
    fun `english happy keyword`() {
        assertEquals("happy", detector.detectEmotion("I'm so happy today"))
    }

    @Test
    fun `english sad keyword`() {
        assertEquals("sad", detector.detectEmotion("That makes me sad"))
    }

    @Test
    fun `english angry keyword`() {
        assertEquals("angry", detector.detectEmotion("I'm furious about this"))
    }

    // ==================== Priority / weighting ====================

    @Test
    fun `longer keywords weigh more than shorter ones`() {
        // "眉开眼笑" (weight 3) should beat single "哭" (weight 1)
        // This sentence has both sad and happy keywords, but happy has more weight
        val text = "他眉开眼笑，但旁人都在哭"
        // "眉开眼笑" = 3 for happy, "哭" = 1 for sad
        assertEquals("happy", detector.detectEmotion(text))
    }

    @Test
    fun `multiple keywords of same emotion accumulate score`() {
        val text = "她又哭又难过，悲伤极了" // 哭(1) + 难过(2) + 悲伤(2) = 5 for sad
        assertEquals("sad", detector.detectEmotion(text))
    }

    @Test
    fun `action pattern takes priority over keyword`() {
        // Action pattern for sad (English), keyword for happy (Chinese)
        val text = "*sighs* 我很开心"
        // Action pattern is checked before keywords
        assertEquals("sad", detector.detectEmotion(text))
    }

    // ==================== No false positives ====================

    @Test
    fun `neutral text without emotion keywords returns neutral`() {
        assertEquals("neutral", detector.detectEmotion("今天天气不错，我们出去走走吧"))
    }

    @Test
    fun `question sentence without emotion returns neutral`() {
        assertEquals("neutral", detector.detectEmotion("你什么时候有空？"))
    }

    @Test
    fun `normal english sentence returns neutral`() {
        assertEquals("neutral", detector.detectEmotion("Please send me the report by Friday"))
    }

    @Test
    fun `technical text returns neutral`() {
        assertEquals("neutral", detector.detectEmotion("The API returns a JSON object with status 200"))
    }

    // ==================== getSupportedEmotions ====================

    @Test
    fun `supported emotions list is not empty`() {
        val emotions = detector.getSupportedEmotions()
        assertTrue(emotions.isNotEmpty())
    }

    @Test
    fun `supported emotions contains all expected types`() {
        val emotions = detector.getSupportedEmotions()
        val expected = listOf("happy", "sad", "angry", "surprised", "scared",
            "disgusted", "confused", "embarrassed", "love", "neutral")
        assertEquals(expected.sorted(), emotions.sorted())
    }
}
