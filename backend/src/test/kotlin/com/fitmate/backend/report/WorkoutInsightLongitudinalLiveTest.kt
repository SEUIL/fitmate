package com.fitmate.backend.report

import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightRules
import com.fitmate.backend.report.infrastructure.*
import com.fitmate.backend.recommendation.infrastructure.WorkoutRecommendationAiProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Paid provider calls are explicitly opted in. Synthetic values only, no payload files or logs. */
@EnabledIfEnvironmentVariable(named = "FITMATE_LIVE_WORKOUT_INSIGHT_TEST", matches = "true")
class WorkoutInsightLongitudinalLiveTest {
    @Test fun `longitudinal generation passes validation and independent review`() {
        val base = insightSnapshot()
        val current = base.report.copy(exercises = base.report.exercises.map { it.copy(completedSets = listOf(
            WorkoutReportSet(BigDecimal.TEN, 10, 90, 7), WorkoutReportSet(BigDecimal.TEN, 10, 90, 8))) })
        val histories = listOf(21L, 14L, 7L).map { days -> InsightHistorySession(current.copy(
            workoutSessionId = UUID.randomUUID(), startedAt = current.startedAt.minusSeconds(days * 86400),
            endedAt = current.endedAt.minusSeconds(days * 86400)), InsightSamsungSummary(1500, BigDecimal("200"), BigDecimal("115"))) }
        val bodies = listOf(21L, 14L, 7L).map { days -> InsightBodyComposition(LocalDate.of(2026,1,1).minusDays(days), BigDecimal("22"), BigDecimal("30")) }
        val h = InsightLongitudinal(4, current.endedAt.minusSeconds(28*86400), current.endedAt, true, histories, bodies,
            listOf(InsightPreviousAdvice(histories.last().report.workoutSessionId, histories.last().report.endedAt,
                listOf(InsightPoint("demo", "이전 수행과 체감 강도를 비교해요", "다음 동일 운동에서 반복 수행 여부와 체감 강도를 함께 기록해 보세요.", emptyList())))))
        val snapshot = base.copy(report = current, inbody = bodies.last(), samsungHealth = InsightSamsungSummary(1500, BigDecimal("210"), BigDecimal("120")), longitudinal = h)
        val app = SpringApplication(OpenAiWorkoutInsightLiveTest.LiveConfig::class.java).apply { webApplicationType = WebApplicationType.NONE }
        app.run("--spring.main.banner-mode=off").use { beans ->
            val properties = beans.getBean(WorkoutRecommendationAiProperties::class.java)
            check(properties.apiKey.isNotBlank()) { "AI key missing" }
            val rules = WorkoutInsightRules()
            val selected = OpenAiWorkoutInsight(RestClient.builder(), properties, true).select(snapshot, rules.catalog(snapshot),
                CreateWorkoutInsightRequest(UUID.randomUUID(), externalAiConsent = true, consentVersion = WorkoutInsightConsent.VERSION))
            assertNotNull(selected, "A fallback is not AI success; inspect the safe validation code.")
        }
    }
}
