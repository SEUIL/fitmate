package com.fitmate.backend.health.api

import com.fitmate.backend.health.domain.ExternalWorkoutSummary
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PutExternalWorkoutRequest(
    @field:NotBlank @field:Size(max = 32) val provider: String,
    @field:NotBlank @field:Size(max = 128) val sourceRecordId: String,
    @field:Size(max = 255) val sourceAppId: String? = null,
    val sourceModifiedAt: Instant? = null,
    @field:NotBlank @field:Size(max = 64) val exerciseType: String,
    val startedAt: Instant,
    val endedAt: Instant,
    @field:Min(1) val activeDurationSeconds: Long,
    @field:DecimalMin("0.0") @field:DecimalMax("9999999999.99") val distanceMeters: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("9999999999.99") val caloriesKcal: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("300.0") val meanHeartRate: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("300.0") val minHeartRate: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("300.0") val maxHeartRate: BigDecimal? = null,
    @field:Min(0) val expectedVersion: Long? = null,
)

data class ExternalWorkoutResponse(
    val sessionId: UUID,
    val provider: String,
    val sourceRecordId: String,
    val sourceAppId: String?,
    val sourceModifiedAt: Instant?,
    val exerciseType: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val activeDurationSeconds: Long,
    val distanceMeters: BigDecimal?,
    val caloriesKcal: BigDecimal?,
    val meanHeartRate: BigDecimal?,
    val minHeartRate: BigDecimal?,
    val maxHeartRate: BigDecimal?,
    val version: Long,
    val savedAt: Instant,
    val updatedAt: Instant,
)

internal fun ExternalWorkoutSummary.toResponse(sessionId: UUID = requireNotNull(workoutSessionId)) = ExternalWorkoutResponse(
    sessionId, provider, sourceRecordId, sourceAppId, sourceModifiedAt,
    exerciseType, startedAt, endedAt, activeDurationSeconds, distanceMeters, caloriesKcal,
    meanHeartRate, minHeartRate, maxHeartRate, version, savedAt, updatedAt,
)
