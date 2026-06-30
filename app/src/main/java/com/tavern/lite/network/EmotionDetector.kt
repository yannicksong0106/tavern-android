package com.tavern.lite.network

import com.tavern.lite.domain.port.EmotionDetectionPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 AI 回复文本中检测情感，用于切换立绘表情。
 *
 * 检测策略：
 * 1. 表情符号（最高优先级，精确匹配）
 * 2. 动作描述模式（如 *叹了口气*）
 * 3. 关键词匹配（长词权重 > 短词权重）
 */
@Singleton
class EmotionDetector @Inject constructor() : EmotionDetectionPort {

    /** 动作描述模式：星号包裹的动作描述中的关键词 */
    private val actionKeywords = mapOf(
        "happy" to listOf("笑", "微笑", "大笑", "咧嘴", "眉开眼笑", "开心地", "smile", "grin", "beam", "chuckle"),
        "sad" to listOf("叹气", "叹息", "流泪", "哭泣", "落泪", "低头", "哽咽", "抽泣", "sigh", "cry", "weep", "sob", "tear"),
        "angry" to listOf("握拳", "咬牙", "拍桌", "怒", "瞪", "摔", "咆哮", "clench", "slam", "stomp", "glare", "growl", "snarl"),
        "surprised" to listOf("瞪大", "愣住", "震惊", "吃惊", "倒吸", "惊讶", "gasp", "startle", "stun"),
        "scared" to listOf("颤抖", "发抖", "退后", "害怕", "恐惧", "瑟瑟", "tremble", "shiver", "cower", "flinch", "cringe"),
        "disgusted" to listOf("皱眉", "捂嘴", "后退", "恶心", "厌恶", "frown", "grimace", "wince", "recoil"),
        "embarrassed" to listOf("脸红", "害羞", "结巴", "支支吾吾", "blush", "fluster", "stammer", "fidget"),
        "love" to listOf("拥抱", "亲吻", "牵手", "依偎", "靠近", "脸红心跳", "hug", "kiss", "embrace", "cuddle", "snuggle")
    )

    /** 关键词映射：每个情感的关键词列表，长词在前（匹配优先） */
    private val emotionKeywords = mapOf(
        "happy" to listOf(
            "开心", "高兴", "快乐", "欢喜", "愉快", "兴奋", "喜悦", "欣喜",
            "眉开眼笑", "兴高采烈", "喜出望外", "欢天喜地",
            "微笑", "大笑", "哈哈", "嘻嘻", "呵呵",
            "happy", "glad", "joyful", "excited", "cheerful", "delighted",
            "laugh", "smile", "grin", "haha", "hehe", "lol"
        ),
        "sad" to listOf(
            "伤心", "难过", "悲伤", "悲痛", "悲哀", "忧伤", "哀伤",
            "泣不成声", "泪流满面", "痛哭流涕",
            "哭泣", "流泪", "落泪", "呜呜", "唉声叹气",
            "sad", "sorrowful", "grief", "mournful", "melancholy",
            "cry", "weep", "tears", "sob", "wail"
        ),
        "angry" to listOf(
            "生气", "愤怒", "恼怒", "暴怒", "气愤", "恼火", "发火",
            "怒不可遏", "火冒三丈", "怒发冲冠",
            "怒吼", "咆哮", "哼",
            "angry", "furious", "enraged", "irritated", "annoyed", "mad",
            "rage", "huff", "hmph"
        ),
        "surprised" to listOf(
            "惊讶", "吃惊", "震惊", "惊愕", "诧异", "目瞪口呆",
            "不可思议", "难以置信", "大吃一惊",
            "哇", "哎呀", "天哪", "不会吧",
            "surprised", "astonished", "amazed", "shocked", "stunned",
            "wow", "whoa", "omg"
        ),
        "scared" to listOf(
            "害怕", "恐惧", "畏惧", "惊恐", "恐慌", "胆怯",
            "瑟瑟发抖", "毛骨悚然", "心惊胆战",
            "怕", "吓",
            "scared", "afraid", "fearful", "terrified", "frightened", "horror",
            "tremble", "shiver", "eek"
        ),
        "disgusted" to listOf(
            "厌恶", "恶心", "反感", "讨厌", "嫌弃",
            "深恶痛绝", "忍无可忍",
            "呕", "呸",
            "disgusted", "revolted", "repulsed", "nauseated", "gross",
            "ew", "yuck", "ugh"
        ),
        "confused" to listOf(
            "困惑", "迷茫", "疑惑", "不解", "纳闷", "莫名其妙",
            "百思不得其解", "一头雾水",
            "嗯？", "啥？",
            "confused", "puzzled", "bewildered", "perplexed", "baffled",
            "huh"
        ),
        "embarrassed" to listOf(
            "害羞", "尴尬", "羞涩", "不好意思", "难为情", "腼腆",
            "无地自容", "面红耳赤",
            "脸红", "支支吾吾",
            "embarrassed", "shy", "bashful", "flustered", "blush",
            "awkward"
        ),
        "love" to listOf(
            "喜欢", "爱慕", "倾心", "心动",
            "情不自禁", "心花怒放",
            "爱", "亲", "吻", "拥抱",
            "love", "adore", "cherish", "affection", "fond",
            "kiss", "hug", "embrace", "heart"
        ),
        "neutral" to listOf(
            "平静", "淡定", "冷静", "正常",
            "neutral", "calm", "composed", "steady"
        )
    )

