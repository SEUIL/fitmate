package com.fitmate.backend.health.application

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class TimeSeriesValue(val measuredAt: Instant, val heartRate: BigDecimal?, val speedMps: BigDecimal?)
internal data class GpsSpeedValue(val measuredAt: Instant, val speedMps: BigDecimal)
internal data class AnalysisWindow(val startOffsetSeconds: Long, val endOffsetSeconds: Long, val changeBpm: BigDecimal)
internal data class PeakSegment(val startOffsetSeconds: Long, val endOffsetSeconds: Long, val sampleCount: Int)
internal data class HeartRateResult(
    val validSampleCount: Int, val coveragePercent: Int, val longGapCount: Int, val maxGapSeconds: Long,
    val peakBpm: BigDecimal?, val peakOffsetSeconds: Long?, val peakSegment: PeakSegment?,
    val biggestRise: AnalysisWindow?, val biggestFall: AnalysisWindow?, val unavailableReason: String?,
)
internal data class PaceResult(
    val samsungSampleCount: Int, val gpsSampleCount: Int, val samsungSourceUsed: Boolean,
    val gpsSourceUsed: Boolean, val samsungFastestPaceSecondsPerKm: Int?,
    val smartphoneGpsFastestPaceSecondsPerKm: Int?, val unavailableReason: String?,
)
internal data class AlignmentResult(val alignedPairCount: Int, val windowSeconds: Long = ALIGNMENT_WINDOW_SECONDS)
internal data class DeterministicAnalysis(val heartRate: HeartRateResult, val pace: PaceResult, val alignment: AlignmentResult)

/** Deterministic observations only. It never labels gaps as a pause or makes health inferences. */
internal object ExternalWorkoutTimeSeriesAnalyzer {
    private const val MAX_CONTIGUOUS_GAP_SECONDS = 60L
    private const val CHANGE_MIN_SECONDS = 30L
    private const val CHANGE_MAX_SECONDS = 60L
    private const val MIN_HEART_SAMPLES = 3
    private const val MIN_PACE_SECONDS_PER_KM = 120.0
    private const val MAX_PACE_SECONDS_PER_KM = 1800.0
    private const val PEAK_RATIO = 0.95

    fun analyze(startedAt: Instant, endedAt: Instant, samples: List<TimeSeriesValue>, gps: List<GpsSpeedValue>): DeterministicAnalysis {
        val hearts = samples.mapNotNull { it.heartRate?.let { value -> it.measuredAt to value } }.sortedBy { it.first }
        val samsungPaces = samples.mapNotNull { sample -> sample.speedMps?.let { speed -> paceIfValid(sample.measuredAt, speed) } }.sortedBy { it.first }
        val gpsPaces = gps.mapNotNull { sample -> paceIfValid(sample.measuredAt, sample.speedMps) }
            .filter { gpsPoint -> samsungPaces.none { Duration.between(it.first, gpsPoint.first).abs().seconds <= ALIGNMENT_WINDOW_SECONDS } }
            .sortedBy { it.first }
        val allPaces = (samsungPaces.map { it.first } + gpsPaces.map { it.first }).sorted()
        return DeterministicAnalysis(
            heartRate = analyzeHeart(startedAt, endedAt, hearts),
            pace = PaceResult(samsungPaces.size, gpsPaces.size, samsungPaces.isNotEmpty(), gpsPaces.isNotEmpty(),
                samsungPaces.minOfOrNull { it.second.toInt() }, gpsPaces.minOfOrNull { it.second.toInt() },
                if (samsungPaces.isEmpty() && gpsPaces.isEmpty()) "PACE_DATA_UNAVAILABLE" else null),
            alignment = AlignmentResult(hearts.count { heart -> allPaces.any { Duration.between(heart.first, it).abs().seconds <= ALIGNMENT_WINDOW_SECONDS } }),
        )
    }

