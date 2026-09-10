package com.fitmate.app.samsunghealth

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit

internal const val SAMSUNG_HEALTH_PROVIDER = "SAMSUNG_HEALTH_DATA_SDK"

data class ExternalWorkoutSummary(
    val provider: String = SAMSUNG_HEALTH_PROVIDER,
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
) {
    fun normalized() = copy(
        sourceModifiedAt = sourceModifiedAt?.truncatedTo(ChronoUnit.MILLIS),
        startedAt = startedAt.truncatedTo(ChronoUnit.MILLIS),
        endedAt = endedAt.truncatedTo(ChronoUnit.MILLIS),
        distanceMeters = distanceMeters.normalizedMetric(),
        caloriesKcal = caloriesKcal.normalizedMetric(),
        meanHeartRate = meanHeartRate.normalizedMetric(),
        minHeartRate = minHeartRate.normalizedMetric(),
        maxHeartRate = maxHeartRate.normalizedMetric(),
    )
}

fun SamsungWorkoutUiModel.toExternalWorkoutSummary() = ExternalWorkoutSummary(
    sourceRecordId = uid,
    sourceAppId = sourceAppId?.takeIf { it.isNotEmpty() },
    sourceModifiedAt = sourceModifiedAt,
    exerciseType = exerciseType,
    startedAt = startTime,
    endedAt = endTime,
    activeDurationSeconds = activeDurationSeconds,
    distanceMeters = distanceMeters?.toString()?.toBigDecimalOrNull(),
    caloriesKcal = calories?.toString()?.toBigDecimalOrNull(),
    meanHeartRate = meanHeartRate?.toString()?.toBigDecimalOrNull(),
    minHeartRate = minHeartRate?.toString()?.toBigDecimalOrNull(),
    maxHeartRate = maxHeartRate?.toString()?.toBigDecimalOrNull(),
).normalized()

private fun BigDecimal?.normalizedMetric() = this?.setScale(2, RoundingMode.HALF_UP)
