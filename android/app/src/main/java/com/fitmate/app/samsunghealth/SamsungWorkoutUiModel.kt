package com.fitmate.app.samsunghealth

import java.time.Instant

data class SamsungWorkoutUiModel(
    val uid: String,
    val sourceAppId: String?,
    val sourceModifiedAt: Instant?,
    val device: SamsungDeviceUiModel?,
    val exerciseType: String,
    val customTitle: String?,
    val startTime: Instant,
    val endTime: Instant,
    val activeDurationSeconds: Long,
    val sessionCount: Int,
    val distanceMeters: Float?,
    val calories: Float?,
    val meanHeartRate: Float?,
    val minHeartRate: Float?,
    val maxHeartRate: Float?,
    val meanSpeedMetersPerSecond: Float?,
    val maxSpeedMetersPerSecond: Float?,
    val altitudeGainMeters: Float?,
    val altitudeLossMeters: Float?,
    val meanCadence: Float?,
    val vo2Max: Float?,
    val logSampleCount: Int,
    /** Retained in memory only; it is sent only after the user confirms this source record. */
    val timeSeries: List<SamsungWorkoutTimeSample> = emptyList(),
    val timeSeriesQuality: SamsungTimeSeriesQuality = SamsungTimeSeriesQuality(),
)

data class SamsungWorkoutTimeSample(
    val measuredAt: Instant,
    val heartRate: Float?,
    val speedMetersPerSecond: Float?,
)

data class SamsungTimeSeriesQuality(
    val heartRateConflictTimestampCount: Int = 0,
    val speedConflictTimestampCount: Int = 0,
)

object SamsungWorkoutMetrics {
    fun kilometers(meters: Float?): Float? = meters?.takeIf { it > 0f }?.div(1_000f)
    fun kilometersPerHour(metersPerSecond: Float?): Float? = metersPerSecond?.times(3.6f)
    fun paceSecondsPerKm(distanceMeters: Float?, durationSeconds: Long): Double? =
        distanceMeters?.takeIf { it > 0f }?.let { durationSeconds / (it / 1_000.0) }
}
