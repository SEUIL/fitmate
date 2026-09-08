package com.fitmate.backend.inbody.infrastructure.ai.mock

import com.fitmate.backend.inbody.domain.InbodyAiProvider
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalysisInput
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalyzer
import com.fitmate.backend.inbody.infrastructure.ai.InbodyProviderResult
import com.fitmate.backend.inbody.infrastructure.ai.ProviderExtractedField
import org.springframework.stereotype.Component

@Component
class MockInbodyAnalyzer : InbodyAnalyzer {
    override val provider = InbodyAiProvider.MOCK

    override fun analyze(input: InbodyAnalysisInput) = InbodyProviderResult(
        model = "fitmate-inbody-mock-v1",
        fields = listOf(
            ProviderExtractedField("MEASUREMENT_DATE", "2026-09-08", null, "2026.09.08"),
            ProviderExtractedField("WEIGHT_KG", "68.2", "kg", "체중 68.2 kg"),
            ProviderExtractedField("SKELETAL_MUSCLE_MASS_KG", "29.4", "kg", "골격근량 29.4 kg"),
            ProviderExtractedField("BODY_FAT_PERCENT", null, "%", null),
            ProviderExtractedField("BMI", "22.1", null, "BMI 22.1"),
        ),
    )
}
