package com.fitmate.backend.recommendation.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fitmate.backend.fitness.domain.*
import com.fitmate.backend.member.domain.*
import com.fitmate.backend.recommendation.api.*
import com.fitmate.backend.recommendation.application.*
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OpenAiWorkoutPlanProposerTest {
    private val mapper = jacksonObjectMapper()
    private val rules = WorkoutPlanRules()
    private fun context(consent: Boolean = true) = WorkoutPlanContext(
        CreateWorkoutPlanRequest(UUID.randomUUID(), 1, 45, externalAiConsent = consent, consentVersion = OpenAiWorkoutPlanProposer.CONSENT_VERSION,
            inbodyRecordId = UUID.randomUUID()), GoalCode.FITNESS, SkillLevel.BEGINNER,
        listOf(BodyPart.CHEST, BodyPart.BACK, BodyPart.LEGS).mapIndexed { i, part -> PlanCatalogExercise(UUID(0, i + 1L), "합성 운동", part, EquipmentType.MACHINE) },
        LocalDate.of(2025, 1, 1), java.math.BigDecimal("25"), java.math.BigDecimal("30"))

    @Test fun `composition response parsed and only allowlisted context sent`() {
        val input = AtomicReference<String>()
        val ctx = context()
        withServer(mapper.writeValueAsString(rules.fallback(ctx)), captured = input) { proposer ->
            assertNotNull(proposer.propose(ctx))
            val sent = mapper.readTree(input.get())
            assertFalse(sent["store"].asBoolean())
            val fields = mapper.readTree(sent["input"].asText()).fieldNames().asSequence().toSet()
            assertEquals(setOf("goal", "skillLevel", "daysPerWeek", "sessionMinutes", "catalog", "preferredExerciseIds", "warmupSeconds", "cardioSeconds", "maxSetsPerExercise", "maxSetsPerDay", "suggestedDayGroups", "bodyFatPercentage", "skeletalMuscleMassKg"), fields)
            assertFalse(input.get().contains(ctx.conditions.inbodyRecordId.toString()))
            assertFalse(input.get().contains(ctx.conditions.requestId.toString()))
        }
    }
    @Test fun `missing consent or stale version prevents any provider request`() {
        val count = AtomicInteger()
        withServer("{}", count = count) { proposer ->
            assertNull(proposer.propose(context(false)))
            val ctx = context()
            assertNull(proposer.propose(ctx.copy(conditions = ctx.conditions.copy(consentVersion = "obsolete"))))
        }
        assertEquals(0, count.get())
    }
    @Test fun `recent running and consented Samsung context use only minimal derived fields`() {
        val base = context()
        val running = RecentRunningRecommendationContext(
            goalType = "DISTANCE_TARGET", goalAchieved = true, distanceMeters = 5000,
            activeDurationSeconds = 1800, averagePaceSecondsPerKm = 360, rpe = 6, conditionScore = 4,
            samsungSummary = SamsungRecommendationContext(
                activeDurationSeconds = 1780, meanHeartRate = java.math.BigDecimal("142"), peakHeartRate = java.math.BigDecimal("171"),
                heartRateCoveragePercent = 91, heartRateLongGapCount = 1,
                paceSources = listOf("SAMSUNG_HEALTH", "SMARTPHONE_GPS"), analysisVersion = "external-timeseries-v1",
                limitations = listOf("PACE_PARTIAL_SAMPLES"),
            ),
        )
        val proposer = OpenAiWorkoutPlanProposer(RestClient.builder(), WorkoutRecommendationAiProperties(), rules)
        val withoutConsent = proposer.providerInput(base.copy(
            conditions = base.conditions.copy(externalAiConsent = false), recentRunning = listOf(running),
        ))
        assertFalse(withoutConsent.containsKey("recentRunningSummary"))

        val sent = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
            proposer.providerInput(base.copy(recentRunning = listOf(running))),
        )["recentRunningSummary"][0]
        assertEquals(
            setOf("goalType", "goalAchieved", "distanceMeters", "activeDurationSeconds", "averagePaceSecondsPerKm", "rpe", "conditionScore", "samsungSummary"),
            sent.fieldNames().asSequence().toSet(),
        )
        assertEquals(
            setOf("activeDurationSeconds", "meanHeartRate", "peakHeartRate", "heartRateCoveragePercent", "heartRateLongGapCount", "paceSources", "analysisVersion", "limitations"),
            sent["samsungSummary"].fieldNames().asSequence().toSet(),
        )
        val serialized = sent.toString()
        listOf("samples", "routePoints", "sourceRecordId", "measuredAt", "startedAt", "completedAt", "userId", "memo", "uuid").forEach {
            assertFalse(serialized.contains("\"$it\"", ignoreCase = true), "forbidden field $it")
        }
    }
    @Test fun `malformed extra fields null integer and truncated output rejected`() {
        val valid = mapper.writeValueAsString(rules.fallback(context()))
        for (body in listOf("not-json", "{\"days\":null}", valid.replace("\"sets\":2", "\"sets\":null"), valid.replace("\"sets\":2", "\"sets\":2.5"), valid.dropLast(1) + ",\"weight\":100}")) {
            withServer(body) { assertNull(it.propose(context())) }
        }
        withServer(valid, status = "incomplete") { assertNull(it.propose(context())) }
    }
    @Test fun `provider errors timeout and missing key fall back without throwing`() {
        withServer("{}", code = 429) { assertNull(it.propose(context())) }
        withServer("{}", code = 500) { assertNull(it.propose(context())) }
        withServer("{}", pause = 1500) { assertNull(it.propose(context())) }
        val proposer = OpenAiWorkoutPlanProposer(RestClient.builder(), WorkoutRecommendationAiProperties(planGenerationEnabled = true), rules)
        assertFalse(proposer.available()); assertNull(proposer.propose(context()))
    }
    private fun withServer(output: String, code: Int = 200, status: String = "completed", pause: Long = 0,
        captured: AtomicReference<String> = AtomicReference(), count: AtomicInteger = AtomicInteger(), test: (OpenAiWorkoutPlanProposer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            count.incrementAndGet(); captured.set(exchange.requestBody.bufferedReader().use { it.readText() })
            if (pause > 0) Thread.sleep(pause)
            val bytes = mapper.writeValueAsBytes(mapOf("status" to status, "output" to listOf(mapOf("content" to listOf(mapOf("type" to "output_text", "text" to output))))))
            try {
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(code, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            } catch (_: java.io.IOException) { exchange.close() }
        }
        server.start()
        try {
            test(OpenAiWorkoutPlanProposer(RestClient.builder(), WorkoutRecommendationAiProperties(apiKey = "synthetic-provider-key", baseUrl = "http://127.0.0.1:${server.address.port}", timeoutSeconds = 1, planGenerationEnabled = true), rules))
        } finally { server.stop(0) }
    }
}
