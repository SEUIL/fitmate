package com.fitmate.app.running

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRouteCollectorTest {
    @Test fun `enabled route keeps valid chronological points and optional altitude`() {
        val collector = RunRouteCollector(recordRoute = true)

        assertNotNull(collector.accept(sample(0.0, 0.0, 1_000L, altitude = 12.0)))
        val next = collector.accept(sample(0.0, 0.0001, 11_000L))

        assertNotNull(next)
        assertTrue(requireNotNull(next).distanceMeters > 0.0)
        assertEquals(2, collector.routePoints().size)
        assertEquals(12.0, collector.routePoints().first().altitudeMeters)
    }

    @Test fun `paused segment does not bridge distance on resume`() {
        val collector = RunRouteCollector(recordRoute = true)
        collector.accept(sample(0.0, 0.0, 1_000L))
        collector.pauseSegment()

        val resumed = requireNotNull(collector.accept(sample(0.0, 0.5, 2_000L)))

        assertEquals(0.0, resumed.distanceMeters, 0.0)
        assertEquals(2, collector.routePoints().size)
    }

    @Test fun `route disabled never retains coordinates`() {
        val collector = RunRouteCollector(recordRoute = false)
        collector.accept(sample(0.0, 0.0, 1_000L))
        val next = requireNotNull(collector.accept(sample(0.0, 0.0001, 11_000L)))

        assertNull(next.routePoint)
        assertTrue(next.distanceMeters > 0.0)
        assertTrue(collector.routePoints().isEmpty())
    }

    @Test fun `inaccurate jump duplicate and reverse samples are rejected`() {
        val collector = RunRouteCollector(recordRoute = true)
        assertNull(collector.accept(sample(0.0, 0.0, 1_000L, accuracy = 51f)))
        assertNull(collector.accept(sample(0.0, 0.0, 1_001L, accuracy = -1f)))
        assertNotNull(collector.accept(sample(0.0, 0.0, 2_000L)))
        assertNull(collector.accept(sample(0.0, 0.0001, 2_000L)))
        assertNull(collector.accept(sample(0.0, 0.0001, 1_999L)))
        assertNull(collector.accept(sample(0.0, 0.5, 3_000L)))
        assertEquals(1, collector.routePoints().size)
    }

    private fun sample(
        latitude: Double,
        longitude: Double,
        recordedAt: Long,
        accuracy: Float = 5f,
        altitude: Double? = null,
    ) = RunLocationSample(latitude, longitude, recordedAt, accuracy, altitude)
}
