package com.fitmate.backend.health.api

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class ExternalWorkoutApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate

    @Test
    fun `owner stores reads retries and updates one external summary`() {
        val owner = signup()
        val stranger = signup()
        val session = completedWorkout(owner)
        val sessionId = session["id"].asText()
        val path = "/api/v1/workout-sessions/$sessionId/external-workout"

        assertTrue(command(path, HttpMethod.GET, owner, null).body!!["data"].isNull)
        assertEquals(HttpStatus.NOT_FOUND, command(path, HttpMethod.GET, stranger, null).statusCode)

        val payload = payload(session, calories = 123.456)
        val first = command(path, HttpMethod.PUT, owner, payload)
        assertEquals(HttpStatus.OK, first.statusCode, first.body.toString())
        assertEquals(123.46, first.body!!["data"]["caloriesKcal"].asDouble())
        assertEquals(0, first.body!!["data"]["version"].asInt())

        val retry = command(path, HttpMethod.PUT, owner, payload)
        assertEquals(HttpStatus.OK, retry.statusCode)
        assertEquals(0, retry.body!!["data"]["version"].asInt())
        val normalizedRetry = command(path, HttpMethod.PUT, owner, payload(session, 123.46).filterKeys { it != "distanceMeters" })
        assertEquals(HttpStatus.OK, normalizedRetry.statusCode)
        assertEquals(0, normalizedRetry.body!!["data"]["version"].asInt())

        val missingVersion = command(path, HttpMethod.PUT, owner, payload(session, calories = 125.0))
        assertEquals(HttpStatus.CONFLICT, missingVersion.statusCode)
        val updated = command(path, HttpMethod.PUT, owner, payload(session, calories = 125.0) + ("expectedVersion" to 0))
        assertEquals(HttpStatus.OK, updated.statusCode, updated.body.toString())
        assertEquals(1, updated.body!!["data"]["version"].asInt())
        assertEquals(125.0, command(path, HttpMethod.GET, owner, null).body!!["data"]["caloriesKcal"].asDouble())
    }

    @Test
    fun `null source app duplicates are blocked while distinct case sensitive apps are separate`() {
        val owner = signup()
        val first = completedWorkout(owner)
        val second = completedWorkout(owner)
        val third = completedWorkout(owner)
        val fourth = completedWorkout(owner)
        fun save(session: JsonNode, appId: String?) = command(
            "/api/v1/workout-sessions/${session["id"].asText()}/external-workout", HttpMethod.PUT, owner,
            payload(session, 100.0, "shared-UID") + ("sourceAppId" to appId),
        )

        assertEquals(HttpStatus.OK, save(first, null).statusCode)
        assertEquals(HttpStatus.CONFLICT, save(second, null).statusCode)
        assertEquals(HttpStatus.OK, save(third, "com.example.App").statusCode)
        assertEquals(HttpStatus.OK, save(fourth, "com.example.app").statusCode)
    }

    @Test
    fun `concurrent attempts connect one source to only one session`() {
        val owner = signup()
        val sessions = listOf(completedWorkout(owner), completedWorkout(owner))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = sessions.map { session ->
                executor.submit<Int> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    command(
                        "/api/v1/workout-sessions/${session["id"].asText()}/external-workout", HttpMethod.PUT, owner,
                        payload(session, 100.0, "concurrent-UID") + ("sourceAppId" to "com.example.source"),
                    ).statusCode.value()
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf(200, 409), results.map { it.get(20, TimeUnit.SECONDS) }.sorted())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun completedWorkout(token: String): JsonNode {
        val created = command(
            "/api/v1/workout-sessions", HttpMethod.POST, token,
            mapOf("exercises" to listOf(mapOf("exerciseId" to "30000000-0000-0000-0000-000000000001", "sequenceNo" to 1, "targetSets" to 1))),
        ).body!!["data"]
        val id = created["id"].asText()
        val exerciseId = created["exercises"][0]["id"].asText()
        command("/api/v1/workout-sessions/$id/start", HttpMethod.POST, token, mapOf("version" to 0))
        command("/api/v1/workout-sessions/$id/exercises/$exerciseId/sets/1", HttpMethod.PUT, token, mapOf("weightKg" to 20, "repetitions" to 10, "completed" to true, "version" to 0))
        command("/api/v1/workout-sessions/$id/complete", HttpMethod.POST, token, mapOf("version" to 1))
        return command("/api/v1/workout-sessions/$id", HttpMethod.GET, token, null).body!!["data"]
    }

    private fun payload(session: JsonNode, calories: Double, sourceRecordId: String = "UID-A"): Map<String, Any?> {
        val startedAt = Instant.parse(session["startedAt"].asText())
        val endedAt = Instant.parse(session["endedAt"].asText())
        return mapOf(
            "provider" to "SAMSUNG_HEALTH_DATA_SDK", "sourceRecordId" to sourceRecordId,
            "sourceAppId" to null, "sourceModifiedAt" to endedAt.toString(), "exerciseType" to "BENCH_PRESS",
            "startedAt" to startedAt.toString(), "endedAt" to endedAt.toString(),
            "activeDurationSeconds" to java.time.Duration.between(startedAt, endedAt).seconds.coerceAtLeast(1),
            "distanceMeters" to null, "caloriesKcal" to calories, "meanHeartRate" to 110,
            "minHeartRate" to 90, "maxHeartRate" to 130,
        )
    }

    private fun signup(): String {
        val response = http.postForEntity("/api/v1/auth/signup", mapOf(
            "email" to "external-${UUID.randomUUID()}@example.com", "password" to "test-password-123",
            "nickname" to "external-${UUID.randomUUID().toString().take(20)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"),
        ), JsonNode::class.java)
        return response.body!!["data"]["tokens"]["accessToken"].asText()
    }

    private fun command(path: String, method: HttpMethod, token: String, body: Any?) = http.exchange(
        path, method, HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token) }), JsonNode::class.java,
    )

    companion object {
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }
}
