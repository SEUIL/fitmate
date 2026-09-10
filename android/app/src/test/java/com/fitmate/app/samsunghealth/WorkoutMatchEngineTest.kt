package com.fitmate.app.samsunghealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkoutMatchEngineTest {
    private val start = Instant.parse("2026-09-10T01:00:00Z")
    private val end = Instant.parse("2026-09-10T01:30:00Z")

    @Test fun `matches exact allowed type inside ten minute boundaries`() {
        val earlyBoundary = workout("b", "app", "BENCH_PRESS", start.minusSeconds(600), end.plusSeconds(600))
        val exact = workout("a", "app", "BENCH_PRESS", start, end)
        assertEquals(listOf("a", "b"), WorkoutMatchEngine.candidates("HEALTH", start, end, listOf(earlyBoundary, exact)).map { it.uid })
    }

    @Test fun `rejects mismatch outside boundary and compound exercise`() {
        val mismatch = workout("a", null, "RUNNING", start, end)
        val late = workout("b", null, "BENCH_PRESS", start.plusSeconds(601), end)
        val compound = workout("c", null, "BENCH_PRESS", start, end, sessionCount = 2)
        assertTrue(WorkoutMatchEngine.candidates("HEALTH", start, end, listOf(mismatch, late, compound)).isEmpty())
    }

    @Test fun `deduplicates only the same uid and source app`() {
        val first = workout("same", null, "BENCH_PRESS", start, end)
        val duplicate = workout("same", null, "BENCH_PRESS", start, end)
        val otherApp = workout("same", "app", "BENCH_PRESS", start, end)
        assertEquals(2, WorkoutMatchEngine.candidates("HEALTH", start, end, listOf(first, duplicate, otherApp)).size)
    }

    private fun workout(
        uid: String, app: String?, type: String, from: Instant, to: Instant, sessionCount: Int = 1,
    ) = SamsungWorkoutUiModel(
        uid, app, from, null, type, null, from, to, java.time.Duration.between(from, to).seconds,
        sessionCount, null, null, null, null, null, null, null, null, null, null, null, 0,
    )
}
