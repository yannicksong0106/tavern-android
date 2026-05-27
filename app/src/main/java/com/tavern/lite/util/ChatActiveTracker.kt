package com.tavern.lite.util

import java.util.concurrent.ConcurrentHashMap

/**
 * 追踪用户当前正在前台操作的聊天。
 * 由 ChatViewModel 在 init/onCleared 中维护，
 * BackgroundProactiveWorker 读取以跳过活跃聊天。
 */
object ChatActiveTracker {
    private val activeChatIds = ConcurrentHashMap<Long, Boolean>()

    fun setActive(chatId: Long) {
        activeChatIds[chatId] = true
    }

    fun clearActive(chatId: Long) {
        activeChatIds.remove(chatId)
    }

    fun isActive(chatId: Long): Boolean = activeChatIds[chatId] == true
}
