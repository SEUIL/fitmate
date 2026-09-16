package com.fitmate.backend.health.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ExternalWorkoutTimeSeriesAnalyzerTest {
    private val start = Instant.parse("2026-01-01T23:59:30Z")
    private val end = start.plusSeconds(180)
    private fun sample(offset: Long, heart: String? = null, speed: String? = null) =
        TimeSeriesValue(start.plusSeconds(offset), heart?.toBigDecimal(), speed?.toBigDecimal())

    @Test fun `normal irregular values calculate peak fixed window changes and Samsung pace`() {
        val result = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(
            sample(0, "100", "3.0"), sample(20, "110", "3.1"), sample(50, "145", "3.2"),
            sample(80, "140", "3.0"), sample(110, "120", "2.9"),
        ), emptyList())
        assertEquals(BigDecimal("145"), result.heartRate.peakBpm)
        assertNotNull(result.heartRate.biggestRise)
        assertNotNull(result.heartRate.biggestFall)
        assertEquals(5, result.heartRate.validSampleCount)
        assertEquals(5, result.pace.samsungSampleCount)
        assertTrue(result.pace.samsungSourceUsed)
        assertEquals(313, result.pace.samsungFastestPaceSecondsPerKm)
    }

    @Test fun `heart only speed only null and empty data remain distinct`() {
        val heartOnly = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(sample(0, "100"), sample(30, "110"), sample(60, "120")), emptyList())
        assertEquals(0, heartOnly.pace.samsungSampleCount)
        assertEquals("PACE_DATA_UNAVAILABLE", heartOnly.pace.unavailableReason)
        val speedOnly = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(sample(0, speed = "3.0"), sample(30, speed = "0")), emptyList())
        assertEquals(1, speedOnly.pace.samsungSampleCount)
        assertEquals("HEART_RATE_INSUFFICIENT_SAMPLES", speedOnly.heartRate.unavailableReason)
        val empty = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, emptyList(), emptyList())
        assertEquals(0, empty.heartRate.validSampleCount)
        assertEquals(0, empty.pace.samsungSampleCount)
    }

    @Test fun `two samples and long gap do not create false heart rate change windows`() {
        val sparse = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(sample(0, "100"), sample(120, "180")), emptyList())
        assertEquals("HEART_RATE_INSUFFICIENT_SAMPLES", sparse.heartRate.unavailableReason)
        val gap = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(sample(0, "100"), sample(40, "130"), sample(150, "80")), emptyList())
        assertEquals(1, gap.heartRate.longGapCount)
        assertNull(gap.heartRate.biggestFall)
    }

    @Test fun `GPS pace supplements only missing Samsung pace and exposes source`() {
        val route = listOf(
            RoutePointForAnalysis(0.0, 0.0, start),
            RoutePointForAnalysis(0.0, 0.0001, start.plusSeconds(10)),
            RoutePointForAnalysis(0.0, 0.0002, start.plusSeconds(20)),
        )
        val fallback = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(sample(0, "100")), ExternalWorkoutTimeSeriesAnalyzer.speedFromRoute(route))
        assertFalse(fallback.pace.samsungSourceUsed)
        assertTrue(fallback.pace.gpsSourceUsed)
        val samsung = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(sample(10, "100", "3.0")), ExternalWorkoutTimeSeriesAnalyzer.speedFromRoute(route))
        assertEquals(1, samsung.pace.samsungSampleCount)
        assertEquals(0, samsung.pace.gpsSampleCount)
    }

    @Test fun `invalid speeds produce no pace`() {
        val result = ExternalWorkoutTimeSeriesAnalyzer.analyze(start, end, listOf(
            sample(0, speed = "0"), sample(20, speed = "0.1"), sample(40, speed = "20"),
        ), emptyList())
        assertEquals(0, result.pace.samsungSampleCount)
    }
}
