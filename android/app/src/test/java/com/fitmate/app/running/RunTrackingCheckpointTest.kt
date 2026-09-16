package com.fitmate.app.running

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTrackingCheckpointTest {
    @Test fun `active duration excludes a paused interval while elapsed duration preserves it`() {
        val running = RunTrackingCheckpoint(
            startedAtEpochMillis = 100_000L,
            activeBeforePauseMillis = 30_000L,
            activeSegmentStartedElapsedMillis = 1_000L,
            paused = false,
            finished = false,
        )
        assertEquals(31_000L, running.activeMillis(2_000L))

        val paused = running.copy(
            activeBeforePauseMillis = running.activeMillis(2_000L),
            activeSegmentStartedElapsedMillis = 0L,
            paused = true,
        )
        assertEquals(31_000L, paused.activeMillis(62_000L))

        val resumed = paused.copy(activeSegmentStartedElapsedMillis = 62_000L, paused = false)
        assertEquals(32_000L, resumed.activeMillis(63_000L))
        val finished = resumed.copy(
            activeBeforePauseMillis = resumed.activeMillis(63_000L),
            activeSegmentStartedElapsedMillis = 0L,
            paused = true,
            finished = true,
            completedAtEpochMillis = 200_000L,
        )

        val update = finished.update(nowWallMillis = 999_000L, nowElapsedMillis = 999_000L)
        assertTrue(update.finished)
        assertEquals(32, update.activeDurationSeconds)
        assertEquals(100, update.elapsedSeconds)
        assertEquals(200_000L, update.completedAtEpochMillis)
    }
}
