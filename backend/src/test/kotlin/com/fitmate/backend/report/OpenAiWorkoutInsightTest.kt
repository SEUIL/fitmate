package com.fitmate.backend.report

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fitmate.backend.recommendation.infrastructure.WorkoutRecommendationAiProperties
import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightRules
import com.fitmate.backend.report.infrastructure.*
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OpenAiWorkoutInsightTest {
    private val mapper = jacksonObjectMapper()
    private val snapshot = insightSnapshot()
    private val catalog = WorkoutInsightRules().catalog(snapshot)
    private val request = CreateWorkoutInsightRequest(UUID.randomUUID(), externalAiConsent = true, consentVersion = WorkoutInsightConsent.VERSION)
    @Test fun `only minimized facts sent and structured selection accepted`() {
        server(mapper.writeValueAsString(narrativeFixture())) { ai, count, capture ->
            assertNotNull(ai.select(snapshot, catalog, request))
            assertEquals(2, count.get())
            val sent = mapper.readTree(capture())
            assertFalse(sent["store"].asBoolean())
            assertEquals("json_schema", sent["text"]["format"]["type"].asText())
            val input = sent["input"].asText()
            assertFalse(input.contains(snapshot.report.workoutSessionId.toString()))
            assertFalse(input.contains(snapshot.report.exercises.single().exerciseName))
            assertFalse(input.contains("startedAt")); assertFalse(input.contains("memo")); assertFalse(input.contains("inbody")); assertFalse(input.contains("samsungHealth"))
        }
    }
    @Test fun `review rejection and malformed verdict fail closed`() {
        for (review in listOf("{\"accepted\":false}", "{\"accepted\":\"true\"}", "{}")) {
            server(mapper.writeValueAsString(narrativeFixture()), review = review) { ai, _, _ ->
                assertNull(ai.select(snapshot, catalog, request))
            }
        }
        server(mapper.writeValueAsString(narrativeFixture()), reviewStatus = "incomplete") { ai, count, _ ->
            assertNull(ai.select(snapshot, catalog, request))
            assertEquals(2, count.get())
        }
    }
    @Test fun `consent missing stale or disabled prevents provider call`() {
        server("{}") { ai, count, _ ->
            assertNull(ai.select(snapshot, catalog, request.copy(externalAiConsent = false)))
            assertNull(ai.select(snapshot, catalog, request.copy(consentVersion = "workout-plan-demo-v1")))
            assertNull(ai.select(snapshot, catalog, request.copy(consentVersion = "workout-insight-demo-v1")))
            assertEquals(0, count.get())
        }
        val disabled = OpenAiWorkoutInsight(RestClient.builder(), WorkoutRecommendationAiProperties(apiKey = "synthetic"), false)
        assertFalse(disabled.available()); assertNull(disabled.select(snapshot, catalog, request))
    }
    @Test fun `bad structure additional prose refusal and incomplete rejected`() {
        listOf("not-json", "{}", "{\"summaryId\":null}", "{\"summaryId\":\"recorded\",\"observationIds\":[\"exercise-0\"],\"actionIds\":[\"record-effort\"],\"diagnosis\":\"invented\"}").forEach { output ->
            server(output) { ai, _, _ -> assertNull(ai.select(snapshot, catalog, request)) }
        }
        server("{}", status = "incomplete") { ai, _, _ -> assertNull(ai.select(snapshot, catalog, request)) }
        server("{}", type = "refusal") { ai, _, _ -> assertNull(ai.select(snapshot, catalog, request)) }
    }
    @Test fun `timeout and provider errors return fallback signal`() {
        server("{}", pause = 1500) { ai, _, _ -> assertNull(ai.select(snapshot, catalog, request)) }
        server("{}", code = 429) { ai, _, _ -> assertNull(ai.select(snapshot, catalog, request)) }
    }
    private fun server(output: String, status: String = "completed", type: String = "output_text", pause: Long = 0, code: Int = 200, review: String = "{\"accepted\":true,\"reason\":\"NONE\"}", reviewStatus: String = "completed",
        test: (OpenAiWorkoutInsight, AtomicInteger, () -> String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); val count = AtomicInteger(); var captured = ""
        server.createContext("/") { exchange ->
            val number = count.incrementAndGet(); val body = exchange.requestBody.bufferedReader().use { it.readText() }; if (number == 1) captured = body
            if (pause > 0) Thread.sleep(pause)
            val bytes = mapper.writeValueAsBytes(mapOf("status" to if (number == 1) status else reviewStatus, "output" to listOf(mapOf("content" to listOf(mapOf("type" to type, "text" to if (number == 1) output else review))))))
            try { exchange.responseHeaders.add("Content-Type", "application/json"); exchange.sendResponseHeaders(code, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) } }
            catch (_: java.io.IOException) { exchange.close() }
        }
        server.start()
        try { test(OpenAiWorkoutInsight(RestClient.builder(), WorkoutRecommendationAiProperties(apiKey = "synthetic", baseUrl = "http://127.0.0.1:${server.address.port}", timeoutSeconds = 1), true, 1), count) { captured } }
        finally { server.stop(0) }
    }
}
