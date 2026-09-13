package com.fitmate.backend.inbody.infrastructure.ai

import com.fitmate.backend.inbody.domain.InbodyAiProvider
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fitmate.inbody-ai")
data class InbodyAiProperties(
    var provider: InbodyAiProvider = InbodyAiProvider.MOCK,
    var openai: OpenAiProperties = OpenAiProperties(),
)

data class OpenAiProperties(
    var apiKey: String = "",
    var model: String = "gpt-5.6-luna",
    var baseUrl: String = "https://api.openai.com",
    var timeoutSeconds: Long = 30,
    var directConnection: Boolean = false,
)
