package com.fitmate.app.samsunghealth

import java.time.Instant

data class SamsungWorkoutUiModel(
    val uid: String,
    val sourceAppId: String?,
    val device: SamsungDeviceUiModel?,
    val exerciseType: String,
    val customTitle: String?,
    val startTime: Instant,
    val endTime: Instant,
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
    val logSampleCount: Int
)

object SamsungWorkoutMetrics {
    fun kilometers(meters: Float?): Float? = meters?.takeIf { it > 0f }?.div(1_000f)
    fun kilometersPerHour(metersPerSecond: Float?): Float? = metersPerSecond?.times(3.6f)
    fun paceSecondsPerKm(distanceMeters: Float?, durationSeconds: Long): Double? =
        distanceMeters?.takeIf { it > 0f }?.let { durationSeconds / (it / 1_000.0) }
}
