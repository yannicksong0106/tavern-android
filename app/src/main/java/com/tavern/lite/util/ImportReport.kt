package com.tavern.lite.util

data class ImportReport(
    val chatId: Long,
    val importedMessages: Int,
    val skippedMessages: Int,
    val format: String,
    val skippedFields: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val totalProcessed: Int get() = importedMessages + skippedMessages
    
    fun summary(): String = buildString {
        append("导入完成\n")
        append("• 成功导入 $importedMessages 条消息\n")
        if (skippedMessages > 0) {
            append("• 跳过 $skippedMessages 条空白消息\n")
        }
        if (skippedFields.isNotEmpty()) {
            append("• 忽略字段：${skippedFields.joinToString()}\n")
        }
        if (warnings.isNotEmpty()) {
            append("• 警告：${warnings.joinToString("; ")}")
        }
    }
}
