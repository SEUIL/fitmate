package com.fitmate.backend.inbody.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.inbody.api.InbodyRecordResponse
import com.fitmate.backend.inbody.api.SaveInbodyRecordRequest
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus
import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.domain.InbodyRecord
import com.fitmate.backend.inbody.domain.InbodyRecordSource
import com.fitmate.backend.inbody.repository.InbodyAnalysisRepository
import com.fitmate.backend.inbody.repository.InbodyRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class InbodyRecordService(
    private val records: InbodyRecordRepository,
    private val analyses: InbodyAnalysisRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun create(userId: UUID, request: SaveInbodyRecordRequest): InbodyRecordResponse {
        validate(request)
        request.analysisId?.let { analysisId ->
            val analysis = analyses.findByIdAndUserId(analysisId, userId)
                ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "인바디 판독 내역을 찾을 수 없습니다.")
            if (analysis.status !in setOf(InbodyAnalysisStatus.COMPLETED, InbodyAnalysisStatus.REVIEW_REQUIRED)) {
                throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "판독이 끝난 뒤 기록을 저장할 수 있습니다.")
            }
            if (records.existsByAnalysisId(analysisId)) {
                throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "이미 저장된 인바디 판독입니다.")
            }
        }

        val optionalValues = request.optionalValues.mapNotNull { (code, value) -> value?.let { code to it } }.toMap()
        val saved = records.saveAndFlush(InbodyRecord(
            id = UUID.randomUUID(),
            userId = userId,
            analysisId = request.analysisId,
            sourceType = if (request.analysisId == null) InbodyRecordSource.MANUAL else InbodyRecordSource.AI_OCR,
            measuredDate = requireNotNull(request.measuredDate),
            weightKg = requireNotNull(request.weightKg),
            skeletalMuscleMassKg = requireNotNull(request.skeletalMuscleMassKg),
            bodyFatPercentage = requireNotNull(request.bodyFatPercentage),
            optionalValuesJson = optionalValues.takeIf { it.isNotEmpty() }?.let(objectMapper::writeValueAsString),
        ))
        return response(saved)
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID): List<InbodyRecordResponse> =
        records.findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(userId).map(::response)

    @Transactional(readOnly = true)
    fun get(userId: UUID, recordId: UUID): InbodyRecordResponse = response(owned(userId, recordId))

    @Transactional
    fun delete(userId: UUID, recordId: UUID) {
        records.delete(owned(userId, recordId))
    }

    private fun validate(request: SaveInbodyRecordRequest) {
        val measuredDate = requireNotNull(request.measuredDate)
        val weight = requireNotNull(request.weightKg)
        val muscle = requireNotNull(request.skeletalMuscleMassKg)
        if (measuredDate > LocalDate.now()) invalid("측정일은 미래일 수 없습니다.")
        if (muscle > weight) invalid("골격근량은 체중보다 클 수 없습니다.")
        val reserved = setOf(
            InbodyFieldCode.MEASUREMENT_DATE,
            InbodyFieldCode.WEIGHT_KG,
            InbodyFieldCode.SKELETAL_MUSCLE_MASS_KG,
            InbodyFieldCode.BODY_FAT_PERCENT,
        )
        if (request.optionalValues.keys.any { it in reserved }) {
            invalid("필수 항목은 각각의 전용 입력란에 입력해 주세요.")
        }
        request.optionalValues.forEach { (code, value) ->
            if (value != null && !validOptionalValue(code, value)) invalid("${code.name} 값의 범위를 확인해 주세요.")
        }
    }

    private fun validOptionalValue(code: InbodyFieldCode, value: BigDecimal): Boolean = when (code) {
        InbodyFieldCode.WEIGHT_CONTROL_KG -> value in BigDecimal("-500")..BigDecimal("500")
        InbodyFieldCode.HEIGHT_CM -> value in BigDecimal("50")..BigDecimal("250")
        InbodyFieldCode.BODY_FAT_MASS_KG,
        InbodyFieldCode.FAT_FREE_MASS_KG,
        InbodyFieldCode.BODY_WATER_L,
        InbodyFieldCode.PROTEIN_KG,
        InbodyFieldCode.MINERALS_KG,
        InbodyFieldCode.RECOMMENDED_WEIGHT_KG,
        InbodyFieldCode.LEFT_ARM_MUSCLE_KG,
        InbodyFieldCode.RIGHT_ARM_MUSCLE_KG,
        InbodyFieldCode.TRUNK_MUSCLE_KG,
        InbodyFieldCode.LEFT_LEG_MUSCLE_KG,
        InbodyFieldCode.RIGHT_LEG_MUSCLE_KG,
        InbodyFieldCode.LEFT_ARM_FAT_KG,
        InbodyFieldCode.RIGHT_ARM_FAT_KG,
        InbodyFieldCode.TRUNK_FAT_KG,
        InbodyFieldCode.LEFT_LEG_FAT_KG,
        InbodyFieldCode.RIGHT_LEG_FAT_KG -> value in BigDecimal.ZERO..BigDecimal("500")
        InbodyFieldCode.BMI,
        InbodyFieldCode.OBESITY_DEGREE_PERCENT,
        InbodyFieldCode.VISCERAL_FAT_LEVEL,
        InbodyFieldCode.INBODY_SCORE -> value in BigDecimal.ZERO..BigDecimal("100")
        InbodyFieldCode.WAIST_HIP_RATIO -> value in BigDecimal.ZERO..BigDecimal("5")
        InbodyFieldCode.BASAL_METABOLIC_RATE_KCAL -> value in BigDecimal.ZERO..BigDecimal("10000")
        else -> false
    }

    private fun owned(userId: UUID, recordId: UUID): InbodyRecord =
        records.findByIdAndUserId(recordId, userId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "인바디 기록을 찾을 수 없습니다.")

    private fun response(record: InbodyRecord): InbodyRecordResponse {
        val optionalValues = record.optionalValuesJson?.let {
            val stored = objectMapper.readValue(it, object : TypeReference<Map<String, BigDecimal>>() {})
            // Existing records use *_FAT_MASS_KG for segment fat. Normalize only when reading;
            // keep the persisted JSON and the current request/response field names unchanged.
            val aliases = mapOf(
                "LEFT_ARM_FAT_MASS_KG" to InbodyFieldCode.LEFT_ARM_FAT_KG,
                "RIGHT_ARM_FAT_MASS_KG" to InbodyFieldCode.RIGHT_ARM_FAT_KG,
                "TRUNK_FAT_MASS_KG" to InbodyFieldCode.TRUNK_FAT_KG,
                "LEFT_LEG_FAT_MASS_KG" to InbodyFieldCode.LEFT_LEG_FAT_KG,
                "RIGHT_LEG_FAT_MASS_KG" to InbodyFieldCode.RIGHT_LEG_FAT_KG,
            )
            stored.entries.associate { (key, value) ->
                val code = aliases[key] ?: InbodyFieldCode.valueOf(key)
                code to (stored[code.name] ?: value)
            }
        }.orEmpty()
        return InbodyRecordResponse(
            id = requireNotNull(record.id),
            analysisId = record.analysisId,
            sourceType = record.sourceType,
            measuredDate = record.measuredDate,
            weightKg = record.weightKg,
            skeletalMuscleMassKg = record.skeletalMuscleMassKg,
            bodyFatPercentage = record.bodyFatPercentage,
            optionalValues = optionalValues,
            createdAt = record.createdAt,
            version = record.version,
        )
    }

    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)
}
