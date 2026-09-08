package com.fitmate.backend.inbody.api

import com.fitmate.backend.inbody.domain.InbodyAiProvider
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus
import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.domain.InbodyFieldStatus
import com.fitmate.backend.inbody.domain.InbodyRecordSource
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateInbodyAnalysisRequest(@field:NotNull val mediaId: UUID?)

data class InbodyAnalysisFieldResponse(
    val code: InbodyFieldCode,
    val value: String?,
    val unit: String?,
    val rawText: String?,
    val status: InbodyFieldStatus,
    val issueCode: String?,
)

data class InbodyAnalysisFailureResponse(val code: String, val message: String)

data class InbodyAnalysisResponse(
    val id: UUID,
    val mediaId: UUID,
    val status: InbodyAnalysisStatus,
    val provider: InbodyAiProvider,
    val providerModel: String?,
    val fields: List<InbodyAnalysisFieldResponse>,
    val failure: InbodyAnalysisFailureResponse?,
    val attemptCount: Int,
    val completedAt: Instant?,
    val version: Long,
)

data class SaveInbodyRecordRequest(
    val analysisId: UUID? = null,
    @field:NotNull val measuredDate: LocalDate?,
    @field:NotNull @field:DecimalMin("1.0") @field:DecimalMax("500.0") val weightKg: BigDecimal?,
    @field:NotNull @field:DecimalMin("0.0") @field:DecimalMax("500.0") val skeletalMuscleMassKg: BigDecimal?,
    @field:NotNull @field:DecimalMin("0.0") @field:DecimalMax("100.0") val bodyFatPercentage: BigDecimal?,
    val optionalValues: Map<InbodyFieldCode, BigDecimal?> = emptyMap(),
)

data class InbodyRecordResponse(
    val id: UUID,
    val analysisId: UUID?,
    val sourceType: InbodyRecordSource,
    val measuredDate: LocalDate,
    val weightKg: BigDecimal,
    val skeletalMuscleMassKg: BigDecimal,
    val bodyFatPercentage: BigDecimal,
    val optionalValues: Map<InbodyFieldCode, BigDecimal>,
    val createdAt: Instant,
    val version: Long,
)
