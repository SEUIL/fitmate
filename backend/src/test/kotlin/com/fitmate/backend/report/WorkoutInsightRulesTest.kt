package com.fitmate.backend.report

import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightRules
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal fun insightSnapshot() = WorkoutInsightSnapshot(
    WorkoutReportResponse(UUID.randomUUID(), "BASIC_READY", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:30:00Z"),
        1800, 1, 2, 20, BigDecimal("200"), listOf(WorkoutExerciseReportResponse(UUID.randomUUID(), "합성 운동", null, 2, 20, BigDecimal("200"), BigDecimal.TEN, null))),
    "HABIT", "BEGINNER", null, null, emptyList(),
)

class WorkoutInsightRulesTest {
    private val rules = WorkoutInsightRules()
    @Test fun `fallback works with or without optional sources and never adds durations`() {
        for (body in listOf(null, InsightBodyComposition(LocalDate.of(2026, 1, 1), BigDecimal("25"), BigDecimal("30")))) {
            for (samsung in listOf(null, InsightSamsungSummary(1700, BigDecimal("100"), null))) {
                val snapshot = insightSnapshot().copy(inbody = body, samsungHealth = samsung)
                val catalog = rules.catalog(snapshot)
                val result = rules.result(snapshot, catalog, rules.fallback(catalog), "RULE_BASED")
                assertEquals(1800, result.snapshot.report.durationSeconds)
                assertEquals(samsung?.activeDurationSeconds, result.snapshot.samsungHealth?.activeDurationSeconds)
                assertEquals(body != null, catalog.observations.any { it.id == "inbody-context" })
                assertFalse(catalog.observations.any { it.id == "samsung-heart" })
                assertTrue(result.nextActions.size in 1..3)
            }
        }
    }
    @Test fun `invalid unknown duplicate and excessive selections rejected`() {
        val snapshot = insightSnapshot(); val catalog = rules.catalog(snapshot); val valid = rules.fallback(catalog)
        listOf(valid.copy(summaryId = "invented"), valid.copy(observationIds = emptyList()),
            valid.copy(observationIds = listOf("exercise-0", "exercise-0")), valid.copy(actionIds = listOf("invented")),
            valid.copy(actionIds = List(4) { "record-effort" })).forEach {
            assertThrows(IllegalArgumentException::class.java) { rules.result(snapshot, catalog, it, "AI") }
        }
    }
    @Test fun `zero completed sets never produces success claim`() {
        val snapshot = insightSnapshot().let { it.copy(report = it.report.copy(completedSetCount = 0, totalRepetitions = 0, exercises = emptyList())) }
        val catalog = rules.catalog(snapshot)
        assertEquals("empty", rules.fallback(catalog).summaryId)
        assertEquals("missing", catalog.observations.single().id)
    }
    @Test fun `goal and skill change eligible advice without medical load prescription`() {
        listOf("HABIT", "MUSCLE_GAIN", "FITNESS", "RECORD_IMPROVEMENT", "DIET").forEach { goal ->
            val beginner = rules.catalog(insightSnapshot().copy(goal = goal))
            assertTrue(beginner.actions.any { it.id.startsWith("goal-") })
            assertTrue(beginner.actions.any { it.id == "skill-form" })
            val experienced = rules.catalog(insightSnapshot().copy(goal = goal, skillLevel = "OVER_THREE_YEARS"))
            assertFalse(experienced.actions.any { it.id == "skill-form" })
        }
    }
    @Test fun `comparison uses actual delta and retains previous evidence`() {
        val original = insightSnapshot()
        listOf(-100, 0, 100).forEach { delta ->
            val comparison = PreviousWorkoutComparisonResponse(UUID.randomUUID(), BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal(200 - delta), BigDecimal(200), BigDecimal(delta))
            val snapshot = original.copy(report = original.report.copy(exercises = listOf(original.report.exercises.single().copy(previousComparison = comparison))))
            val point = rules.catalog(snapshot).observations.first()
            assertTrue(point.title.contains(if (delta < 0) "줄었어요" else if (delta > 0) "늘었어요" else "같아요"))
            assertTrue(point.explanation.contains("단정하지"))
        }
    }
}
