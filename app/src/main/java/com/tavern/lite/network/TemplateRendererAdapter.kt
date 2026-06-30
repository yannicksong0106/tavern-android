package com.tavern.lite.network

import com.tavern.lite.domain.port.TemplateRendererPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRendererAdapter @Inject constructor() : TemplateRendererPort {
    override fun render(template: String, variables: Map<String, Any?>): String {
        return TemplateEngine.render(template, variables)
    }

    override fun clearCache() {
        TemplateEngine.clearCache()
    }
}
