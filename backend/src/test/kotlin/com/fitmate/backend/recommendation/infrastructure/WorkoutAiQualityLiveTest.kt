package com.fitmate.backend.recommendation.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fitmate.backend.fitness.domain.*
import com.fitmate.backend.member.domain.*
import com.fitmate.backend.recommendation.api.*
import com.fitmate.backend.recommendation.application.*
import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightRules
import com.fitmate.backend.report.infrastructure.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.io.File
import java.util.UUID

/** Controlled synthetic comparisons only; opt-in actual provider calls, no database. */
@EnabledIfEnvironmentVariable(named = "FITMATE_LIVE_AI_QUALITY_TEST", matches = "true")
class WorkoutAiQualityLiveTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WorkoutRecommendationAiProperties::class)
    class Config
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private fun <T> configured(block: (WorkoutRecommendationAiProperties) -> T): T {
        val app = SpringApplication(Config::class.java).apply { webApplicationType = WebApplicationType.NONE }
        return app.run("--spring.main.banner-mode=off", "--fitmate.workout-recommendation-ai.plan-generation-enabled=true").use {
            block(it.getBean(WorkoutRecommendationAiProperties::class.java))
        }
    }

    @Test fun `recommendations respond to split time equipment exclusions and skill constraints`() = configured { props ->
        val catalog = listOf(EquipmentType.MACHINE, EquipmentType.BODYWEIGHT).flatMapIndexed { equipmentIndex, equipment ->
            listOf(BodyPart.CHEST, BodyPart.SHOULDERS, BodyPart.BACK, BodyPart.BICEPS, BodyPart.QUADRICEPS, BodyPart.HAMSTRINGS, BodyPart.CORE).mapIndexed { i, part ->
                PlanCatalogExercise(UUID(0, (equipmentIndex * 10 + i + 1).toLong()), "Synthetic $part $equipment", part, equipment)
            }
        }
        val base = WorkoutPlanContext(CreateWorkoutPlanRequest(UUID.randomUUID(), 4, 45, equipmentTypes = setOf(EquipmentType.MACHINE),
            externalAiConsent = true, consentVersion = OpenAiWorkoutPlanProposer.CONSENT_VERSION), GoalCode.MUSCLE_GAIN, SkillLevel.ONE_TO_THREE_YEARS, catalog)
        val cases = linkedMapOf("upper-lower" to base,
            "short-time" to base.copy(conditions = base.conditions.copy(sessionMinutes = 20)),
            "two-days" to base.copy(conditions = base.conditions.copy(daysPerWeek = 2)),
            "bodyweight-exclusion" to base.copy(conditions = base.conditions.copy(equipmentTypes = setOf(EquipmentType.BODYWEIGHT), excludedExerciseIds = setOf(catalog.first { it.equipmentType == EquipmentType.BODYWEIGHT && it.bodyPart == BodyPart.CHEST }.id))),
            "beginner" to base.copy(skillLevel = SkillLevel.BEGINNER))
        val rules = WorkoutPlanRules(); val ai = OpenAiWorkoutPlanProposer(RestClient.builder(), props, rules)
        val output = linkedMapOf<String, Any>()
        for ((name, context) in cases) {
            val proposal = ai.propose(context)
            if (proposal == null) { output[name] = mapOf("accepted" to false); continue }
            try {
                val result = rules.result(context, proposal, "AI")
                output[name] = mapOf("accepted" to true, "result" to result)
                if (name == "upper-lower") assertTrue(result.days.any { day -> day.exercises.map { rules.group(it.bodyPart) }.toSet().size < 3 }, "Experienced split must not force full-body each day")
                if (name == "bodyweight-exclusion") assertTrue(result.days.flatMap { it.exercises }.all { it.equipmentType == EquipmentType.BODYWEIGHT && it.exerciseId !in context.conditions.excludedExerciseIds })
                if (name == "beginner") assertTrue(result.days.all { day -> day.exercises.sumOf { it.sets } <= 12 })
            } catch (failure: com.fitmate.backend.common.error.ApiException) { output[name] = mapOf("accepted" to false, "reason" to "SERVER_CONSTRAINT") }
        }
        File("build/ai-plan-quality.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf("synthetic" to true, "cases" to output)))
        assertTrue(output.values.all { (it as Map<*, *>)["accepted"] == true }, "Some actual AI cases failed; inspect synthetic quality artifact")
    }

    @Test fun `report goal and effort changes produce grounded contextual explanations`() = configured { props ->
        val stored = mapper.readTree(File("build/insight-full-demo.json"))
        check(stored.path("synthetic").asBoolean())
        val base: WorkoutInsightSnapshot = mapper.readValue(stored["result"]["snapshot"].toString())
        val cases = linkedMapOf("muscle-gain" to base,
            "diet-only-change" to base.copy(goal = "DIET"),
            "effort-only-change" to base.copy(report = base.report.copy(exercises = base.report.exercises.mapIndexed { index, e ->
                e.copy(completedSets = e.completedSets.map { it.copy(rpe = if (index == 1) 9 else 5) })
            })))
        val rules = WorkoutInsightRules()
        val output = linkedMapOf<String, Any>()
        for ((name, snapshot) in cases) {
            val drafts = mutableListOf<String>()
            val builder = RestClient.builder().requestInterceptor { request, body, execution ->
                val response = execution.execute(request, body)
                val bytes = response.body.readBytes()
                val json = mapper.readTree(bytes)
                drafts += json.path("output").flatMap { it.path("content").toList() }.filter { it.path("type").asText() == "output_text" }.joinToString("") { it.path("text").asText() }
                object : org.springframework.http.client.ClientHttpResponse {
                    override fun getStatusCode() = response.statusCode
                    override fun getStatusText() = response.statusText
                    override fun getHeaders() = response.headers
                    override fun getBody() = java.io.ByteArrayInputStream(bytes)
                    override fun close() = response.close()
                }
            }
            val ai = OpenAiWorkoutInsight(builder, props, true)
            val selected = ai.select(snapshot, rules.catalog(snapshot), CreateWorkoutInsightRequest(UUID.randomUUID(), externalAiConsent = true, consentVersion = WorkoutInsightConsent.VERSION))
            // Debug text is captured only by this opt-in synthetic test, never in production logging.
            output[name] = if (selected == null) mapOf("accepted" to false, "syntheticDrafts" to drafts) else mapOf("accepted" to true, "result" to rules.result(snapshot, rules.catalog(snapshot), selected, "AI"))
        }
        File("build/ai-report-quality.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf("synthetic" to true, "cases" to output)))
        assertTrue(output.values.all { (it as Map<*, *>)["accepted"] == true }, "Some actual AI cases failed generation or grounding review")
        val results = output.values.map { (it as Map<*, *>)["result"] as WorkoutInsightResult }
        assertTrue(results.all { it.methodVersion == "grounded-narrative-v2" })
        // Human review of the persisted explanations checks relevance, not merely textual inequality.
    }
}
