package com.tavern.lite.util

/**
 * 移除 LLM 回复中的角色名前缀，如 "[角色名]: 内容" → "内容"
 */
fun String.cleanCharacterPrefix(charName: String): String {
    val trimmed = trim()
    val prefix = "[$charName]"
    if (!trimmed.startsWith(prefix)) return trimmed
    val afterPrefix = trimmed.substring(prefix.length)
    var i = 0
    while (i < afterPrefix.length && (afterPrefix[i] == ':' || afterPrefix[i] == '\uFF1A' || afterPrefix[i] == ' ' || afterPrefix[i] == '\t')) {
        i++
    }
    return afterPrefix.substring(i).trim()
}
