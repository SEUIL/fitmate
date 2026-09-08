package com.fitmate.backend.inbody.application

import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.domain.InbodyFieldStatus
import com.fitmate.backend.inbody.infrastructure.ai.ProviderExtractedField
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InbodyFieldNormalizerTest {
    private val normalizer = InbodyFieldNormalizer()

    @Test
    fun `판독되지 않은 항목은 빈 값과 NOT_RECOGNIZED 상태로 반환한다`() {
        val fields = normalizer.normalize(listOf(
            field(InbodyFieldCode.WEIGHT_KG, "68.2", "kg"),
        ))

        assertThat(fields).hasSize(InbodyFieldCode.entries.size)
        assertThat(fields.single { it.code == InbodyFieldCode.WEIGHT_KG }.status)
            .isEqualTo(InbodyFieldStatus.RECOGNIZED)
        assertThat(fields.single { it.code == InbodyFieldCode.BODY_FAT_PERCENT })
            .extracting({ it.value }, { it.status })
            .containsExactly(null, InbodyFieldStatus.NOT_RECOGNIZED)
    }

    @Test
    fun `범위를 벗어난 값과 체중보다 큰 골격근량은 검토 대상으로 반환한다`() {
        val fields = normalizer.normalize(listOf(
            field(InbodyFieldCode.WEIGHT_KG, "68.2", "kg"),
            field(InbodyFieldCode.SKELETAL_MUSCLE_MASS_KG, "70", "kg"),
            field(InbodyFieldCode.BODY_FAT_PERCENT, "130", "%"),
        ))

        assertThat(fields.single { it.code == InbodyFieldCode.SKELETAL_MUSCLE_MASS_KG }.issueCode)
            .isEqualTo("MUSCLE_EXCEEDS_WEIGHT")
        assertThat(fields.single { it.code == InbodyFieldCode.BODY_FAT_PERCENT }.issueCode)
            .isEqualTo("OUT_OF_RANGE")
    }

    @Test
    fun `숫자의 쉼표를 제거하고 날짜를 ISO 형식으로 유지한다`() {
        val fields = normalizer.normalize(listOf(
            field(InbodyFieldCode.MEASUREMENT_DATE, "2026-09-08", null),
            field(InbodyFieldCode.BASAL_METABOLIC_RATE_KCAL, "1,534", "kcal"),
        ))

        assertThat(fields.single { it.code == InbodyFieldCode.MEASUREMENT_DATE }.value).isEqualTo("2026-09-08")
        assertThat(fields.single { it.code == InbodyFieldCode.BASAL_METABOLIC_RATE_KCAL }.value).isEqualTo("1534")
    }

    private fun field(code: InbodyFieldCode, value: String?, unit: String?) =
        ProviderExtractedField(code.name, value, unit, value)
}
