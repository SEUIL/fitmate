package com.fitmate.backend.inbody.infrastructure.ai

import com.fitmate.backend.inbody.domain.InbodyAiProvider
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fitmate.inbody-ai")
data class InbodyAiProperties(
    var provider: InbodyAiProvider = InbodyAiProvider.MOCK,
    var gemini: GeminiProperties = GeminiProperties(),
)

data class GeminiProperties(
    var apiKey: String = "",
    var model: String = "gemini-3.7-flash",
    var baseUrl: String = "https://generativelanguage.googleapis.com",
    var timeoutSeconds: Long = 30,
    var directConnection: Boolean = false,
)
