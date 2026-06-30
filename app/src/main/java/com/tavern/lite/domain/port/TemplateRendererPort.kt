package com.tavern.lite.domain.port

interface TemplateRendererPort {
    fun render(template: String, variables: Map<String, Any?>): String
    fun clearCache()
}
