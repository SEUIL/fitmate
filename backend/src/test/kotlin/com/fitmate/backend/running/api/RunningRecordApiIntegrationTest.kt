package com.fitmate.backend.running.api

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class RunningRecordApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate

    @Test
    fun `retry with same client record id creates one owned record`() {
        val owner = signup()
        val body = mapOf("clientRecordId" to UUID.randomUUID().toString(), "runType" to "FREE",
            "completedAt" to "2026-09-08T12:00:00Z", "activeDurationSeconds" to 60, "distanceMeters" to 0)
        val first = command("/api/v1/running-records", HttpMethod.POST, owner, body)
        val retry = command("/api/v1/running-records", HttpMethod.POST, owner, body)
        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals(HttpStatus.CREATED, retry.statusCode)
        assertEquals(first.body!!["data"]["id"], retry.body!!["data"]["id"])
        val list = command("/api/v1/running-records?cursor=0&size=20", HttpMethod.GET, owner, null)
        assertEquals(1, list.body!!["data"]["items"].size())
        assertEquals(HttpStatus.NOT_FOUND, command("/api/v1/running-records", HttpMethod.POST, signup(), body).statusCode)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test
    fun `completed run stores calculated pace laps route and appears in workout history`() {
        val owner = signup()
        val stranger = signup()
        val created = command(
            "/api/v1/running-records", HttpMethod.POST, owner,
            mapOf(
                "runType" to "DISTANCE_TARGET",
                "targetDistanceMeters" to 5_000,
                "routeVisibility" to "PRIVATE",
                "autoPauseEnabled" to true,
                "startedAt" to "2026-09-07T11:28:00Z",
                "completedAt" to "2026-09-07T12:00:00Z",
                "activeDurationSeconds" to 1_800,
                "distanceMeters" to 5_000,
                "rpe" to 6,
                "conditionScore" to 4,
                "memo" to "  한강 러닝  ",
                "routePoints" to listOf(
                    mapOf("latitude" to 0.0, "longitude" to 0.0, "recordedAt" to "2026-09-07T11:28:00Z"),
                    mapOf("latitude" to 0.001, "longitude" to 0.002, "recordedAt" to "2026-09-07T11:58:00Z"),
                ),
                "laps" to listOf(370, 365, 360, 355, 350).mapIndexed { index, seconds ->
                    mapOf("lapNo" to index + 1, "distanceMeters" to 1_000, "durationSeconds" to seconds)
                },
            ),
        )
        assertEquals(HttpStatus.CREATED, created.statusCode, created.body.toString())
        val data = created.body!!["data"]
        val recordId = data["id"].asText()
        assertEquals(360, data["averagePaceSecondsPerKm"].asInt())
        assertEquals(350, data["bestPaceSecondsPerKm"].asInt())
        assertEquals(10.0, data["averageSpeedKmh"].asDouble())
        assertTrue(data["goalAchieved"].asBoolean())
        assertEquals("한강 러닝", data["memo"].asText())
        assertEquals(5, data["laps"].size())
        assertEquals(2, data["routePoints"].size())
        assertEquals(1_920, data["elapsedDurationSeconds"].asInt())

        val detail = command("/api/v1/running-records/$recordId", HttpMethod.GET, owner, null)
        assertEquals(HttpStatus.OK, detail.statusCode, detail.body.toString())
        assertEquals(5_000, detail.body!!["data"]["distanceMeters"].asInt())
        assertEquals(HttpStatus.NOT_FOUND, command(
            "/api/v1/running-records/$recordId", HttpMethod.GET, stranger, null,
        ).statusCode)

        val list = command("/api/v1/running-records?cursor=0&size=20", HttpMethod.GET, owner, null)
        assertEquals(HttpStatus.OK, list.statusCode, list.body.toString())
        assertEquals(recordId, list.body!!["data"]["items"][0]["id"].asText())
        assertFalse(list.body!!["data"]["hasNext"].asBoolean())

        val workouts = command(
            "/api/v1/workout-sessions?status=COMPLETED&cursor=0&size=20", HttpMethod.GET, owner, null,
        )
        assertEquals(HttpStatus.OK, workouts.statusCode, workouts.body.toString())
        assertEquals("RUNNING", workouts.body!!["data"]["items"][0]["sportCode"].asText())
    }

    @Test
    fun `time and memo can be stored when frontend reports zero distance`() {
        val token = signup()
        val created = command(
            "/api/v1/running-records", HttpMethod.POST, token,
            mapOf(
                "runType" to "TIME_TARGET",
                "targetDurationSeconds" to 600,
                "completedAt" to "2026-09-07T12:00:00Z",
                "activeDurationSeconds" to 600,
                "distanceMeters" to 0,
                "memo" to "실내 러닝",
            ),
        )
        assertEquals(HttpStatus.CREATED, created.statusCode, created.body.toString())
        val data = created.body!!["data"]
        assertTrue(data["averagePaceSecondsPerKm"].isNull)
        assertTrue(data["bestPaceSecondsPerKm"].isNull)
        assertTrue(data["averageSpeedKmh"].isNull)
        assertTrue(data["goalAchieved"].asBoolean())

        val invalid = command(
            "/api/v1/running-records", HttpMethod.POST, token,
            mapOf(
                "runType" to "DISTANCE_TARGET",
                "completedAt" to "2026-09-07T12:00:00Z",
                "activeDurationSeconds" to 600,
                "distanceMeters" to 1_000,
            ),
        )
        assertEquals(HttpStatus.BAD_REQUEST, invalid.statusCode)
        assertEquals("VALIDATION_ERROR", invalid.body!!["error"]["code"].asText())
    }

    @Test
    fun `route stays private rejects duplicate timestamps and is removed with its owned record`() {
        val owner = signup()
        val stranger = signup()
        val base = mapOf(
            "runType" to "FREE",
            "startedAt" to "2026-09-07T11:00:00Z",
            "completedAt" to "2026-09-07T11:02:00Z",
            "activeDurationSeconds" to 60,
            "distanceMeters" to 0,
            "routePoints" to listOf(
                mapOf("latitude" to 0.0, "longitude" to 0.0, "recordedAt" to "2026-09-07T11:00:00Z"),
                mapOf("latitude" to 0.001, "longitude" to 0.001, "recordedAt" to "2026-09-07T11:01:00Z"),
            ),
        )
        assertEquals(HttpStatus.BAD_REQUEST, command(
            "/api/v1/running-records", HttpMethod.POST, owner, base + ("routeVisibility" to "PUBLIC"),
        ).statusCode)
        val duplicateTime = base + ("routeVisibility" to "PRIVATE") + ("routePoints" to listOf(
            mapOf("latitude" to 0.0, "longitude" to 0.0, "recordedAt" to "2026-09-07T11:00:00Z"),
            mapOf("latitude" to 0.001, "longitude" to 0.001, "recordedAt" to "2026-09-07T11:00:00Z"),
        ))
        assertEquals(HttpStatus.BAD_REQUEST, command("/api/v1/running-records", HttpMethod.POST, owner, duplicateTime).statusCode)

        val created = command("/api/v1/running-records", HttpMethod.POST, owner, base + ("routeVisibility" to "PRIVATE"))
        assertEquals(HttpStatus.CREATED, created.statusCode, created.body.toString())
        val id = created.body!!["data"]["id"].asText()
        assertEquals(HttpStatus.NOT_FOUND, command("/api/v1/running-records/$id", HttpMethod.DELETE, stranger, null).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, command("/api/v1/running-records/$id", HttpMethod.DELETE, owner, null).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, command("/api/v1/running-records/$id", HttpMethod.GET, owner, null).statusCode)
        assertTrue(command("/api/v1/running-records?cursor=0&size=20", HttpMethod.GET, owner, null).body!!["data"]["items"].isEmpty)
    }

    @Test
    fun `deleting a running record removes its external time series before source reuse`() {
        val owner = signup()
        val run = mapOf(
            "clientRecordId" to UUID.randomUUID().toString(),
            "runType" to "FREE",
            "startedAt" to "2026-09-09T11:00:00Z",
            "completedAt" to "2026-09-09T11:02:00Z",
            "activeDurationSeconds" to 120,
            "distanceMeters" to 0,
        )
        val first = command("/api/v1/running-records", HttpMethod.POST, owner, run)
        assertEquals(HttpStatus.CREATED, first.statusCode, first.body.toString())
        val firstId = first.body!!["data"]["id"].asText()
        val externalPath = "/api/v1/workout-sessions/$firstId/external-workout"
        val series = mapOf(
            "provider" to "SAMSUNG_HEALTH_DATA_SDK",
            "sourceRecordId" to "deleted-running-series-source",
            "exerciseType" to "RUNNING",
            "startedAt" to run["startedAt"],
            "endedAt" to run["completedAt"],
            "activeDurationSeconds" to 120,
            "samples" to listOf(
                mapOf("measuredAt" to run["startedAt"], "heartRate" to 100),
                mapOf("measuredAt" to run["completedAt"], "speedMetersPerSecond" to 3.0),
            ),
        )
        assertEquals(HttpStatus.OK, command(externalPath, HttpMethod.PUT, owner, series).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, command("/api/v1/running-records/$firstId", HttpMethod.DELETE, owner, null).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, command(externalPath, HttpMethod.GET, owner, null).statusCode)

        val second = command("/api/v1/running-records", HttpMethod.POST, owner, run + ("clientRecordId" to UUID.randomUUID().toString()))
        assertEquals(HttpStatus.CREATED, second.statusCode, second.body.toString())
        val secondPath = "/api/v1/workout-sessions/${second.body!!["data"]["id"].asText()}/external-workout"
        assertEquals(HttpStatus.OK, command(secondPath, HttpMethod.PUT, owner, series).statusCode)
    }

    private fun signup(): String {
        val body = mapOf(
            "email" to "running-${UUID.randomUUID()}@example.com",
            "password" to "test-password-123",
            "nickname" to "running-${UUID.randomUUID().toString().take(20)}",
            "sports" to listOf(mapOf("sportCode" to "RUNNING", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to listOf(
                "20000000-0000-0000-0000-000000000001",
                "20000000-0000-0000-0000-000000000002",
            ),
        )
        val response = http.postForEntity("/api/v1/auth/signup", body, JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode, response.body.toString())
        return response.body!!["data"]["tokens"]["accessToken"].asText()
    }

    private fun command(path: String, method: HttpMethod, token: String, body: Any?) = http.exchange(
        path,
        method,
        HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token) }),
        JsonNode::class.java,
    )
}
