package com.fitmate.app.samsunghealth.debug

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

internal enum class MetricAvailability(val label: String) {
    PROVIDED("제공됨"),
    PARTIAL("일부 제공"),
    NOT_PROVIDED("미제공"),
    UNKNOWN("조회 실패로 미확정"),
}

internal enum class Fit3MetadataMatch(val label: String) {
    MATCH("기기 메타데이터에 Fit3 표시 있음"),
    NOT_MATCH("기기 메타데이터에 Fit3 표시 없음"),
    UNAVAILABLE("기기 메타데이터 없음"),
}

internal data class ProbeLogSample(
    val timestamp: Instant,
    val heartRate: Float?,
    val speedMetersPerSecond: Float?,
)

internal data class ProbeRunningRecord(
    val sourceRecordId: String,
    val exerciseType: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val sourceModifiedAt: Instant?,
    val activeDurationSeconds: Long,
    val exerciseSessionCount: Int,
    val samples: List<ProbeLogSample>,
    val fit3MetadataMatch: Fit3MetadataMatch,
)

internal data class IntervalDistribution(
    val count: Int,
    val minMillis: Long?,
    val medianMillis: Long?,
    val p90Millis: Long?,
    val maxMillis: Long?,
    val underOneSecond: Int,
    val oneToFiveSeconds: Int,
    val fiveToFifteenSeconds: Int,
    val fifteenToSixtySeconds: Int,
    val atLeastSixtySeconds: Int,
    val gapThresholdMillis: Long?,
    val gapCount: Int,
    val maxGapMillis: Long?,
)

internal data class MetricProbeSummary(
    val availability: MetricAvailability,
    val sampleCount: Int,
    val missingOrInvalidCount: Int,
    val firstSampleOffsetMillis: Long?,
    val lastSampleOffsetMillis: Long?,
    val coveragePercent: Int?,
    val outsideSessionCount: Int,
    val nonIncreasingTimestampCount: Int,
    val intervals: IntervalDistribution,
)

internal data class PauseEvidence(
    val explicitPauseFieldAvailable: Boolean,
    val elapsedDurationSeconds: Long,
    val activeDurationSeconds: Long,
    val inactiveDifferenceSeconds: Long,
    val zeroSpeedSampleCount: Int,
    val allLogGapCount: Int,
    val allLogMaxGapMillis: Long?,
)

internal data class RepeatQueryConsistency(
    val queryCount: Int,
    val sameSourceRecord: Boolean,
    val sameModifiedTime: Boolean,
    val sameSampleCounts: Boolean,
    val sameAnalyzedContent: Boolean,
)

internal data class Fit3ProbeReport(
    val sourceRecordIdProvided: Boolean,
    val exerciseType: String,
    val elapsedDurationSeconds: Long,
    val sourceModifiedTimeProvided: Boolean,
    val modifiedAfterEndSeconds: Long?,
    val exerciseSessionCount: Int,
    val exerciseLogExists: Boolean,
    val exerciseLogSampleCount: Int,
    val heartRate: MetricProbeSummary,
    val speed: MetricProbeSummary,
    val pauseEvidence: PauseEvidence,
    val consistency: RepeatQueryConsistency,
    val fit3MetadataMatch: Fit3MetadataMatch,
)

internal object Fit3DataProbeAnalyzer {
    fun analyze(first: ProbeRunningRecord, second: ProbeRunningRecord): Fit3ProbeReport {
        val elapsedSeconds = nonNegativeDurationSeconds(second.startedAt, second.endedAt)
        val allLogIntervals = intervalDistribution(second.samples.map { it.timestamp })
        val heartRate = metricSummary(second) { value -> value.heartRate?.takeIf { it > 0f } }
        val speed = metricSummary(second) { value -> value.speedMetersPerSecond?.takeIf { it >= 0f } }
        return Fit3ProbeReport(
            sourceRecordIdProvided = second.sourceRecordId.isNotBlank(),
            exerciseType = second.exerciseType,
            elapsedDurationSeconds = elapsedSeconds,
            sourceModifiedTimeProvided = second.sourceModifiedAt != null,
            modifiedAfterEndSeconds = second.sourceModifiedAt?.let { Duration.between(second.endedAt, it).seconds },
            exerciseSessionCount = second.exerciseSessionCount,
            exerciseLogExists = second.samples.isNotEmpty(),
            exerciseLogSampleCount = second.samples.size,
            heartRate = heartRate,
            speed = speed,
            pauseEvidence = PauseEvidence(
                explicitPauseFieldAvailable = false,
                elapsedDurationSeconds = elapsedSeconds,
                activeDurationSeconds = second.activeDurationSeconds,
                inactiveDifferenceSeconds = max(0L, elapsedSeconds - second.activeDurationSeconds),
                zeroSpeedSampleCount = second.samples.count { it.speedMetersPerSecond == 0f },
                allLogGapCount = allLogIntervals.gapCount,
                allLogMaxGapMillis = allLogIntervals.maxGapMillis,
            ),
            consistency = RepeatQueryConsistency(
                queryCount = 2,
                sameSourceRecord = first.sourceRecordId == second.sourceRecordId &&
                    first.startedAt == second.startedAt && first.endedAt == second.endedAt,
                sameModifiedTime = first.sourceModifiedAt == second.sourceModifiedAt,
                sameSampleCounts = first.samples.size == second.samples.size &&
                    first.samples.count { it.heartRate?.let { rate -> rate > 0f } == true } == heartRate.sampleCount &&
                    first.samples.count { it.speedMetersPerSecond?.let { speedValue -> speedValue >= 0f } == true } == speed.sampleCount,
                sameAnalyzedContent = first == second,
            ),
            fit3MetadataMatch = second.fit3MetadataMatch,
        )
    }