    /** 表情符号模式（无重叠） */
    private val emojiPatterns = mapOf(
        "happy" to listOf("😊", "😄", "😁", "😆", "🤣", "😂", "🙂", "😃", "🥰"),
        "sad" to listOf("😢", "😭", "😞", "😔", "😟", "😕", "😿"),
        "angry" to listOf("😠", "😡", "🤬", "💢"),
        "surprised" to listOf("😲", "😮", "🤯", "😱", "😳"),
        "scared" to listOf("😨", "😰", "😥"),
        "disgusted" to listOf("🤢", "🤮", "😖"),
        "confused" to listOf("❓", "🤔"),
        "embarrassed" to listOf("🙈", "🙉"),
        "love" to listOf("❤️", "💕", "💖", "💗", "💘", "💝", "😍")
    )

    /**
     * 检测文本中的情感。
     * 返回最匹配的情感标签，如果没有匹配则返回 "neutral"。
     */
    override fun detectEmotion(text: String): String {
        if (text.isBlank()) return "neutral"

        val normalizedText = text.lowercase().trim()

        // 1. 表情符号（最高优先级）
        for ((emotion, emojis) in emojiPatterns) {
            for (emoji in emojis) {
                if (normalizedText.contains(emoji)) {
                    return emotion
                }
            }
        }

        // 2. 动作描述模式（*...* 包裹的动作）
        val actionEmotion = detectActionEmotion(text)
        if (actionEmotion != null) return actionEmotion

        // 3. 关键词匹配（长词权重更高）
        val scores = mutableMapOf<String, Int>()
        for ((emotion, keywords) in emotionKeywords) {
            var score = 0
            for (keyword in keywords) {
                if (normalizedText.contains(keyword.lowercase())) {
                    // 长词权重更高：4+ 字符权重 3，2-3 字符权重 2，单字符权重 1
                    score += when {
                        keyword.length >= 4 -> 3
                        keyword.length >= 2 -> 2
                        else -> 1
                    }
                }
            }
            if (score > 0) {
                scores[emotion] = score
            }
        }

        // 4. 返回得分最高的情感
        return scores.maxByOrNull { it.value }?.key ?: "neutral"
    }

    /**
     * 从 *...* 动作块中检测情感。
     */
    private fun detectActionEmotion(text: String): String? {
        var start = text.indexOf('*')
        while (start != -1) {
            val end = text.indexOf('*', start + 1)
            if (end == -1) break
            val block = text.substring(start + 1, end) // 去掉星号
            val blockLower = block.lowercase()
            for ((emotion, keywords) in actionKeywords) {
                if (keywords.any { blockLower.contains(it.lowercase()) }) {
                    return emotion
                }
            }
            start = text.indexOf('*', end + 1)
        }
        return null
    }

    /**
     * 获取支持的情感列表。
     */
    override fun getSupportedEmotions(): List<String> = emotionKeywords.keys.toList()
}
