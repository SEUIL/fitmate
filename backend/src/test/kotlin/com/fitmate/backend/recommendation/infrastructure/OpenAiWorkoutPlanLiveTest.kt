package com.fitmate.backend.recommendation.infrastructure

import com.fitmate.backend.fitness.domain.*
import com.fitmate.backend.member.domain.*
import com.fitmate.backend.recommendation.api.*
import com.fitmate.backend.recommendation.application.*
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

/** Opt-in paid call: generated fixtures only, with no user records or identifiers. */
@EnabledIfEnvironmentVariable(named = "FITMATE_LIVE_WORKOUT_PLAN_TEST", matches = "true")
class OpenAiWorkoutPlanLiveTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WorkoutRecommendationAiProperties::class)
    class LiveConfig

    @Test
    fun `real provider composition satisfies server constraints with synthetic inbody`() {
        val application = SpringApplication(LiveConfig::class.java).apply { webApplicationType = WebApplicationType.NONE }
        application.run("--spring.main.banner-mode=off", "--fitmate.workout-recommendation-ai.plan-generation-enabled=true").use { beans ->
            val properties = beans.getBean(WorkoutRecommendationAiProperties::class.java)
            check(properties.apiKey.isNotBlank()) { "AI key configuration missing; no request sent." }
            val rules = WorkoutPlanRules()
            val input = WorkoutPlanContext(
                CreateWorkoutPlanRequest(UUID.randomUUID(), 2, 30, externalAiConsent = true,
                    consentVersion = OpenAiWorkoutPlanProposer.CONSENT_VERSION),
                GoalCode.FITNESS, SkillLevel.BEGINNER,
                listOf(BodyPart.CHEST, BodyPart.BACK, BodyPart.LEGS, BodyPart.CORE).mapIndexed { index, part ->
                    PlanCatalogExercise(UUID(0, index + 1L), "Synthetic exercise", part, EquipmentType.MACHINE)
                }, LocalDate.of(2025, 1, 1), BigDecimal("25"), BigDecimal("30"),
            )
            val builder = RestClient.builder().requestInterceptor { request, body, execution ->
                execution.execute(request, body).also { response ->
                    println("Live provider HTTP status: ${response.statusCode.value()}")
                }
            }
            val proposal = OpenAiWorkoutPlanProposer(builder, properties, rules).propose(input)
            assertNotNull(proposal, "Actual AI composition was not returned; fallback does not count as live AI success.")
            rules.validate(input, requireNotNull(proposal))
        }
    }
}
