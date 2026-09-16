package com.fitmate.app.samsunghealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `unsupported type stays reference only when custom title matches current exercise`() {
        val unsupported = workout("other", "app", "OTHER", start, end, customTitle = " 벤치   프레스 ")

        val analysis = WorkoutMatchEngine.analyze(
            "HEALTH", start, end, listOf(unsupported), listOf("벤치프레스"),
        )

        assertTrue(analysis.candidates.isEmpty())
        assertEquals(listOf("벤치프레스"), analysis.unsupported.single().matchingSessionExerciseNames)
    }

    @Test fun `explains supported time mismatch and compound separately`() {
        val timeMismatch = workout("late", null, "BENCH_PRESS", start.plusSeconds(601), end)
        val compound = workout("compound", null, "BENCH_PRESS", start, end, sessionCount = 2)

        val analysis = WorkoutMatchEngine.analyze("HEALTH", start, end, listOf(timeMismatch, compound))

        assertTrue(analysis.candidates.isEmpty())
        assertEquals(1, analysis.timeMismatchCount)
        assertEquals(1, analysis.compoundCount)
        assertTrue(analysis.unsupported.isEmpty())
    }

    @Test fun `allowed type does not require custom title or name match`() {
        val supported = workout("bench", null, "BENCH_PRESS", start, end)

        val analysis = WorkoutMatchEngine.analyze("HEALTH", start, end, listOf(supported), listOf("스쿼트"))

        assertEquals(listOf("bench"), analysis.candidates.map { it.uid })
    }

    @Test fun `scores mapped exercise and activity duration ahead of weaker evidence`() {
        val mapped = workout("mapped", "app", "SQUATS", start.plusSeconds(30), end, customTitle = "다른 제목")
        val titleOnly = workout("title", "app", "BENCH_PRESS", start, end, customTitle = "바벨 백 스쿼트")

        val analysis = WorkoutMatchEngine.analyze(
            "HEALTH", start, end, listOf(titleOnly, mapped), listOf("바벨 백 스쿼트"),
        )

        assertEquals(listOf("mapped", "title"), analysis.candidates.map { it.uid })
        assertTrue(analysis.recommendations.first().score > analysis.recommendations.last().score)
        assertTrue(analysis.recommendations.first().reasons.any { "운동 종류" in it })
        assertTrue(analysis.recommendations.last().reasons.any { "보조 근거" in it })
    }

    @Test fun `activity duration difference contributes to candidate ordering`() {
        val close = workout("close", "app", "BENCH_PRESS", start, end, activeSeconds = 1_780)
        val far = workout("far", "app", "BENCH_PRESS", start, end, activeSeconds = 600)

        val analysis = WorkoutMatchEngine.analyze("HEALTH", start, end, listOf(far, close))

        assertEquals(listOf("close", "far"), analysis.candidates.map { it.uid })
        assertEquals(20, analysis.recommendations.first().activeDurationDifferenceSeconds)
    }

    @Test fun `active duration takes priority over elapsed time with a pause`() {
        val pausedEnd = start.plusSeconds(40 * 60L)
        val source = workout("paused", "app", "RUNNING", start, pausedEnd, activeSeconds = 30 * 60L)

        val recommendation = WorkoutMatchEngine.analyze(
            "RUNNING", start, pausedEnd, listOf(source), sessionActiveDurationSeconds = 30 * 60L,
        ).recommendations.single()

        assertEquals(0, recommendation.activeDurationDifferenceSeconds)
        assertEquals(FitMateDurationBasis.ACTIVE_DURATION, recommendation.fitMateDurationBasis)
        assertTrue(recommendation.reasons.any { "FitMate 활동 시간" in it })
    }

    @Test fun `legacy record falls back to elapsed duration and explains the basis`() {
        val pausedEnd = start.plusSeconds(40 * 60L)
        val source = workout("legacy", "app", "RUNNING", start, pausedEnd, activeSeconds = 30 * 60L)

        val recommendation = WorkoutMatchEngine.analyze("RUNNING", start, pausedEnd, listOf(source))
            .recommendations.single()

        assertEquals(10 * 60L, recommendation.activeDurationDifferenceSeconds)
        assertEquals(FitMateDurationBasis.ELAPSED_FALLBACK, recommendation.fitMateDurationBasis)
        assertTrue(recommendation.reasons.any { "활동 시간이 없어" in it })
    }

    @Test fun `equal active and elapsed durations keep zero difference`() {
        val source = workout("same-duration", "app", "RUNNING", start, end, activeSeconds = 30 * 60L)

        val recommendation = WorkoutMatchEngine.analyze(
            "RUNNING", start, end, listOf(source), sessionActiveDurationSeconds = 30 * 60L,
        ).recommendations.single()

        assertEquals(0, recommendation.activeDurationDifferenceSeconds)
        assertEquals(FitMateDurationBasis.ACTIVE_DURATION, recommendation.fitMateDurationBasis)
    }

    @Test fun `larger time overlap sorts ahead when other evidence is equal`() {
        val smallerOverlap = workout(
            "smaller", "app", "BENCH_PRESS", start.plusSeconds(300), end.minusSeconds(300), activeSeconds = 1_800,
        )
        val largerOverlap = workout(
            "larger", "app", "BENCH_PRESS", start.plusSeconds(60), end.minusSeconds(60), activeSeconds = 1_800,
        )

        val analysis = WorkoutMatchEngine.analyze("HEALTH", start, end, listOf(smallerOverlap, largerOverlap))

        assertEquals(listOf("larger", "smaller"), analysis.candidates.map { it.uid })
        assertTrue(analysis.recommendations.first().overlapPercent > analysis.recommendations.last().overlapPercent)
    }

    @Test fun `type and title evidence are reported separately`() {
        val matching = workout("matching", "app", "BENCH_PRESS", start, end, customTitle = "벤치 프레스")
        val mismatch = workout("mismatch", "app", "BENCH_PRESS", start, end, customTitle = "다른 제목")

        val analysis = WorkoutMatchEngine.analyze(
            "HEALTH", start, end, listOf(mismatch, matching), listOf("벤치 프레스"),
        )

        assertEquals(MatchEvidence.MATCHED, analysis.recommendations.first().typeMatch)
        assertEquals(MatchEvidence.MATCHED, analysis.recommendations.first().titleMatch)
        assertEquals(MatchEvidence.NOT_MATCHED, analysis.recommendations.last().titleMatch)
    }

    @Test fun `matching title never reverses the required time condition`() {
        val outside = workout(
            "outside", "app", "BENCH_PRESS", start.plusSeconds(601), end.plusSeconds(601), customTitle = "벤치 프레스",
        )

        val analysis = WorkoutMatchEngine.analyze("HEALTH", start, end, listOf(outside), listOf("벤치 프레스"))

        assertTrue(analysis.candidates.isEmpty())
        assertEquals(1, analysis.timeMismatchCount)
    }

    @Test fun `weak overlap and evidence produce low confidence with cautions`() {
        val weak = workout(
            "weak", "app", "BENCH_PRESS", start.plusSeconds(540), end.plusSeconds(540),
            customTitle = "다른 제목", activeSeconds = 300,
        )

        val recommendation = WorkoutMatchEngine.analyze(
            "HEALTH", start, end, listOf(weak), listOf("스쿼트"),
        ).recommendations.single()

        assertEquals(MatchConfidence.LOW, recommendation.confidence)
        assertTrue(recommendation.requiresExplicitConfirmation)
        assertTrue(recommendation.cautions.isNotEmpty())
        assertTrue(recommendation.reasons.any { "보조 근거" in it })
    }

    @Test fun `tie order is stable by source key`() {
        val second = workout("b", "same-app", "BENCH_PRESS", start, end)
        val first = workout("a", "same-app", "BENCH_PRESS", start, end)

        assertEquals(
            listOf("a", "b"),
            WorkoutMatchEngine.analyze("HEALTH", start, end, listOf(second, first)).candidates.map { it.uid },
        )
    }

    @Test fun `cross midnight instant overlap is a candidate regardless of system date`() {
        val midnightStart = Instant.parse("2026-09-10T23:55:00Z")
        val midnightEnd = Instant.parse("2026-09-11T00:10:00Z")
        val source = workout(
            "midnight", null, "RUNNING",
            Instant.parse("2026-09-10T23:56:00Z"), Instant.parse("2026-09-11T00:09:00Z"),
        )

        val recommendation = WorkoutMatchEngine.analyze(
            "RUNNING", midnightStart, midnightEnd, listOf(source), sessionActiveDurationSeconds = 13 * 60L,
        ).recommendations.single()
        assertEquals("midnight", recommendation.workout.uid)
        assertEquals(FitMateDurationBasis.ACTIVE_DURATION, recommendation.fitMateDurationBasis)
    }

    @Test fun `touching boundary without overlap and unsupported records produce no candidate`() {
        val touching = workout("touching", null, "BENCH_PRESS", end, end.plusSeconds(60))
        val unsupported = workout("other", null, "OTHER", start, end, customTitle = "벤치 프레스")

        val analysis = WorkoutMatchEngine.analyze("HEALTH", start, end, listOf(touching, unsupported), listOf("벤치 프레스"))

        assertTrue(analysis.candidates.isEmpty())
        assertEquals(1, analysis.timeMismatchCount)
        assertEquals(1, analysis.unsupported.size)
    }

    @Test fun `direct FitMate health mappings expand while generic and unknown codes stay blocked`() {
        val supported = setOf(
            "BENCH_PRESS", "SQUATS", "LUNGES", "LEG_PRESSES", "LEG_EXTENSIONS", "LEG_CURLS",
            "LAT_PULLDOWNS", "DEADLIFTS", "SHOULDER_PRESSES", "LATERAL_RAISES", "PUSH_UPS",
            "PULL_UPS", "CRUNCH", "PLANK", "ARM_CURLS", "ARM_EXTENSIONS",
        )

        assertTrue(supported.all { WorkoutMatchEngine.isSupported("HEALTH", it) })
        assertTrue(WorkoutMatchEngine.isSupported("RUNNING", "RUNNING"))
        assertFalse(WorkoutMatchEngine.isSupported("HEALTH", "WEIGHT_MACHINE"))
        assertFalse(WorkoutMatchEngine.isSupported("HEALTH", "OTHER"))
    }

    @Test fun `already linked source key is excluded without hiding the same id from another app`() {
        val linked = workout("same", "app-a", "BENCH_PRESS", start, end)
        val otherApp = workout("same", "app-b", "BENCH_PRESS", start, end)

        val analysis = WorkoutMatchEngine.analyze(
            "HEALTH",
            start,
            end,
            listOf(linked, otherApp),
            excludedSourceKeys = setOf(linked.sourceKey()),
        )

        assertEquals(listOf("app-b"), analysis.candidates.map { it.sourceAppId })
        assertEquals(1, analysis.alreadyLinkedCount)
    }

    @Test fun `source modification and deletion stop final confirmation`() {
        val selected = workout("same", "app", "BENCH_PRESS", start, end)
        val refreshed = selected.copy(sourceModifiedAt = end.plusSeconds(1))

        assertEquals(ExternalWorkoutRecheck.UNCHANGED, recheckExternalWorkoutSnapshot(selected, selected.copy()))
        assertEquals(ExternalWorkoutRecheck.MODIFIED, recheckExternalWorkoutSnapshot(selected, refreshed))
        assertEquals(ExternalWorkoutRecheck.MISSING, recheckExternalWorkoutSnapshot(selected, null))
    }

    private fun workout(
        uid: String,
        app: String?,
        type: String,
        from: Instant,
        to: Instant,
        sessionCount: Int = 1,
        customTitle: String? = null,
        activeSeconds: Long = java.time.Duration.between(from, to).seconds,
    ) = SamsungWorkoutUiModel(
        uid, app, from, null, type, customTitle, from, to, activeSeconds,
        sessionCount, null, null, null, null, null, null, null, null, null, null, null, 0,
    )
}
