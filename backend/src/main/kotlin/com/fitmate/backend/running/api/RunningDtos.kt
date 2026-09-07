package com.fitmate.backend.running.api

import com.fitmate.backend.running.domain.RouteVisibility
import com.fitmate.backend.running.domain.RunType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateRunningRecordRequest(
    val runType: RunType = RunType.FREE,
    @field:Min(1) @field:Max(1_000_000) val targetDistanceMeters: Int? = null,
    @field:Min(1) @field:Max(604_800) val targetDurationSeconds: Int? = null,
    @field:Min(1) @field:Max(86_400) val targetPaceSecondsPerKm: Int? = null,
    @field:Min(1) @field:Max(86_400) val intervalSeconds: Int? = null,
    val routeVisibility: RouteVisibility = RouteVisibility.PRIVATE,
    val autoPauseEnabled: Boolean = false,
    @field:Min(1) @field:Max(604_800) val activeDurationSeconds: Int,
    @field:Min(0) @field:Max(1_000_000) val distanceMeters: Int,
    val startedAt: Instant? = null,
    val completedAt: Instant,
    @field:DecimalMin("0.0") @field:DecimalMax("999999.99") val caloriesKcal: BigDecimal? = null,
    @field:Min(1) @field:Max(300) val cadenceSpm: Int? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("9999.99") val strideLengthCm: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("999999.99") val elevationGainMeters: BigDecimal? = null,
    @field:Min(1) @field:Max(10) val rpe: Int? = null,
    @field:Min(1) @field:Max(5) val conditionScore: Int? = null,
    @field:Size(max = 500) val memo: String? = null,
    @field:Valid @field:Size(max = 10_000) val routePoints: List<RunningRoutePoint> = emptyList(),
    @field:Valid @field:Size(max = 1_000) val laps: List<RunningLapRequest> = emptyList(),
)

data class RunningRoutePoint(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: BigDecimal,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: BigDecimal,
    val recordedAt: Instant,
    val altitudeMeters: BigDecimal? = null,
)

data class RunningLapRequest(
    @field:Min(1) @field:Max(1_000) val lapNo: Int,
    @field:Min(1) @field:Max(1_000_000) val distanceMeters: Int,
    @field:Min(1) @field:Max(604_800) val durationSeconds: Int,
)

data class RunningRecordResponse(
    val id: UUID,
    val runType: RunType,
    val targetDistanceMeters: Int?,
    val targetDurationSeconds: Int?,
    val targetPaceSecondsPerKm: Int?,
    val intervalSeconds: Int?,
    val routeVisibility: RouteVisibility,
    val autoPauseEnabled: Boolean,
    val startedAt: Instant,
    val completedAt: Instant,
    val elapsedDurationSeconds: Long,
    val activeDurationSeconds: Int,
    val distanceMeters: Int,
    val averagePaceSecondsPerKm: Int?,
    val bestPaceSecondsPerKm: Int?,
    val averageSpeedKmh: BigDecimal?,
    val caloriesKcal: BigDecimal?,
    val cadenceSpm: Int?,
    val strideLengthCm: BigDecimal?,
    val elevationGainMeters: BigDecimal?,
    val rpe: Int?,
    val conditionScore: Int?,
    val memo: String?,
    val goalAchieved: Boolean?,
    val routePoints: List<RunningRoutePoint>,
    val laps: List<RunningLapResponse>,
    val version: Long,
)

data class RunningLapResponse(
    val id: UUID,
    val lapNo: Int,
    val startedAt: Instant,
    val endedAt: Instant,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val averagePaceSecondsPerKm: Int,
)

data class RunningRecordSummaryResponse(
    val id: UUID,
    val runType: RunType,
    val completedAt: Instant,
    val activeDurationSeconds: Int,
    val distanceMeters: Int,
    val averagePaceSecondsPerKm: Int?,
    val bestPaceSecondsPerKm: Int?,
    val goalAchieved: Boolean?,
)
