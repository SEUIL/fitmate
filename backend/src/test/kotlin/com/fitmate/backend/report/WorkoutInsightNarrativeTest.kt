package com.fitmate.backend.report

import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightNarrative
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

internal fun narrativeFixture() = InsightNarrative(
    InsightNarrativePoint("운동 습관을 이어갈 기준이 생겼어요", "운동 습관을 목표로 수행한 기록입니다. 이번 기록을 기준으로 다음 운동의 수행 조건을 비교해 보세요.", listOf("profile", "session")),
    listOf(InsightNarrativePoint("비교 기준을 유지해요", "{{exercise-0}}의 기록은 남아 있지만 이전 비교가 없어 변화 방향을 판단하기는 어려워요.", listOf("exercise-0")),
        InsightNarrativePoint("목표와 기록을 함께 보세요", "운동 습관이라는 목표에 맞춰 다음 운동에서도 수행한 내용을 꾸준히 남겨 비교해 보세요.", listOf("profile", "session"))),
    listOf(InsightNarrativePoint("다음 수행 조건을 비교해요", "{{exercise-0}}을 다시 할 때 동작 기준을 유지하고 반복 수와 체감 강도를 함께 비교해 보세요.", listOf("exercise-0"))))

class WorkoutInsightNarrativeTest {
    @Test fun `narrative renders exact server evidence and local exercise names`() {
        val s = insightSnapshot()
        val result = WorkoutInsightNarrative.result(s, narrativeFixture())
        assertEquals("grounded-narrative-v2", result.methodVersion)
        assertTrue(result.observations.first().explanation.contains(s.report.exercises.first().exerciseName))
        assertTrue(result.observations.first().evidence.first().contains("200kg"))
    }
    @Test fun `invented quantities unknown references missing citations and duplicate points rejected`() {
        val n = narrativeFixture()
        val bad = listOf(n.copy(summary = n.summary.copy(explanation = "이번 운동은 999kg을 수행하여 이전보다 크게 증가했어요.")),
            n.copy(summary = n.summary.copy(evidenceIds = listOf("unknown"))),
            n.copy(summary = n.summary.copy(evidenceIds = listOf("session"))),
            n.copy(observations = listOf(n.observations.first(), n.observations.first())),
            n.copy(nextActions = listOf(n.nextActions.first().copy(explanation = "{{exercise-9}}의 수행 기준을 다음에도 유지해 비교해 보세요."))),
            n.copy(nextActions = emptyList()))
        bad.forEach { assertThrows(IllegalArgumentException::class.java) { WorkoutInsightNarrative.result(insightSnapshot(), it) } }
    }
    @Test fun `optional sources require coverage and missing sources cannot be cited`() {
        val full = insightSnapshot().copy(inbody = InsightBodyComposition(LocalDate.of(2026,1,1), BigDecimal("22"), BigDecimal("34")), samsungHealth = InsightSamsungSummary(1700, null, null))
        assertThrows(IllegalArgumentException::class.java) { WorkoutInsightNarrative.result(full, narrativeFixture()) }
        val n = narrativeFixture().let { it.copy(observations = it.observations + InsightNarrativePoint("측정 기록은 참고 정보예요", "체성분과 기기 활동 기록이 있지만 단일 측정만으로 운동 효과를 판단할 수는 없어요.", listOf("inbody", "samsung"))) }
        assertNotNull(WorkoutInsightNarrative.result(full, n))
        assertThrows(IllegalArgumentException::class.java) { WorkoutInsightNarrative.result(insightSnapshot(), n) }
    }
    @Test fun `minimized facts retain effort rest and relative measurement age without identities`() {
        val s = insightSnapshot().let { it.copy(report = it.report.copy(exercises = it.report.exercises.map { e -> e.copy(completedSets = listOf(WorkoutReportSet(BigDecimal.TEN,10,60,7))) })) }
        val facts = WorkoutInsightNarrative.facts(s)
        assertEquals(s.report.exercises.first().completedSets, facts.getValue("exercise-0")["completedSets"])
        val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(facts)
        assertFalse(json.contains(s.report.workoutSessionId.toString()))
        assertFalse(json.contains(s.report.exercises.first().exerciseName))
        assertFalse(json.contains("startedAt"))
        assertTrue(json.contains("\"rpe\":7"))
        assertTrue(json.contains("\"restSeconds\":60"))
    }

    @Test fun `old persisted reports without set detail remain readable`() {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().findAndRegisterModules()
        val json = mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(insightSnapshot())
        (json["report"]["exercises"][0] as com.fasterxml.jackson.databind.node.ObjectNode).remove("completedSets")
        assertTrue(mapper.treeToValue(json, WorkoutInsightSnapshot::class.java).report.exercises.first().completedSets.isEmpty())
    }

    @Test fun `exercise placeholders choose Korean particles from the actual name`() {
        val s = insightSnapshot().let { it.copy(report = it.report.copy(exercises = it.report.exercises.map { e -> e.copy(exerciseName = "벤치 프레스") })) }
        val n = narrativeFixture().let { it.copy(observations = listOf(it.observations.first().copy(explanation = "{{exercise-0}}은 이전 비교가 없어 다음 기록과 수행 조건을 비교해 보세요."), it.observations.last())) }
        assertTrue(WorkoutInsightNarrative.result(s, n).observations.first().explanation.startsWith("벤치 프레스는"))
    }
}