    private fun metricSummary(
        record: ProbeRunningRecord,
        value: (ProbeLogSample) -> Float?,
    ): MetricProbeSummary {
        val validInOriginalOrder = record.samples.filter { value(it) != null }
        val ordered = validInOriginalOrder.sortedBy { it.timestamp }
        val intervals = intervalDistribution(ordered.map { it.timestamp })
        val elapsedMillis = nonNegativeDurationMillis(record.startedAt, record.endedAt)
        val firstOffset = ordered.firstOrNull()?.let { Duration.between(record.startedAt, it.timestamp).toMillis() }
        val lastOffset = ordered.lastOrNull()?.let { Duration.between(record.startedAt, it.timestamp).toMillis() }
        val coverage = if (ordered.size >= 2 && elapsedMillis > 0L) {
            ((Duration.between(ordered.first().timestamp, ordered.last().timestamp).toMillis() * 100.0) / elapsedMillis)
                .coerceIn(0.0, 100.0).toInt()
        } else null
        val outside = ordered.count { it.timestamp < record.startedAt || it.timestamp > record.endedAt }
        val nonIncreasing = validInOriginalOrder.zipWithNext().count { (left, right) -> !right.timestamp.isAfter(left.timestamp) }
        val missing = record.samples.size - ordered.size
        val availability = when {
            ordered.isEmpty() -> MetricAvailability.NOT_PROVIDED
            ordered.size < 3 || missing > 0 || outside > 0 || nonIncreasing > 0 ||
                intervals.gapCount > 0 || coverage == null || coverage < 80 -> MetricAvailability.PARTIAL
            else -> MetricAvailability.PROVIDED
        }
        return MetricProbeSummary(
            availability = availability,
            sampleCount = ordered.size,
            missingOrInvalidCount = missing,
            firstSampleOffsetMillis = firstOffset,
            lastSampleOffsetMillis = lastOffset,
            coveragePercent = coverage,
            outsideSessionCount = outside,
            nonIncreasingTimestampCount = nonIncreasing,
            intervals = intervals,
        )
    }

    private fun intervalDistribution(timestamps: List<Instant>): IntervalDistribution {
        val sorted = timestamps.sorted()
        val values = sorted.zipWithNext().map { (left, right) -> Duration.between(left, right).toMillis() }
            .filter { it >= 0L }.sorted()
        val median = percentile(values, 0.5)
        val gapThreshold = median?.let { if (values.size < 3) 15_000L else max(15_000L, it * 3L) }
        val gaps = gapThreshold?.let { threshold -> values.filter { it > threshold } }.orEmpty()
        return IntervalDistribution(
            count = values.size,
            minMillis = values.firstOrNull(),
            medianMillis = median,
            p90Millis = percentile(values, 0.9),
            maxMillis = values.lastOrNull(),
            underOneSecond = values.count { it < 1_000L },
            oneToFiveSeconds = values.count { it in 1_000L until 5_000L },
            fiveToFifteenSeconds = values.count { it in 5_000L until 15_000L },
            fifteenToSixtySeconds = values.count { it in 15_000L until 60_000L },
            atLeastSixtySeconds = values.count { it >= 60_000L },
            gapThresholdMillis = gapThreshold,
            gapCount = gaps.size,
            maxGapMillis = gaps.maxOrNull(),
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long? {
        if (sortedValues.isEmpty()) return null
        val index = ceil(percentile * sortedValues.size).toInt().coerceIn(1, sortedValues.size) - 1
        return sortedValues[index]
    }

    private fun nonNegativeDurationSeconds(start: Instant, end: Instant): Long =
        max(0L, Duration.between(start, end).seconds)

    private fun nonNegativeDurationMillis(start: Instant, end: Instant): Long =
        max(0L, Duration.between(start, end).toMillis())
}