    private fun analyzeHeart(start: Instant, end: Instant, values: List<Pair<Instant, BigDecimal>>): HeartRateResult {
        val gaps = values.zipWithNext().map { Duration.between(it.first.first, it.second.first).seconds }
        val maxGap = gaps.maxOrNull() ?: 0L
        val longGaps = gaps.count { it > MAX_CONTIGUOUS_GAP_SECONDS }
        val covered = gaps.filter { it <= MAX_CONTIGUOUS_GAP_SECONDS }.sum()
        val elapsed = Duration.between(start, end).seconds.coerceAtLeast(1)
        if (values.size < MIN_HEART_SAMPLES) return HeartRateResult(values.size, (covered * 100 / elapsed).toInt(), longGaps, maxGap,
            null, null, null, null, null, "HEART_RATE_INSUFFICIENT_SAMPLES")
        val peak = values.maxWith(compareBy<Pair<Instant, BigDecimal>> { it.second }.thenBy { it.first })
        val threshold = peak.second.multiply(BigDecimal(PEAK_RATIO))
        val segments = mutableListOf<List<Pair<Instant, BigDecimal>>>()
        var current = mutableListOf<Pair<Instant, BigDecimal>>()
        values.forEach { value ->
            if (value.second >= threshold && (current.isEmpty() || Duration.between(current.last().first, value.first).seconds <= MAX_CONTIGUOUS_GAP_SECONDS)) current += value
            else { if (current.isNotEmpty()) segments += current; current = mutableListOf() }
        }
        if (current.isNotEmpty()) segments += current
        val segment = segments.filter { it.size >= 2 }.maxWithOrNull(compareBy<List<Pair<Instant, BigDecimal>>> { Duration.between(it.first().first, it.last().first).seconds }.thenBy { it.size })
            ?.let { PeakSegment(Duration.between(start, it.first().first).seconds, Duration.between(start, it.last().first).seconds, it.size) }
        val windows = buildList {
            values.indices.forEach { i ->
                for (j in i + 1 until values.size) {
                    val duration = Duration.between(values[i].first, values[j].first).seconds
                    if (duration > CHANGE_MAX_SECONDS) break
                    if (duration >= CHANGE_MIN_SECONDS && values.subList(i, j + 1).zipWithNext().all { Duration.between(it.first.first, it.second.first).seconds <= MAX_CONTIGUOUS_GAP_SECONDS }) {
                        add(AnalysisWindow(Duration.between(start, values[i].first).seconds, Duration.between(start, values[j].first).seconds, values[j].second.subtract(values[i].second)))
                    }
                }
            }
        }
        return HeartRateResult(values.size, (covered * 100 / elapsed).toInt(), longGaps, maxGap, peak.second,
            Duration.between(start, peak.first).seconds, segment,
            windows.filter { it.changeBpm > BigDecimal.ZERO }.maxByOrNull { it.changeBpm },
            windows.filter { it.changeBpm < BigDecimal.ZERO }.minByOrNull { it.changeBpm }, null)
    }

    private fun paceIfValid(time: Instant, speed: BigDecimal): Pair<Instant, BigDecimal>? {
        if (speed <= BigDecimal.ZERO) return null
        val pace = BigDecimal(1000).divide(speed, 0, RoundingMode.HALF_UP)
        return if (pace.toDouble() in MIN_PACE_SECONDS_PER_KM..MAX_PACE_SECONDS_PER_KM) time to pace else null
    }

    fun speedFromRoute(points: List<RoutePointForAnalysis>): List<GpsSpeedValue> = points.zipWithNext().mapNotNull { (a, b) ->
        val seconds = Duration.between(a.recordedAt, b.recordedAt).seconds
        if (seconds !in 2..MAX_CONTIGUOUS_GAP_SECONDS) null else {
            val distance = haversine(a.latitude, a.longitude, b.latitude, b.longitude)
            val speed = BigDecimal(distance / seconds).setScale(3, RoundingMode.HALF_UP)
            GpsSpeedValue(b.recordedAt, speed)
        }
    }

    private fun haversine(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val lat = Math.toRadians(bLat - aLat); val lon = Math.toRadians(bLon - aLon)
        val h = sin(lat / 2) * sin(lat / 2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(lon / 2) * sin(lon / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}

internal data class RoutePointForAnalysis(val latitude: Double, val longitude: Double, val recordedAt: Instant)
internal const val ALIGNMENT_WINDOW_SECONDS = 15L
