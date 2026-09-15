package com.fitmate.backend.report

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.application.WorkoutInsightRules
import com.fitmate.backend.report.infrastructure.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.profiles.active=test", "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}", "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class WorkoutInsightApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate
    @MockitoBean private lateinit var ai: WorkoutInsightAi
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }
    @BeforeEach fun setup() { reset(ai); `when`(ai.available()).thenReturn(true) }

    @Test fun `no consent never calls AI and persisted snapshot survives profile updates`() {
        val owner = signup(); val session = session(owner); val root = root(session); val request = request(false)
        val started = post(root, owner, request); val id = started["id"].asText()
        val result = await(root, id, owner)
        assertEquals("RULE_BASED", result["result"]["source"].asText())
        assertEquals(result, get("$root/$id", owner))
        assertEquals(id, post(root, owner, request)["id"].asText())
        assertEquals(id, get(root, owner)["id"].asText())
        val snapshot = result["result"]["snapshot"]
        assertEquals(1, snapshot["report"]["completedSetCount"].asInt())
        jdbc.update("UPDATE core_user_goals SET goal_code='HABIT' WHERE user_id=(SELECT user_id FROM fitness_workout_sessions WHERE id=UUID_TO_BIN(?,1))", session)
        assertEquals(snapshot, get("$root/$id", owner)["result"]["snapshot"])
        verify(ai, never()).select(anySnapshot(), anyCatalog(), anyRequest())
    }

    @Test fun `AI executes outside transaction and invalid selection falls back`() {
        doAnswer { invocation ->
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            InsightSelection(narrative = narrativeFixture().let { n -> n.copy(summary = n.summary.copy(evidenceIds = n.summary.evidenceIds + "history")) })
        }.`when`(ai).select(anySnapshot(), anyCatalog(), anyRequest())
        val owner = signup(); val root = root(session(owner))
        val first = post(root, owner, request()); val generated = await(root, first["id"].asText(), owner)
        assertEquals("AI", generated["result"]["source"].asText())
        assertEquals("longitudinal-narrative-v3", generated["result"]["methodVersion"].asText())
        assertEquals(generated, get("$root/${first["id"].asText()}", owner))
        assertEquals(7, generated["result"]["snapshot"]["report"]["exercises"][0]["completedSets"][0]["rpe"].asInt())
        doAnswer { InsightSelection("invented", listOf("invented"), listOf("invented")) }.`when`(ai).select(anySnapshot(), anyCatalog(), anyRequest())
        val second = post(root, owner, request()); assertEquals("RULE_BASED", await(root, second["id"].asText(), owner)["result"]["source"].asText())
        doAnswer { throw IllegalStateException("synthetic failure") }.`when`(ai).select(anySnapshot(), anyCatalog(), anyRequest())
        val third = post(root, owner, request()); assertEquals("RULE_BASED", await(root, third["id"].asText(), owner)["result"]["source"].asText())
    }

    @Test fun `other users session report and inbody are inaccessible and consent is separate`() {
        val owner = signup(); val stranger = signup(); val root = root(session(owner)); val req = request(false)
        val id = post(root, owner, req)["id"].asText(); await(root, id, owner)
        assertEquals(HttpStatus.NOT_FOUND, call("$root/$id", stranger).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, call("$root/options", stranger).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, call(root, stranger, HttpMethod.POST, request()).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, call("$root/$id/cancel", stranger, HttpMethod.POST, emptyMap<String, Any>()).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, call(root, owner, HttpMethod.POST, request() + ("consentVersion" to "workout-plan-demo-v1")).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, call(root, owner, HttpMethod.POST, request() + ("inbodyRecordId" to UUID.randomUUID().toString())).statusCode)
        assertEquals(HttpStatus.CONFLICT, call(root, owner, HttpMethod.POST, req + ("includeSamsungHealth" to true)).statusCode)
        val strangerRoot = root(session(stranger))
        assertEquals(HttpStatus.NOT_FOUND, call(strangerRoot, stranger, HttpMethod.POST, req).statusCode)
    }

    @Test fun `duplicate generation blocked cancellation fences late response and new request retries`() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val exited = CountDownLatch(1)
        doAnswer { invocation ->
            entered.countDown(); release.await(8, TimeUnit.SECONDS); exited.countDown()
            WorkoutInsightRules().fallback(invocation.arguments[1] as InsightCatalog)
        }.`when`(ai).select(anySnapshot(), anyCatalog(), anyRequest())
        val owner = signup(); val root = root(session(owner)); val req = request()
        try {
            val id = post(root, owner, req)["id"].asText(); assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(id, post(root, owner, req)["id"].asText())
            assertEquals(HttpStatus.CONFLICT, call(root, owner, HttpMethod.POST, request()).statusCode)
            assertEquals("CANCELED", post("$root/$id/cancel", owner, emptyMap<String, Any>())["status"].asText())
            release.countDown(); assertTrue(exited.await(5, TimeUnit.SECONDS))
            val retry = post(root, owner, request(false)); await(root, retry["id"].asText(), owner)
            assertEquals("CANCELED", get("$root/$id", owner)["status"].asText())
            assertTrue(get("$root/$id", owner)["result"].isNull)
        } finally { release.countDown() }
    }

    @Test fun `expired generation is terminal and late completion cannot overwrite`() {
        val owner = signup(); val root = root(session(owner)); val id = post(root, owner, request(false))["id"].asText(); await(root, id, owner)
        jdbc.update("UPDATE fitness_workout_insights SET status='GENERATING', result_json=NULL, updated_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 3 MINUTE) WHERE id=?", id)
        assertEquals("FAILED", get("$root/$id", owner)["status"].asText())
        val retry = post(root, owner, request(false)); assertEquals("READY", await(root, retry["id"].asText(), owner)["status"].asText())
    }

    @Test fun `owned inbody and saved Samsung summary included only when selected`() {
        val owner = signup(); val stranger = signup(); val session = session(owner); val root = root(session)
        assertFalse(get("$root/options", owner)["samsungHealthAvailable"].asBoolean())
        val body = post("/api/v1/inbody-records", owner, mapOf("measuredDate" to "2026-01-01", "weightKg" to 70, "skeletalMuscleMassKg" to 30, "bodyFatPercentage" to 25), HttpStatus.CREATED)
        val bodyId = body["id"].asText()
        val strangerRoot = root(session(stranger))
        assertEquals(HttpStatus.NOT_FOUND, call(strangerRoot, stranger, HttpMethod.POST, request(false) + ("inbodyRecordId" to bodyId)).statusCode)
        // Synthetic completed session only: widen timestamps for a valid linked device summary.
        jdbc.update("UPDATE fitness_workout_sessions SET started_at='2026-01-01 00:00:00', ended_at='2026-01-01 00:30:00' WHERE id=UUID_TO_BIN(?,1)", session)
        assertEquals(HttpStatus.OK, call("/api/v1/workout-sessions/$session/external-workout", owner, HttpMethod.PUT, mapOf(
            "provider" to "SAMSUNG_HEALTH_DATA_SDK", "sourceRecordId" to UUID.randomUUID().toString(), "exerciseType" to "BENCH_PRESS",
            "startedAt" to "2026-01-01T00:00:00Z", "endedAt" to "2026-01-01T00:30:00Z", "activeDurationSeconds" to 1500, "meanHeartRate" to 110,
        )).statusCode)
        assertTrue(get("$root/options", owner)["samsungHealthAvailable"].asBoolean())
        val selected = post(root, owner, request(false) + mapOf("inbodyRecordId" to bodyId, "includeSamsungHealth" to true))
        val snapshot = await(root, selected["id"].asText(), owner)["result"]["snapshot"]
        assertEquals("2026-01-01", snapshot["inbody"]["measuredDate"].asText())
        assertEquals(1500, snapshot["samsungHealth"]["activeDurationSeconds"].asInt())
        assertEquals(1800, snapshot["report"]["durationSeconds"].asInt())
        val omitted = post(root, owner, request(false))
        val without = await(root, omitted["id"].asText(), owner)["result"]["snapshot"]
        assertTrue(without["inbody"].isNull); assertTrue(without["samsungHealth"].isNull)
    }

    @Test fun `longitudinal history excludes future foreign and demo sessions and validates multiple owned bodies`() {
        val owner = signup(); val stranger = signup()
        val past = session(owner); val future = session(owner); val demo = session(owner); val current = session(owner)
        jdbc.update("UPDATE fitness_workout_sessions SET started_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 7 DAY), ended_at=DATE_ADD(DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 7 DAY), INTERVAL 30 MINUTE) WHERE id=UUID_TO_BIN(?,1)", past)
        jdbc.update("UPDATE fitness_workout_sessions SET started_at=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY), ended_at=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 2 DAY) WHERE id=UUID_TO_BIN(?,1)", future)
        jdbc.update("UPDATE fitness_workout_sessions SET started_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 3 DAY), ended_at=DATE_ADD(DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 3 DAY), INTERVAL 30 MINUTE) WHERE id=UUID_TO_BIN(?,1)", demo)
        jdbc.update("UPDATE fitness_workout_session_exercises SET display_name_snapshot=CONCAT('[DEMO] ',display_name_snapshot) WHERE workout_session_id=UUID_TO_BIN(?,1)", demo)
        session(stranger)
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        fun body(token: String, date: java.time.LocalDate) = post("/api/v1/inbody-records", token, mapOf("measuredDate" to date.toString(), "weightKg" to 70, "skeletalMuscleMassKg" to 30, "bodyFatPercentage" to 25), HttpStatus.CREATED)["id"].asText()
        val first = body(owner, today.minusDays(10)); val second = body(owner, today.minusDays(2)); val foreign = body(stranger, today)
        val path = root(current)
        val selection = request(false) + mapOf("inbodyRecordIds" to listOf(first, second), "historyWeeks" to 4)
        val id = post(path, owner, selection)["id"].asText()
        val snapshot = await(path, id, owner)["result"]["snapshot"]
        assertEquals(2, snapshot["longitudinal"]["bodies"].size())
        assertEquals(1, snapshot["longitudinal"]["sessions"].size())
        assertEquals(past, snapshot["longitudinal"]["sessions"][0]["report"]["workoutSessionId"].asText())
        assertEquals(1, get("$path/options?historyWeeks=4", owner)["historySessionCount"].asInt())
        assertEquals(HttpStatus.NOT_FOUND, call(path, owner, HttpMethod.POST, request(false) + mapOf("inbodyRecordIds" to listOf(foreign))).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, call(path, owner, HttpMethod.POST, request(false) + mapOf("historyWeeks" to 5)).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, call(path, owner, HttpMethod.POST, request(false) + mapOf("inbodyRecordIds" to listOf(first, first))).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, call(path, owner, HttpMethod.POST, request(false) + mapOf("inbodyRecordId" to first, "inbodyRecordIds" to listOf(second))).statusCode)
        val oldBody = body(owner, today.minusDays(40))
        assertEquals(HttpStatus.BAD_REQUEST, call(path, owner, HttpMethod.POST, request(false) + mapOf("inbodyRecordIds" to listOf(oldBody))).statusCode)
        val pastData = get("/api/v1/workout-sessions/$past", owner)
        assertEquals(HttpStatus.OK, call("/api/v1/workout-sessions/$past/external-workout", owner, HttpMethod.PUT, mapOf(
            "provider" to "SAMSUNG_HEALTH_DATA_SDK", "sourceRecordId" to UUID.randomUUID().toString(), "exerciseType" to "BENCH_PRESS",
            "startedAt" to pastData["startedAt"].asText(), "endedAt" to pastData["endedAt"].asText(), "activeDurationSeconds" to 1500)).statusCode)
        val oldReport = post(root(past), owner, request(false))["id"].asText(); await(root(past), oldReport, owner)
        jdbc.update("UPDATE fitness_workout_insights SET created_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 6 DAY) WHERE id=?", oldReport)
        val withContext = post(path, owner, request(false) + mapOf("includeSamsungHealth" to true, "includePreviousReports" to true))["id"].asText()
        val context = await(path, withContext, owner)["result"]["snapshot"]["longitudinal"]
        assertEquals(1500, context["sessions"][0]["samsungHealth"]["activeDurationSeconds"].asInt())
        assertEquals(1, context["previousAdvice"].size())
        assertEquals(past, context["previousAdvice"][0]["sessionId"].asText())
        assertTrue(get("$path/options", owner)["samsungHealthAvailable"].asBoolean())
    }

    private fun request(consent: Boolean = true) = mapOf<String, Any>("requestId" to UUID.randomUUID().toString(), "externalAiConsent" to consent, "consentVersion" to WorkoutInsightConsent.VERSION)
    private fun root(session: String) = "/api/v1/workout-sessions/$session/insights"
    private fun signup(): String {
        val response = http.postForEntity("/api/v1/auth/signup", mapOf("email" to "insight-${UUID.randomUUID()}@example.com", "password" to ("T!9a" + UUID.randomUUID()),
            "nickname" to "ins-${UUID.randomUUID().toString().take(16)}", "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["data"]["tokens"]["accessToken"].asText()
    }
    private fun session(owner: String): String {
        val created = post("/api/v1/workout-sessions", owner, mapOf("exercises" to listOf(mapOf("exerciseId" to "30000000-0000-0000-0000-000000000001", "sequenceNo" to 1, "targetSets" to 1))), HttpStatus.CREATED)
        val id = created["id"].asText(); val path = "/api/v1/workout-sessions/$id"
        post("$path/start", owner, mapOf("version" to created["version"].asLong()))
        val exercise = get(path, owner)["exercises"][0]
        assertEquals(HttpStatus.OK, call("$path/exercises/${exercise["id"].asText()}/sets/1", owner, HttpMethod.PUT,
            mapOf("version" to exercise["sets"][0]["version"].asLong(), "weightKg" to 10, "repetitions" to 10, "rpe" to 7, "restSeconds" to 60, "completed" to true)).statusCode)
        post("$path/complete", owner, mapOf("version" to get(path, owner)["version"].asLong()))
        return id
    }
    private fun await(root: String, id: String, owner: String): JsonNode {
        repeat(150) { val value = get("$root/$id", owner); if (value["status"].asText() != "GENERATING") return value; Thread.sleep(50) }
        error("Insight generation did not finish")
    }
    private fun call(path: String, token: String, method: HttpMethod = HttpMethod.GET, body: Any? = null) = http.exchange(path, method, HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token) }), JsonNode::class.java)
    private fun get(path: String, token: String): JsonNode { val r = call(path, token); assertEquals(HttpStatus.OK, r.statusCode); return r.body!!["data"] }
    private fun post(path: String, token: String, body: Any, expected: HttpStatus = HttpStatus.OK): JsonNode {
        val r = call(path, token, HttpMethod.POST, body); assertEquals(expected, r.statusCode, r.body?.path("error")?.path("code")?.asText()); return r.body!!["data"]
    }
    private fun anySnapshot(): WorkoutInsightSnapshot = any(WorkoutInsightSnapshot::class.java) ?: insightSnapshot()
    private fun anyCatalog(): InsightCatalog = any(InsightCatalog::class.java) ?: WorkoutInsightRules().catalog(insightSnapshot())
    private fun anyRequest(): CreateWorkoutInsightRequest = any(CreateWorkoutInsightRequest::class.java) ?: CreateWorkoutInsightRequest(UUID.randomUUID())
}
