package com.fitmate.backend.inbody.infrastructure.ai

import org.springframework.stereotype.Component
import com.fitmate.backend.inbody.domain.InbodyAiProvider

@Component
class InbodyAnalyzerRouter(
    analyzers: List<InbodyAnalyzer>,
    private val properties: InbodyAiProperties,
) {
    private val analyzersByProvider = analyzers.associateBy { it.provider }

    fun current(): InbodyAnalyzer = analyzersByProvider[properties.provider]
        ?: throw InbodyProviderException("PROVIDER_NOT_CONFIGURED", "설정한 AI 공급자를 사용할 수 없습니다.")

    fun forProvider(provider: InbodyAiProvider): InbodyAnalyzer = analyzersByProvider[provider]
        ?: throw InbodyProviderException("PROVIDER_NOT_CONFIGURED", "요청한 AI 공급자를 사용할 수 없습니다.")
}
