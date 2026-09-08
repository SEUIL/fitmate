package com.fitmate.backend.inbody.application

import com.fitmate.backend.inbody.domain.InbodyExtractedField
import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.domain.InbodyFieldStatus
import com.fitmate.backend.inbody.infrastructure.ai.ProviderExtractedField
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class InbodyFieldNormalizer {
    fun normalize(input: List<ProviderExtractedField>): List<InbodyExtractedField> {
        val firstByCode = input.mapNotNull { raw ->
            runCatching { InbodyFieldCode.valueOf(raw.code) }.getOrNull()?.let { it to raw }
        }.toMap()
        val normalized = InbodyFieldCode.entries.map { code -> normalize(code, firstByCode[code]) }.toMutableList()
        val weight = numeric(normalized.first { it.code == InbodyFieldCode.WEIGHT_KG }.value)
        val muscleIndex = normalized.indexOfFirst { it.code == InbodyFieldCode.SKELETAL_MUSCLE_MASS_KG }
        val muscle = numeric(normalized[muscleIndex].value)
        if (weight != null && muscle != null && muscle > weight) {
            normalized[muscleIndex] = normalized[muscleIndex].copy(
                status = InbodyFieldStatus.REVIEW_REQUIRED,
                issueCode = "MUSCLE_EXCEEDS_WEIGHT",
            )
        }
        return normalized
    }

    private fun normalize(code: InbodyFieldCode, raw: ProviderExtractedField?): InbodyExtractedField {
        val value = raw?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: return InbodyExtractedField(code, null, raw?.unit, raw?.rawText, InbodyFieldStatus.NOT_RECOGNIZED)
        if (code == InbodyFieldCode.MEASUREMENT_DATE) {
            val date = runCatching { LocalDate.parse(value) }.getOrNull()
            return if (date == null || date > LocalDate.now()) {
                InbodyExtractedField(code, value, null, raw.rawText, InbodyFieldStatus.REVIEW_REQUIRED, "INVALID_DATE")
            } else InbodyExtractedField(code, date.toString(), null, raw.rawText, InbodyFieldStatus.RECOGNIZED)
        }
        val number = numeric(value)
            ?: return InbodyExtractedField(code, value, raw.unit, raw.rawText, InbodyFieldStatus.REVIEW_REQUIRED, "INVALID_NUMBER")
        val range = ranges[code]
        if (range != null && (number < range.first || number > range.second)) {
            return InbodyExtractedField(code, number.stripTrailingZeros().toPlainString(), raw.unit, raw.rawText,
                InbodyFieldStatus.REVIEW_REQUIRED, "OUT_OF_RANGE")
        }
        return InbodyExtractedField(code, number.stripTrailingZeros().toPlainString(), raw.unit, raw.rawText,
            InbodyFieldStatus.RECOGNIZED)
    }

    private fun numeric(value: String?): BigDecimal? = value?.replace(",", "")?.let {
        runCatching { BigDecimal(it) }.getOrNull()
    }

    private companion object {
        val ranges = mapOf(
            InbodyFieldCode.HEIGHT_CM to (BigDecimal("50") to BigDecimal("250")),
            InbodyFieldCode.WEIGHT_KG to (BigDecimal("1") to BigDecimal("500")),
            InbodyFieldCode.SKELETAL_MUSCLE_MASS_KG to (BigDecimal.ZERO to BigDecimal("500")),
            InbodyFieldCode.BODY_FAT_PERCENT to (BigDecimal.ZERO to BigDecimal("100")),
            InbodyFieldCode.BMI to (BigDecimal.ZERO to BigDecimal("100")),
            InbodyFieldCode.INBODY_SCORE to (BigDecimal.ZERO to BigDecimal("100")),
            InbodyFieldCode.VISCERAL_FAT_LEVEL to (BigDecimal.ZERO to BigDecimal("100")),
            InbodyFieldCode.BASAL_METABOLIC_RATE_KCAL to (BigDecimal.ZERO to BigDecimal("10000")),
        )
    }
}
