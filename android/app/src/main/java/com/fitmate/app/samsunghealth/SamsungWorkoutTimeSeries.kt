package com.fitmate.app.samsunghealth

/** Filters SDK values without replacing absent measurements with zero. */
internal fun sanitizeSamsungTimeSeries(
    samples: List<SamsungWorkoutTimeSample>,
    startedAt: java.time.Instant,
    endedAt: java.time.Instant,
): List<SamsungWorkoutTimeSample> = sanitizeSamsungTimeSeriesWithQuality(samples, startedAt, endedAt).samples

internal data class SanitizedSamsungTimeSeries(
    val samples: List<SamsungWorkoutTimeSample>,
    val quality: SamsungTimeSeriesQuality,
)

internal fun sanitizeSamsungTimeSeriesWithQuality(
    samples: List<SamsungWorkoutTimeSample>,
    startedAt: java.time.Instant,
    endedAt: java.time.Instant,
): SanitizedSamsungTimeSeries {
    var heartRateConflictCount = 0
    var speedConflictCount = 0
    val sanitized = samples.asSequence()
        .filter { it.measuredAt >= startedAt && it.measuredAt <= endedAt }
        .map { sample ->
            sample.copy(
                heartRate = sample.heartRate?.takeIf { it.isFinite() && it > 0f && it <= 300f },
                speedMetersPerSecond = sample.speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0f && it <= 15f },
            )
        }
        .filter { it.heartRate != null || it.speedMetersPerSecond != null }
        .groupBy { it.measuredAt }
        .toSortedMap()
        .mapNotNull { (measuredAt, sameTime) ->
            val heartRates = sameTime.mapNotNull { it.heartRate }.distinct().sorted()
            val speeds = sameTime.mapNotNull { it.speedMetersPerSecond }.distinct().sorted()
            val heartRate = when (heartRates.size) {
                0 -> null
                1 -> heartRates.single()
                else -> null.also { heartRateConflictCount++ }
            }
            val speed = when (speeds.size) {
                0 -> null
                1 -> speeds.single()
                else -> null.also { speedConflictCount++ }
            }
            if (heartRate == null && speed == null) null
            else SamsungWorkoutTimeSample(measuredAt, heartRate, speed)
        }
        .take(MAX_TIME_SERIES_SAMPLES)
    return SanitizedSamsungTimeSeries(
        samples = sanitized,
        quality = SamsungTimeSeriesQuality(
            heartRateConflictTimestampCount = heartRateConflictCount,
            speedConflictTimestampCount = speedConflictCount,
        ),
    )
}

internal const val MAX_TIME_SERIES_SAMPLES = 10_000
