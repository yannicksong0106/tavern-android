package com.tavern.lite.data.repository

import com.tavern.lite.data.db.dao.ScriptDao
import com.tavern.lite.data.db.entity.ScriptEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptRepository @Inject constructor(
    private val scriptDao: ScriptDao
) {
    // Cache compiled regex patterns to avoid recompilation on every call
    private val regexCache = mutableMapOf<String, Regex>()
    fun getScriptsForCharacter(characterId: Long): Flow<List<ScriptEntity>> =
        scriptDao.getScriptsForCharacter(characterId)

    suspend fun getEnabledScripts(characterId: Long): List<ScriptEntity> =
        scriptDao.getEnabledScripts(characterId)

    suspend fun insertScript(script: ScriptEntity): Long = scriptDao.insertScript(script)

    suspend fun updateScript(script: ScriptEntity) {
        regexCache.clear()
        scriptDao.updateScript(script)
    }

    suspend fun deleteScript(script: ScriptEntity) {
        regexCache.remove("${script.findPattern}:${script.caseSensitive}")
        scriptDao.deleteScript(script)
    }

    suspend fun deleteAllForCharacter(characterId: Long) {
        regexCache.clear()
        scriptDao.deleteAllForCharacter(characterId)
    }

    /**
     * 对消息文本执行脚本替换。
     * @param characterId 角色 ID
     * @param text 原始文本
     * @param scriptType 0=用户消息, 1=AI回复
     * @return 处理后的文本
     */
    suspend fun applyScripts(characterId: Long, text: String, scriptType: Int): String {
        val scripts = scriptDao.getEnabledScripts(characterId)
        if (scripts.isEmpty()) return text

        var result = text
        for (script in scripts) {
            if (script.scriptType != 2 && script.scriptType != scriptType) continue
            result = applyScript(script, result)
        }
        return result
    }

    private fun applyScript(script: ScriptEntity, text: String): String {
        if (script.findPattern.isEmpty()) return text

        return try {
            if (script.isRegex) {
                val cacheKey = "${script.findPattern}:${script.caseSensitive}"
                val regex = regexCache.getOrPut(cacheKey) {
                    val options = if (script.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    Regex(script.findPattern, options)
                }
                regex.replace(text, script.replacePattern)
            } else {
                if (script.caseSensitive) {
                    text.replace(script.findPattern, script.replacePattern)
                } else {
                    text.replace(script.findPattern, script.replacePattern, ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            text // 正则表达式无效时跳过
        }
    }
}
