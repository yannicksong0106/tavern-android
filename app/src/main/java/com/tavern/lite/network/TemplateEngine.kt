package com.tavern.lite.network

import android.util.Log
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.context.MethodValueResolver
import java.util.Collections

/**
 * Handlebars template engine for prompt construction.
 * Supports {{var}}, {{#if ...}}, {{#each ...}}, {{#unless ...}} syntax.
 * Templates are compiled and cached for performance.
 */
object TemplateEngine {

    private const val TAG = "TemplateEngine"

    private const val MAX_CACHE_ENTRIES = 32

    private val handlebars = Handlebars()

    // key 是整段模板文本（角色描述/系统提示，常几 KB），无界会随角色/编辑次数无限增长（X 审计）。
    // 用 access-order LRU 限制到 MAX_CACHE_ENTRIES，包 synchronizedMap 保并发安全。
    private val templateCache: MutableMap<String, Template> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Template>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Template>): Boolean =
                size > MAX_CACHE_ENTRIES
        }
    )

    /**
     * Render a Handlebars template with the given variables.
     * Templates are cached after first compilation.
     */
    fun render(template: String, variables: Map<String, Any?>): String {
        if (template.isBlank()) return template

        // Quick path: if no template syntax, return as-is
        if (!template.contains("{{")) return template

        return try {
            val compiled = templateCache.getOrPut(template) {
                handlebars.compileInline(template)
            }
            val context = Context.newBuilder(variables)
                .resolver(MapValueResolver.INSTANCE, MethodValueResolver.INSTANCE)
                .build()
            compiled.apply(context)
        } catch (e: Exception) {
            // Fallback: simple replacement if Handlebars parsing fails
            Log.w(TAG, "Handlebars render failed, using simple replacement", e)
            fallbackReplace(template, variables)
        }
    }

    /**
     * Fallback simple replacement when Handlebars parsing fails.
     * Preserves backward compatibility with {{user}} and {{char}} style templates.
     */
    private fun fallbackReplace(template: String, variables: Map<String, Any?>): String {
        var result = template
        variables.forEach { (key, value) ->
            if (value != null) {
                result = result.replace("{{$key}}", value.toString())
            }
        }
        return result
    }

    /**
     * Clear the template cache (e.g., when switching characters or presets).
     */
    fun clearCache() {
        templateCache.clear()
    }
}
