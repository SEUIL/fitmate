package com.fitmate.backend.inbody.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.inbody.api.SaveInbodyRecordRequest
import com.fitmate.backend.inbody.domain.InbodyAnalysis
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus
import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.domain.InbodyRecord
import com.fitmate.backend.inbody.domain.InbodyRecordSource
import com.fitmate.backend.inbody.repository.InbodyAnalysisRepository
import com.fitmate.backend.inbody.repository.InbodyRecordRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class InbodyRecordServiceTest {
    private val records = mock(InbodyRecordRepository::class.java)
    private val analyses = mock(InbodyAnalysisRepository::class.java)
    private val service = InbodyRecordService(records, analyses, jacksonObjectMapper())

    @Test
    fun `AI가 못 읽은 체지방률을 사용자가 입력해 최종 기록으로 저장한다`() {
        val userId = UUID.randomUUID()
        val analysisId = UUID.randomUUID()
        `when`(analyses.findByIdAndUserId(analysisId, userId)).thenReturn(InbodyAnalysis(
            id = analysisId,
            userId = userId,
            mediaId = UUID.randomUUID(),
            status = InbodyAnalysisStatus.REVIEW_REQUIRED,
        ))
        `when`(records.existsByAnalysisId(analysisId)).thenReturn(false)
        `when`(records.saveAndFlush(any(InbodyRecord::class.java))).thenAnswer { it.arguments[0] as InbodyRecord }

        val saved = service.create(userId, SaveInbodyRecordRequest(
            analysisId = analysisId,
            measuredDate = LocalDate.of(2026, 9, 8),
            weightKg = BigDecimal("68.2"),
            skeletalMuscleMassKg = BigDecimal("29.4"),
            bodyFatPercentage = BigDecimal("18.7"),
            optionalValues = mapOf(InbodyFieldCode.BMI to BigDecimal("22.1")),
        ))

        assertThat(saved.sourceType).isEqualTo(InbodyRecordSource.AI_OCR)
        assertThat(saved.bodyFatPercentage).isEqualByComparingTo("18.7")
        assertThat(saved.optionalValues[InbodyFieldCode.BMI]).isEqualByComparingTo("22.1")
    }

    @Test
    fun `사용자가 보정한 값도 물리적으로 불가능하면 저장하지 않는다`() {
        assertThatThrownBy {
            service.create(UUID.randomUUID(), SaveInbodyRecordRequest(
                measuredDate = LocalDate.of(2026, 9, 8),
                weightKg = BigDecimal("60"),
                skeletalMuscleMassKg = BigDecimal("70"),
                bodyFatPercentage = BigDecimal("20"),
            ))
        }.isInstanceOf(ApiException::class.java)
            .extracting { (it as ApiException).errorCode }
            .isEqualTo(ApiErrorCode.VALIDATION_ERROR)
    }
}
