package com.tavern.lite.data.model

enum class GroupSchedulingStrategy(val key: String) {
    NATURAL("natural"),
    LIST_ORDER("list_order"),
    ROUND_ROBIN("round_robin");

    companion object {
        fun fromKey(key: String): GroupSchedulingStrategy =
            entries.find { it.key == key } ?: NATURAL
    }
}
