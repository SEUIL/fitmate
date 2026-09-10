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
    fun `기존 부위별 지방량 항목이 있는 기록도 목록과 상세에서 수치를 보존한다`() {
        val userId = UUID.randomUUID()
        val record = InbodyRecord(id = UUID.randomUUID(), userId = userId, optionalValuesJson = """
            {"TRUNK_FAT_MASS_KG":7.9,"LEFT_ARM_FAT_MASS_KG":0.8,"RIGHT_ARM_FAT_MASS_KG":0.7,
             "LEFT_LEG_FAT_MASS_KG":2.1,"RIGHT_LEG_FAT_MASS_KG":2.0,"BMI":23.7}
        """.trimIndent())
        val original = record.optionalValuesJson
        `when`(records.findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(userId)).thenReturn(listOf(record))
        `when`(records.findByIdAndUserId(requireNotNull(record.id), userId)).thenReturn(record)

        val expected = mapOf(InbodyFieldCode.TRUNK_FAT_KG to BigDecimal("7.9"),
            InbodyFieldCode.LEFT_ARM_FAT_KG to BigDecimal("0.8"), InbodyFieldCode.RIGHT_ARM_FAT_KG to BigDecimal("0.7"),
            InbodyFieldCode.LEFT_LEG_FAT_KG to BigDecimal("2.1"), InbodyFieldCode.RIGHT_LEG_FAT_KG to BigDecimal("2.0"),
            InbodyFieldCode.BMI to BigDecimal("23.7"))
        assertThat(service.list(userId).single().optionalValues).isEqualTo(expected)
        assertThat(service.get(userId, requireNotNull(record.id)).optionalValues).isEqualTo(expected)
        assertThat(record.optionalValuesJson).isEqualTo(original)
    }

    @Test
    fun `현재 코드와 기존 코드가 함께 있으면 JSON 순서와 무관하게 현재 코드 값을 사용한다`() {
        val userId = UUID.randomUUID()
        val rows = listOf(
            """{"TRUNK_FAT_KG":8.1,"TRUNK_FAT_MASS_KG":7.9}""",
            """{"TRUNK_FAT_MASS_KG":7.9,"TRUNK_FAT_KG":8.1}""",
        ).map { InbodyRecord(id = UUID.randomUUID(), userId = userId, optionalValuesJson = it) }
        `when`(records.findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(userId)).thenReturn(rows)
        service.list(userId).forEach {
            assertThat(it.optionalValues).containsOnlyKeys(InbodyFieldCode.TRUNK_FAT_KG)
            assertThat(it.optionalValues[InbodyFieldCode.TRUNK_FAT_KG]).isEqualByComparingTo("8.1")
        }
    }

    @Test
    fun `현재 항목 코드와 선택 항목 없는 기록은 그대로 조회한다`() {
        val userId = UUID.randomUUID()
        val values = InbodyFieldCode.entries.associateWith { BigDecimal("1.25") }
        `when`(records.findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(userId)).thenReturn(listOf(
            InbodyRecord(id = UUID.randomUUID(), userId = userId, optionalValuesJson = jacksonObjectMapper().writeValueAsString(values)),
            InbodyRecord(id = UUID.randomUUID(), userId = userId),
        ))
        val result = service.list(userId)
        assertThat(result[0].optionalValues).isEqualTo(values)
        assertThat(result[1].optionalValues).isEmpty()
    }

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
