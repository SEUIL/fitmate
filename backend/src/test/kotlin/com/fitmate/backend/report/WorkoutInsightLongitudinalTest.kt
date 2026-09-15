package com.fitmate.backend.report

import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightNarrative
import com.fitmate.backend.report.application.WorkoutInsightRules
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class WorkoutInsightLongitudinalTest {
    @Test fun `blind evaluation uses regular input without mutating fixture provenance`() {
        val s = insightSnapshot()
        val advice = InsightPoint("fixture", "[DEMO] 다음 운동의 체감 강도를 비교해요",
            "다음 동일 운동에서 반복 수행 여부를 확인해 보세요. 시연용 과거 제안이며 실제 이행 여부는 알 수 없습니다.", emptyList())
        val h = InsightLongitudinal(4, s.report.startedAt.minusSeconds(28*86400), s.report.endedAt, true,
            emptyList(), emptyList(), listOf(InsightPreviousAdvice(s.report.workoutSessionId, s.report.startedAt, listOf(advice))))
        val snapshot = s.copy(longitudinal = h)
        val normal = WorkoutInsightNarrative.facts(snapshot)
        val blind = WorkoutInsightNarrative.facts(snapshot, blindFixtureEvaluation = true)
        assertEquals(true, normal.getValue("history")["demo"])
        assertEquals(false, blind.getValue("history")["demo"])
        val json = jacksonObjectMapper().writeValueAsString(blind)
        assertFalse(json.contains("[DEMO]")); assertFalse(json.contains("시연용"))
        assertTrue(json.contains("실제 이행 여부는 알 수 없습니다."))
        assertTrue(snapshot.longitudinal!!.demo)
        assertEquals(advice, snapshot.longitudinal!!.previousAdvice.single().actions.single())
        assertEquals(normal["session"], blind["session"])
        assertEquals(normal["exercise-0"], blind["exercise-0"])
    }
    @Test fun `prior advice is mandatory in generation and validation`() {
        val s = insightSnapshot()
        val h = InsightLongitudinal(4, s.report.startedAt.minusSeconds(28*86400), s.report.endedAt, true,
            emptyList(), emptyList(), listOf(InsightPreviousAdvice(s.report.workoutSessionId, s.report.startedAt.minusSeconds(86400),
                listOf(InsightPoint("demo", "이전 기록과 비교해요", "다음 수행의 체감 강도를 기록하고 비교해 보세요.", emptyList())))))
        val snapshot = s.copy(longitudinal = h)
        val missing = narrativeFixture().let { n -> n.copy(observations = n.observations.map { it.copy(evidenceIds = it.evidenceIds + "history") }) }
        val failure = assertThrows(WorkoutInsightNarrative.ValidationFailure::class.java) { WorkoutInsightNarrative.result(snapshot, missing) }
        assertEquals("PREVIOUS_ADVICE_COVERAGE_MISSING", failure.code)
        val instructions = com.fitmate.backend.report.infrastructure.OpenAiWorkoutInsight.coverageInstructions(snapshot)
        assertTrue(instructions.contains("history, previous-advice"))
        val fixed = missing.copy(nextActions = missing.nextActions.map { it.copy(evidenceIds = it.evidenceIds + "previous-advice") })
        assertEquals("AI", WorkoutInsightNarrative.result(snapshot, fixed).source)
    }
    @Test fun `history facts omit identifiers and retain linked device and multiple measurements`() {
        val s = insightSnapshot()
        val earlier = s.report.copy(startedAt = s.report.startedAt.minusSeconds(86400), endedAt = s.report.endedAt.minusSeconds(86400))
        val h = InsightLongitudinal(4, s.report.endedAt.minusSeconds(28*86400), s.report.endedAt, true,
            listOf(InsightHistorySession(earlier, InsightSamsungSummary(1200, BigDecimal.TEN, BigDecimal("110")))),
            listOf(InsightBodyComposition(LocalDate.of(2025,12,20), BigDecimal("21"), BigDecimal("30")),
                InsightBodyComposition(LocalDate.of(2025,12,30), BigDecimal("20"), BigDecimal("31"))), emptyList())
        val snapshot = s.copy(longitudinal = h)
        val facts = WorkoutInsightNarrative.facts(snapshot)
        assertEquals(2, facts.getValue("inbody")["measurementCount"])
        assertTrue(facts.containsKey("samsung"))
        assertEquals(true, facts.getValue("history")["demo"])
        val json = jacksonObjectMapper().writeValueAsString(facts)
        assertFalse(json.contains(s.report.workoutSessionId.toString()))
        assertFalse(json.contains(s.report.exercises.first().exerciseId.toString()))
        assertFalse(json.contains(s.report.exercises.first().exerciseName))
        assertFalse(json.contains("startedAt"))
        assertThrows(IllegalArgumentException::class.java) { WorkoutInsightNarrative.result(snapshot, narrativeFixture()) }
        val rules = WorkoutInsightRules(); val catalog = rules.catalog(snapshot)
        assertEquals("history-summary", rules.fallback(catalog).summaryId)
    }
    @Test fun `old persisted snapshot still has no history and reads unchanged`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val tree = mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(insightSnapshot())
        tree.remove("longitudinal")
        assertNull(mapper.treeToValue(tree, WorkoutInsightSnapshot::class.java).longitudinal)
    }
}
