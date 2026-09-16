package com.fitmate.app.samsunghealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SamsungWorkoutTimeSeriesTest {
    private val start = Instant.parse("2026-01-01T23:59:30Z")
    private val end = start.plusSeconds(60)

    @Test fun `identical duplicate samples are collapsed without conflicts`() {
        val sample = SamsungWorkoutTimeSample(start, 100f, 3f)

        val result = sanitizeSamsungTimeSeriesWithQuality(listOf(sample, sample), start, end)

        assertEquals(listOf(sample), result.samples)
        assertEquals(SamsungTimeSeriesQuality(), result.quality)
    }

    @Test fun `heart rate conflict invalidates only heart rate at the timestamp`() {
        val result = sanitizeSamsungTimeSeriesWithQuality(
            listOf(
                SamsungWorkoutTimeSample(start, 100f, 3f),
                SamsungWorkoutTimeSample(start, 120f, 3f),
            ),
            start,
            end,
        )

        assertEquals(listOf(SamsungWorkoutTimeSample(start, null, 3f)), result.samples)
        assertEquals(1, result.quality.heartRateConflictTimestampCount)
        assertEquals(0, result.quality.speedConflictTimestampCount)
    }

    @Test fun `speed conflict invalidates only speed at the timestamp`() {
        val result = sanitizeSamsungTimeSeriesWithQuality(
            listOf(
                SamsungWorkoutTimeSample(start, 100f, 3f),
                SamsungWorkoutTimeSample(start, 100f, 4f),
            ),
            start,
            end,
        )

        assertEquals(listOf(SamsungWorkoutTimeSample(start, 100f, null)), result.samples)
        assertEquals(0, result.quality.heartRateConflictTimestampCount)
        assertEquals(1, result.quality.speedConflictTimestampCount)
    }

    @Test fun `both field conflicts omit the empty logical sample and retain quality counts`() {
        val result = sanitizeSamsungTimeSeriesWithQuality(
            listOf(
                SamsungWorkoutTimeSample(start, 100f, 3f),
                SamsungWorkoutTimeSample(start, 120f, 4f),
            ),
            start,
            end,
        )

        assertEquals(emptyList<SamsungWorkoutTimeSample>(), result.samples)
        assertEquals(1, result.quality.heartRateConflictTimestampCount)
        assertEquals(1, result.quality.speedConflictTimestampCount)
    }

    @Test fun `null and valid values preserve the valid value without replacing null with zero`() {
        val result = sanitizeSamsungTimeSeriesWithQuality(
            listOf(
                SamsungWorkoutTimeSample(start, null, 3f),
                SamsungWorkoutTimeSample(start, 100f, null),
            ),
            start,
            end,
        )

        assertEquals(listOf(SamsungWorkoutTimeSample(start, 100f, 3f)), result.samples)
        assertEquals(SamsungTimeSeriesQuality(), result.quality)
    }

    @Test fun `conflict result is independent of input order`() {
        val input = listOf(
            SamsungWorkoutTimeSample(start.plusSeconds(10), 120f, 4f),
            SamsungWorkoutTimeSample(start, 100f, 3f),
            SamsungWorkoutTimeSample(start, 110f, 3f),
            SamsungWorkoutTimeSample(start.plusSeconds(10), 120f, 2f),
        )

        val forward = sanitizeSamsungTimeSeriesWithQuality(input, start, end)
        val reversed = sanitizeSamsungTimeSeriesWithQuality(input.reversed(), start, end)

        assertEquals(forward, reversed)
    }

    @Test fun `sanitizer keeps partial samples but rejects invalid and out of range values`() {
        val result = sanitizeSamsungTimeSeries(
            listOf(
                SamsungWorkoutTimeSample(start.minusSeconds(1), 100f, 3f),
                SamsungWorkoutTimeSample(start, 100f, Float.NaN),
                SamsungWorkoutTimeSample(start.plusSeconds(10), null, 3f),
                SamsungWorkoutTimeSample(start.plusSeconds(20), Float.POSITIVE_INFINITY, 20f),
            ),
            start,
            end,
        )

        assertEquals(2, result.size)
        assertEquals(100f, result[0].heartRate)
        assertNull(result[0].speedMetersPerSecond)
        assertNull(result[1].heartRate)
        assertEquals(3f, result[1].speedMetersPerSecond)
    }
}
