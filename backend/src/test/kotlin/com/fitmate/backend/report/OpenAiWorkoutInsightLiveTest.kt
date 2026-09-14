package com.fitmate.backend.report

import com.fitmate.backend.recommendation.infrastructure.WorkoutRecommendationAiProperties
import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightRules
import com.fitmate.backend.report.infrastructure.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/** Explicit opt-in: synthetic data only; no database is opened. */
@EnabledIfEnvironmentVariable(named = "FITMATE_LIVE_WORKOUT_INSIGHT_TEST", matches = "true")
class OpenAiWorkoutInsightLiveTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WorkoutRecommendationAiProperties::class)
    class LiveConfig
    @Test fun `actual AI chooses valid grounded report with synthetic optional data`() {
        val app = SpringApplication(LiveConfig::class.java).apply { webApplicationType = WebApplicationType.NONE }
        app.run("--spring.main.banner-mode=off").use { beans ->
            val properties = beans.getBean(WorkoutRecommendationAiProperties::class.java)
            check(properties.apiKey.isNotBlank()) { "AI key missing; no request sent." }
            val snapshot = insightSnapshot().copy(inbody = InsightBodyComposition(LocalDate.of(2026, 1, 1), BigDecimal("25"), BigDecimal("30")),
                samsungHealth = InsightSamsungSummary(1700, BigDecimal("100"), BigDecimal("110")))
            val rules = WorkoutInsightRules(); val catalog = rules.catalog(snapshot)
            val selected = OpenAiWorkoutInsight(RestClient.builder(), properties, true).select(snapshot, catalog,
                CreateWorkoutInsightRequest(UUID.randomUUID(), externalAiConsent = true, consentVersion = WorkoutInsightConsent.VERSION))
            assertNotNull(selected, "Fallback does not count as actual AI success.")
            val result = rules.result(snapshot, catalog, requireNotNull(selected), "AI")
            File("build/insight-narrative-habit-live.json").writeText(jacksonObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter().writeValueAsString(mapOf("synthetic" to true, "result" to result)))
        }
    }

    @Test fun `full synthetic saved scenario produces integrated narrative`() {
        val file = File("build/insight-full-demo.json")
        org.junit.jupiter.api.Assumptions.assumeTrue(file.exists())
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val saved = mapper.readTree(file)
        check(saved.path("synthetic").asBoolean())
        val original: WorkoutInsightSnapshot = mapper.readValue(saved["result"]["snapshot"].toString())
        val snapshot = original.copy(report = original.report.copy(exercises = original.report.exercises.map { e ->
            e.copy(completedSets = List(e.completedSetCount) { WorkoutReportSet(e.maxWeightKg, 10, null, 7) })
        }))
        val app = SpringApplication(LiveConfig::class.java).apply { webApplicationType = WebApplicationType.NONE }
        app.run("--spring.main.banner-mode=off").use { beans ->
            val rules = WorkoutInsightRules()
            val ai = OpenAiWorkoutInsight(RestClient.builder(), beans.getBean(WorkoutRecommendationAiProperties::class.java), true)
            val selected = ai.select(snapshot, rules.catalog(snapshot), CreateWorkoutInsightRequest(UUID.randomUUID(), externalAiConsent = true, consentVersion = WorkoutInsightConsent.VERSION))
            assertNotNull(selected, "Full data synthesis must pass validation and review")
            val result = rules.result(snapshot, rules.catalog(snapshot), requireNotNull(selected), "AI")
            File("build/insight-narrative-live.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf("synthetic" to true, "result" to result)))
        }
    }
}
