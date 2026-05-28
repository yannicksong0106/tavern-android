package com.tavern.lite.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 AI 回复文本中检测情感，用于切换立绘表情。
 * 使用关键词匹配实现轻量级情感检测。
 */
@Singleton
class EmotionDetector @Inject constructor() {

    // 情感关键词映射
    private val emotionKeywords = mapOf(
        "happy" to listOf(
            "开心", "高兴", "快乐", "欢喜", "愉快", "兴奋", "喜悦", "欣喜",
            "笑", "微笑", "大笑", "哈哈", "嘻嘻", "呵呵",
            "happy", "glad", "joyful", "excited", "cheerful", "delighted",
            "laugh", "smile", "grin", "haha", "hehe", "lol"
        ),
        "sad" to listOf(
            "伤心", "难过", "悲伤", "悲痛", "悲哀", "忧伤", "哀伤",
            "哭", "流泪", "落泪", "哭泣", "呜呜", "唉",
            "sad", "sorrowful", "grief", "mournful", "melancholy",
            "cry", "weep", "tears", "sob", "wail"
        ),
        "angry" to listOf(
            "生气", "愤怒", "恼怒", "暴怒", "气愤", "恼火", "发火",
            "怒", "愤", "哼", "切", "滚",
            "angry", "furious", "enraged", "irritated", "annoyed", "mad",
            "rage", "huff", "hmph"
        ),
        "surprised" to listOf(
            "惊讶", "吃惊", "震惊", "惊愕", "诧异", "意外",
            "哇", "啊", "咦", "哎呀", "天哪", "不会吧",
            "surprised", "astonished", "amazed", "shocked", "stunned",
            "wow", "whoa", "oh", "omg", "what"
        ),
        "scared" to listOf(
            "害怕", "恐惧", "畏惧", "惊恐", "恐慌", "胆怯",
            "怕", "吓", "瑟瑟发抖",
            "scared", "afraid", "fearful", "terrified", "frightened", "horror",
            "tremble", "shiver", "eek"
        ),
        "disgusted" to listOf(
            "厌恶", "恶心", "反感", "讨厌", "嫌弃",
            "呕", "呸", "切",
            "disgusted", "revolted", "repulsed", "nauseated", "gross",
            "ew", "yuck", "ugh"
        ),
        "confused" to listOf(
            "困惑", "迷茫", "疑惑", "不解", "纳闷", "莫名其妙",
            "嗯？", "啥？", "什么？",
            "confused", "puzzled", "bewildered", "perplexed", "baffled",
            "huh", "what"
        ),
        "embarrassed" to listOf(
            "害羞", "尴尬", "羞涩", "不好意思", "难为情", "腼腆",
            "脸红",
            "embarrassed", "shy", "bashful", "flustered", "blush",
            "awkward"
        ),
        "love" to listOf(
            "喜欢", "爱", "爱慕", "倾心", "心动", "心动",
            "亲", "吻", "拥抱",
            "love", "adore", "cherish", "affection", "fond",
            "kiss", "hug", "embrace", "heart"
        ),
        "neutral" to listOf(
            "平静", "淡定", "冷静", "正常",
            "neutral", "calm", "composed", "steady"
        )
    )

    // 表情符号模式
    private val emojiPatterns = mapOf(
        "happy" to listOf("😊", "😄", "😁", "😆", "🤣", "😂", "🙂", "😃", "🥰", "😍"),
        "sad" to listOf("😢", "😭", "😞", "😔", "😟", "😕", "😿"),
        "angry" to listOf("😠", "😡", "🤬", "💢"),
        "surprised" to listOf("😲", "😮", "🤯", "😱", "😳"),
        "scared" to listOf("😨", "😰", "😥", "😱"),
        "disgusted" to listOf("🤢", "🤮", "😖"),
        "confused" to listOf("😕", "❓", "🤔"),
        "embarrassed" to listOf("😳", "🙈", "🙉"),
        "love" to listOf("❤️", "💕", "💖", "💗", "💘", "💝", "🥰", "😍")
    )

    /**
     * 检测文本中的情感。
     * 返回最匹配的情感标签，如果没有匹配则返回 "neutral"。
     */
    fun detectEmotion(text: String): String {
        if (text.isBlank()) return "neutral"

        val normalizedText = text.lowercase().trim()

        // 1. 先检查表情符号
        for ((emotion, emojis) in emojiPatterns) {
            for (emoji in emojis) {
                if (normalizedText.contains(emoji)) {
                    return emotion
                }
            }
        }

        // 2. 检查关键词
        val scores = mutableMapOf<String, Int>()
        for ((emotion, keywords) in emotionKeywords) {
            var score = 0
            for (keyword in keywords) {
                if (normalizedText.contains(keyword.lowercase())) {
                    score++
                }
            }
            if (score > 0) {
                scores[emotion] = score
            }
        }

        // 3. 返回得分最高的情感
        return scores.maxByOrNull { it.value }?.key ?: "neutral"
    }

    /**
     * 获取支持的情感列表。
     */
    fun getSupportedEmotions(): List<String> = emotionKeywords.keys.toList()
}
