package com.fitmate.app.samsunghealth.debug

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fit3DataProbeAnalyzerTest {
    private val start = Instant.parse("2026-09-15T01:00:00Z")

    @Test
    fun `complete heart rate and speed samples are provided`() {
        val record = record(
            samples = (0..10).map { second ->
                ProbeLogSample(start.plusSeconds(second.toLong()), 100f + second, 2.5f)
            },
            endSeconds = 10,
            activeSeconds = 10,
        )

        val report = Fit3DataProbeAnalyzer.analyze(record, record)

        assertEquals(MetricAvailability.PROVIDED, report.heartRate.availability)
        assertEquals(MetricAvailability.PROVIDED, report.speed.availability)
        assertEquals(10, report.heartRate.intervals.oneToFiveSeconds)
        assertEquals(100, report.heartRate.coveragePercent)
        assertTrue(report.consistency.sameAnalyzedContent)
    }

    @Test
    fun `missing values and a long gap are classified as partial`() {
        val record = record(
            samples = listOf(
                ProbeLogSample(start, 100f, 2f),
                ProbeLogSample(start.plusSeconds(1), null, 0f),
                ProbeLogSample(start.plusSeconds(2), 102f, null),
                ProbeLogSample(start.plusSeconds(40), 110f, 3f),
            ),
            endSeconds = 40,
            activeSeconds = 32,
        )

        val report = Fit3DataProbeAnalyzer.analyze(record, record)

        assertEquals(MetricAvailability.PARTIAL, report.heartRate.availability)
        assertEquals(MetricAvailability.PARTIAL, report.speed.availability)
        assertEquals(1, report.heartRate.missingOrInvalidCount)
        assertEquals(1, report.speed.missingOrInvalidCount)
        assertEquals(1, report.pauseEvidence.zeroSpeedSampleCount)
        assertEquals(8, report.pauseEvidence.inactiveDifferenceSeconds)
        assertEquals(1, report.pauseEvidence.allLogGapCount)
    }

    @Test
    fun `empty exercise log is not provided without synthetic success`() {
        val record = record(samples = emptyList(), endSeconds = 60, activeSeconds = 60)

        val report = Fit3DataProbeAnalyzer.analyze(record, record)

        assertFalse(report.exerciseLogExists)
        assertEquals(MetricAvailability.NOT_PROVIDED, report.heartRate.availability)
        assertEquals(MetricAvailability.NOT_PROVIDED, report.speed.availability)
        assertEquals(0, report.heartRate.sampleCount)
        assertNull(report.heartRate.intervals.medianMillis)
    }

    @Test
    fun `two sparse samples remain partial and expose the long gap`() {
        val record = record(
            samples = listOf(
                ProbeLogSample(start, 100f, 2f),
                ProbeLogSample(start.plusSeconds(60), 110f, 3f),
            ),
            endSeconds = 60,
            activeSeconds = 60,
        )

        val report = Fit3DataProbeAnalyzer.analyze(record, record)

        assertEquals(MetricAvailability.PARTIAL, report.heartRate.availability)
        assertEquals(1, report.heartRate.intervals.gapCount)
        assertEquals(60_000L, report.heartRate.intervals.maxGapMillis)
    }

    @Test
    fun `repeated query detects source and content changes`() {
        val first = record(
            samples = listOf(ProbeLogSample(start, 100f, 2f)),
            endSeconds = 10,
            activeSeconds = 10,
        )
        val second = first.copy(
            sourceModifiedAt = start.plusSeconds(20),
            samples = first.samples + ProbeLogSample(start.plusSeconds(10), 110f, 3f),
        )

        val report = Fit3DataProbeAnalyzer.analyze(first, second)

        assertTrue(report.consistency.sameSourceRecord)
        assertFalse(report.consistency.sameModifiedTime)
        assertFalse(report.consistency.sameSampleCounts)
        assertFalse(report.consistency.sameAnalyzedContent)
    }

    @Test
    fun `source id is reported only as availability`() {
        val report = Fit3DataProbeAnalyzer.analyze(record(sourceId = ""), record(sourceId = ""))

        assertFalse(report.sourceRecordIdProvided)
        assertEquals("RUNNING", report.exerciseType)
    }

    private fun record(
        sourceId: String = "raw-id-never-rendered",
        samples: List<ProbeLogSample> = listOf(
            ProbeLogSample(start, 100f, 2f),
            ProbeLogSample(start.plusSeconds(1), 101f, 2f),
        ),
        endSeconds: Long = 1,
        activeSeconds: Long = 1,
    ) = ProbeRunningRecord(
        sourceRecordId = sourceId,
        exerciseType = "RUNNING",
        startedAt = start,
        endedAt = start.plusSeconds(endSeconds),
        sourceModifiedAt = start.plusSeconds(endSeconds + 5),
        activeDurationSeconds = activeSeconds,
        exerciseSessionCount = 1,
        samples = samples,
        fit3MetadataMatch = Fit3MetadataMatch.MATCH,
    )
}
