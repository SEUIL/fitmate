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
        assertEquals(HttpStatus.NOT_FOUND, command(path, HttpMethod.PUT, stranger, payload(session, 123.456)).statusCode)

        val payload = payload(session, calories = 123.456)
        val first = command(path, HttpMethod.PUT, owner, payload)
        assertEquals(HttpStatus.OK, first.statusCode, first.body.toString())
        assertEquals(123.46, first.body!!["data"]["caloriesKcal"].asDouble())
        assertEquals(0, first.body!!["data"]["version"].asInt())
        assertEquals(0, first.body!!["data"]["timeSeriesSampleCount"].asInt())

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

    @Test
    fun `owner replaces time series idempotently and unlink removes it`() {
        val owner = signup()
        val stranger = signup()
        val session = completedWorkout(owner)
        val sessionId = session["id"].asText()
        val path = "/api/v1/workout-sessions/$sessionId/external-workout"
        val startedAt = Instant.parse(session["startedAt"].asText())
        val endedAt = Instant.parse(session["endedAt"].asText())
        val firstPayload = payload(session, 100.0, "series-UID") + ("samples" to listOf(
            mapOf("measuredAt" to startedAt.toString(), "heartRate" to 100, "speedMetersPerSecond" to null),
        ))
        val first = command(path, HttpMethod.PUT, owner, firstPayload)
        assertEquals(HttpStatus.OK, first.statusCode, first.body.toString())
        assertEquals(1, first.body!!["data"]["timeSeriesSampleCount"].asInt())
        val retry = command(path, HttpMethod.PUT, owner, firstPayload)
        assertEquals(HttpStatus.OK, retry.statusCode)
        assertEquals(0, retry.body!!["data"]["version"].asInt())
        assertEquals(HttpStatus.NOT_FOUND, command("$path/analysis", HttpMethod.GET, stranger, null).statusCode)
        val analysis = command("$path/analysis", HttpMethod.GET, owner, null)
        assertEquals(HttpStatus.OK, analysis.statusCode, analysis.body.toString())
        assertEquals("external-timeseries-v1", analysis.body!!["data"]["analysisVersion"].asText())
        assertEquals(1, analysis.body!!["data"]["heartRate"]["validSampleCount"].asInt())
        assertEquals(0, analysis.body!!["data"]["pace"]["samsungSampleCount"].asInt())

        val replacement = firstPayload + ("samples" to listOf(
            mapOf("measuredAt" to startedAt.toString(), "heartRate" to 120, "speedMetersPerSecond" to null),
        )) + ("sourceModifiedAt" to endedAt.plusSeconds(1).toString()) + ("expectedVersion" to 0)
        val updated = command(path, HttpMethod.PUT, owner, replacement)
        assertEquals(HttpStatus.OK, updated.statusCode, updated.body.toString())
        assertEquals(1, updated.body!!["data"]["timeSeriesSampleCount"].asInt())
        assertEquals(HttpStatus.NO_CONTENT, command(path, HttpMethod.DELETE, owner, null).statusCode)
        assertTrue(command(path, HttpMethod.GET, owner, null).body!!["data"].isNull)
    }

    @Test
    fun `running report requery combines private route Samsung summary and deterministic analysis`() {
        val owner = signup()
        val stranger = signup()
        val startedAt = Instant.parse("2026-01-01T00:00:00Z")
        val completedAt = startedAt.plusSeconds(180)
        val route = listOf(130L, 140L, 150L).mapIndexed { index, offset ->
            mapOf(
                "latitude" to (37.0 + index * 0.0001),
                "longitude" to (127.0 + index * 0.0001),
                "recordedAt" to startedAt.plusSeconds(offset).toString(),
            )
        }
        val run = command("/api/v1/running-records", HttpMethod.POST, owner, mapOf(
            "runType" to "DISTANCE_TARGET", "targetDistanceMeters" to 500,
            "activeDurationSeconds" to 180, "distanceMeters" to 500,
            "startedAt" to startedAt.toString(), "completedAt" to completedAt.toString(),
            "routeVisibility" to "PRIVATE", "routePoints" to route, "rpe" to 6, "conditionScore" to 4,
        )).body!!["data"]
        val sessionId = run["id"].asText()
        val externalPath = "/api/v1/workout-sessions/$sessionId/external-workout"
        val heartRates = listOf(100, 130, 170, 165, 140, 120)
        val samples = heartRates.mapIndexed { index, heartRate ->
            mapOf(
                "measuredAt" to startedAt.plusSeconds(index * 30L).toString(),
                "heartRate" to heartRate,
                "speedMetersPerSecond" to if (index < 2) 3.0 else null,
            )
        }
        val linked = command(externalPath, HttpMethod.PUT, owner, mapOf(
            "provider" to "SAMSUNG_HEALTH_DATA_SDK", "sourceRecordId" to "running-report-source",
            "exerciseType" to "RUNNING", "startedAt" to startedAt.toString(), "endedAt" to completedAt.toString(),
            "activeDurationSeconds" to 180, "meanHeartRate" to 138, "minHeartRate" to 100, "maxHeartRate" to 170,
            "samples" to samples,
        ))
        assertEquals(HttpStatus.OK, linked.statusCode)
        assertEquals(6, linked.body!!["data"]["timeSeriesSampleCount"].asInt())

        val first = command("/api/v1/running-records/$sessionId", HttpMethod.GET, owner, null)
        val second = command("/api/v1/running-records/$sessionId", HttpMethod.GET, owner, null)
        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(first.body!!["data"], second.body!!["data"])
        assertEquals(3, first.body!!["data"]["routePoints"].size())
        assertEquals(HttpStatus.NOT_FOUND, command("/api/v1/running-records/$sessionId", HttpMethod.GET, stranger, null).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, command("$externalPath/analysis", HttpMethod.GET, stranger, null).statusCode)

        val analysis = command("$externalPath/analysis", HttpMethod.GET, owner, null)
        assertEquals(HttpStatus.OK, analysis.statusCode)
        val report = analysis.body!!["data"]
        assertTrue(report["available"].asBoolean())
        assertEquals(170, report["heartRate"]["peakBpm"].asInt())
        assertTrue(report["heartRate"]["biggestRise"].isObject)
        assertTrue(report["heartRate"]["biggestFall"].isObject)
        assertTrue(report["heartRate"]["peakSustained"].isObject)
        assertTrue(report["pace"]["samsungSourceUsed"].asBoolean())
        assertTrue(report["pace"]["smartphoneGpsSourceUsed"].asBoolean())
        assertTrue(report["limitations"].map(JsonNode::asText).contains("PACE_PARTIAL_SAMPLES"))
        assertTrue(!report.toString().contains("latitude") && !report.toString().contains("measuredAt"))

        assertEquals(HttpStatus.NO_CONTENT, command(externalPath, HttpMethod.DELETE, owner, null).statusCode)
        val disconnected = command("$externalPath/analysis", HttpMethod.GET, owner, null).body!!["data"]
        assertEquals(false, disconnected["available"].asBoolean())
        assertTrue(disconnected["limitations"].map(JsonNode::asText).contains("TIME_SERIES_NOT_CONNECTED"))
        assertEquals(3, command("/api/v1/running-records/$sessionId", HttpMethod.GET, owner, null).body!!["data"]["routePoints"].size())
    }

    @Test
    fun `basic running report remains available without route or Samsung data`() {
        val owner = signup()
        val completedAt = Instant.parse("2026-01-02T00:01:00Z")
        val run = command("/api/v1/running-records", HttpMethod.POST, owner, mapOf(
            "runType" to "FREE", "activeDurationSeconds" to 60, "distanceMeters" to 0,
            "startedAt" to completedAt.minusSeconds(60).toString(), "completedAt" to completedAt.toString(),
            "rpe" to 5, "conditionScore" to 3,
        )).body!!["data"]
        val sessionId = run["id"].asText()

        val stored = command("/api/v1/running-records/$sessionId", HttpMethod.GET, owner, null).body!!["data"]
        assertEquals(0, stored["distanceMeters"].asInt())
        assertEquals(0, stored["routePoints"].size())
        val analysis = command("/api/v1/workout-sessions/$sessionId/external-workout/analysis", HttpMethod.GET, owner, null).body!!["data"]
        assertEquals(false, analysis["available"].asBoolean())
        assertTrue(analysis["limitations"].map(JsonNode::asText).contains("TIME_SERIES_NOT_CONNECTED"))
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
