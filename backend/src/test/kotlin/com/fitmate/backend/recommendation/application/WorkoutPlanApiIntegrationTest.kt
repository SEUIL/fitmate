package com.fitmate.backend.recommendation.application

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.recommendation.api.*
import com.fitmate.backend.recommendation.infrastructure.*
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.profiles.active=test", "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}", "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class WorkoutPlanApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate
    @MockitoBean private lateinit var proposer: WorkoutPlanProposer
    private val rules = WorkoutPlanRules()
    private val root = "/api/v1/workout-recommendations/plans"
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }
    @BeforeEach fun setup() { reset(proposer); `when`(proposer.available()).thenReturn(true) }

    @Test fun `rule generation replacement save reload and workout start round trip with ownership`() {
        val owner = signup(); val stranger = signup()
        val record = post("/api/v1/inbody-records", owner, mapOf("measuredDate" to "2025-01-01", "weightKg" to 70, "skeletalMuscleMassKg" to 30, "bodyFatPercentage" to 25), HttpStatus.CREATED)
        val request = request() + ("inbodyRecordId" to record["id"].asText())
        val id = post(root, owner, request)["id"].asText()
        var plan = await(owner, id)
        assertEquals("READY", plan["status"].asText())
        assertEquals("RULE_BASED", plan["result"]["source"].asText())
        assertEquals("2025-01-01", plan["result"]["inbodyMeasuredDate"].asText())
        verify(proposer, never()).propose(anyContext())
        assertStatus(HttpStatus.NOT_FOUND, getResponse("$root/$id", stranger))
        assertStatus(HttpStatus.NOT_FOUND, command(root, stranger, request))
        assertStatus(HttpStatus.NOT_FOUND, command(root, stranger, request() + ("inbodyRecordId" to record["id"].asText())))
        assertStatus(HttpStatus.NOT_FOUND, command("$root/$id/save", stranger, mapOf("version" to plan["version"].asLong())))
        val day = plan["result"]["days"][0]
        val candidate = day["exercises"].firstNotNullOfOrNull { exercise ->
            val choices = get("$root/$id/alternatives?day=1&exerciseId=${exercise["exerciseId"].asText()}", owner)["items"]
            if (choices.isEmpty) null else exercise to choices[0]
        }
        assertNotNull(candidate)
        val replacement = mapOf("version" to plan["version"].asLong(), "day" to 1, "exerciseId" to candidate!!.first["exerciseId"].asText(), "replacementId" to candidate.second["exercise"]["id"].asText())
        plan = post("$root/$id/replace", owner, replacement)
        assertStatus(HttpStatus.CONFLICT, command("$root/$id/replace", owner, replacement))
        assertTrue(plan["result"]["days"][0]["exercises"].any { it["exerciseId"].asText() == replacement["replacementId"] })
        val saved = post("$root/$id/save", owner, mapOf("version" to plan["version"].asLong()))
        val repeated = post("$root/$id/save", owner, mapOf("version" to plan["version"].asLong()))
        assertEquals(saved["routineId"], repeated["routineId"])
        val routineId = saved["routineId"].asText()
        val routine = get("/api/v1/routines/$routineId", owner)
        assertEquals(plan["result"], routine["recommendation"])
        assertEquals(plan["result"]["days"].sumOf { it["exercises"].size() }, routine["items"].size())
        assertStatus(HttpStatus.NOT_FOUND, getResponse("/api/v1/routines/$routineId", stranger))
        val session = post("/api/v1/workout-sessions", owner, mapOf("routineId" to routineId), HttpStatus.CREATED)
        assertEquals(day["exercises"].size(), session["exercises"].size())
        val started = post("/api/v1/workout-sessions/${session["id"].asText()}/start", owner, mapOf("version" to session["version"].asLong()))
        assertEquals("IN_PROGRESS", started["status"].asText())
    }

    @Test fun `valid AI outside database transaction accepted and invalid AI falls back`() {
        val owner = signup()
        doAnswer { invocation ->
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            rules.fallback(invocation.arguments[0] as WorkoutPlanContext)
        }.`when`(proposer).propose(anyContext())
        val ready = await(owner, post(root, owner, consentRequest())["id"].asText())
        assertEquals("AI", ready["result"]["source"].asText())
        doAnswer { ProposedWorkoutPlan(listOf(PlanDay(1, listOf(PlanExercise(UUID.randomUUID(), 999, 10, 60))))) }.`when`(proposer).propose(anyContext())
        val fallback = await(owner, post(root, owner, consentRequest())["id"].asText())
        assertEquals("RULE_BASED", fallback["result"]["source"].asText())
    }

    @Test fun `missing AI response falls back and infeasible fallback fails with editable conditions`() {
        val owner = signup()
        val ready = await(owner, post(root, owner, consentRequest())["id"].asText())
        assertEquals("RULE_BASED", ready["result"]["source"].asText())
        val all = get("$root/options", owner)["exercises"].map { it["id"].asText() }
        val failed = await(owner, post(root, owner, request() + ("excludedExerciseIds" to all))["id"].asText())
        assertEquals("FAILED", failed["status"].asText())
        assertTrue(failed["message"].asText().contains("조건"))
    }

    @Test fun `concurrent duplicate requests call AI once and cancel fences late completion`() {
        val owner = signup(); val calls = AtomicInteger()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        doAnswer { invocation ->
            calls.incrementAndGet(); entered.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            rules.fallback(invocation.arguments[0] as WorkoutPlanContext)
        }.`when`(proposer).propose(anyContext())
        val request = consentRequest()
        val first = CompletableFuture.supplyAsync { post(root, owner, request) }
        val second = CompletableFuture.supplyAsync { post(root, owner, request) }
        try {
            val id = first.get(10, TimeUnit.SECONDS)["id"].asText()
            assertEquals(id, second.get(10, TimeUnit.SECONDS)["id"].asText())
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            post("$root/$id/cancel", owner, emptyMap<String, String>())
            release.countDown()
            Thread.sleep(150)
            assertEquals("CANCELED", get("$root/$id", owner)["status"].asText())
            assertEquals(1, calls.get())
        } finally { release.countDown() }
    }

    @Test fun `expired generation can retry same request after server interruption`() {
        val owner = signup(); val request = request()
        val id = post(root, owner, request)["id"].asText()
        await(owner, id)
        jdbc.update("UPDATE fitness_workout_plans SET status='GENERATING', result_json=NULL, updated_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 3 MINUTE) WHERE id=?", id)
        assertEquals("FAILED", get("$root/$id", owner)["status"].asText())
        post(root, owner, request)
        assertEquals("READY", await(owner, id)["status"].asText())
    }

    @Test fun `late generation cannot publish after lease without client polling`() {
        val owner = signup(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        doAnswer { invocation ->
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS))
            rules.fallback(invocation.arguments[0] as WorkoutPlanContext)
        }.`when`(proposer).propose(anyContext())
        try {
            val id = post(root, owner, consentRequest())["id"].asText()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            jdbc.update("UPDATE fitness_workout_plans SET updated_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 3 MINUTE) WHERE id=?", id)
            release.countDown()
            // Read raw storage: the status endpoint must not be what expires the request.
            var status = "GENERATING"
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (status == "GENERATING" && System.nanoTime() < deadline) {
                Thread.sleep(30)
                status = jdbc.queryForObject("SELECT status FROM fitness_workout_plans WHERE id=?", String::class.java, id)!!
            }
            assertEquals("FAILED", status)
            assertNull(jdbc.queryForObject("SELECT result_json FROM fitness_workout_plans WHERE id=?", String::class.java, id))
        } finally { release.countDown() }
    }

    @Test fun `unexpected provider failure falls back and wrong muscle replacement is rejected`() {
        val owner = signup()
        doAnswer { throw IllegalStateException("synthetic provider failure") }.`when`(proposer).propose(anyContext())
        val id = post(root, owner, consentRequest())["id"].asText()
        val ready = await(owner, id)
        assertEquals("RULE_BASED", ready["result"]["source"].asText())
        val items = ready["result"]["days"][0]["exercises"]
        val original = items[0]
        val other = items.first { it["bodyPart"] != original["bodyPart"] }
        assertStatus(HttpStatus.BAD_REQUEST, command("$root/$id/replace", owner, mapOf("version" to ready["version"].asLong(), "day" to 1,
            "exerciseId" to original["exerciseId"].asText(), "replacementId" to other["exerciseId"].asText())))
        assertEquals(ready["result"], get("$root/$id", owner)["result"])
    }

    @Test fun `request identity conflicts and invalid consent version are rejected`() {
        val owner = signup(); val request = request()
        val id = post(root, owner, request)["id"].asText()
        await(owner, id)
        assertStatus(HttpStatus.CONFLICT, command(root, owner, request + ("sessionMinutes" to 60)))
        assertStatus(HttpStatus.BAD_REQUEST, command(root, owner, consentRequest() + ("consentVersion" to "stale")))
        assertStatus(HttpStatus.UNAUTHORIZED, http.getForEntity("$root/options", JsonNode::class.java))
    }

    private fun anyContext(): WorkoutPlanContext = any(WorkoutPlanContext::class.java) ?: WorkoutPlanContext(CreateWorkoutPlanRequest(UUID.randomUUID(), 1, 20), com.fitmate.backend.member.domain.GoalCode.FITNESS, com.fitmate.backend.member.domain.SkillLevel.BEGINNER, emptyList())
    private fun request() = mapOf<String, Any>("requestId" to UUID.randomUUID().toString(), "daysPerWeek" to 2, "sessionMinutes" to 45)
    private fun consentRequest() = request() + mapOf("externalAiConsent" to true, "consentVersion" to OpenAiWorkoutPlanProposer.CONSENT_VERSION)
    private fun signup(): String {
        val body = mapOf("email" to "plan-${UUID.randomUUID()}@example.com", "password" to ("T!9a" + UUID.randomUUID()),
            "nickname" to "plan-${UUID.randomUUID().toString().take(16)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"))
        val response = http.postForEntity("/api/v1/auth/signup", body, JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["data"]["tokens"]["accessToken"].asText()
    }
    private fun await(token: String, id: String): JsonNode {
        repeat(150) {
            val value = get("$root/$id", token)
            if (value["status"].asText() != "GENERATING") return value
            Thread.sleep(50)
        }
        error("Generation did not finish")
    }
    private fun headers(token: String) = HttpHeaders().also { it.setBearerAuth(token) }
    private fun getResponse(path: String, token: String) = http.exchange(path, HttpMethod.GET, HttpEntity<Void>(headers(token)), JsonNode::class.java)
    private fun command(path: String, token: String, body: Any) = http.exchange(path, HttpMethod.POST, HttpEntity(body, headers(token)), JsonNode::class.java)
    private fun get(path: String, token: String): JsonNode { val r = getResponse(path, token); assertStatus(HttpStatus.OK, r); return r.body!!["data"] }
    private fun post(path: String, token: String, body: Any, expected: HttpStatus = HttpStatus.OK): JsonNode {
        val response = command(path, token, body); assertStatus(expected, response); return response.body!!["data"]
    }
    private fun assertStatus(expected: HttpStatus, response: ResponseEntity<JsonNode>) { assertEquals(expected, response.statusCode, response.body?.path("error")?.path("code")?.asText()) }
}
