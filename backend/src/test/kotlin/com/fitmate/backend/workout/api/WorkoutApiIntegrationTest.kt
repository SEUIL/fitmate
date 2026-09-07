package com.fitmate.backend.workout.api

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
    "spring.datasource.username=root",
    "spring.datasource.password=",
])
class WorkoutApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test
    fun `completed sets become the previous record in the next workout`() {
        val owner = signup()
        val stranger = signup()
        val first = createDirectWorkout(owner)
        val firstData = first.body!!["data"]
        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals("READY", firstData["status"].asText())
        assertEquals(2, firstData["exercises"][0]["sets"].size())
        assertFalse(firstData["exercises"][0]["sets"][0]["completed"].asBoolean())
        assertTrue(firstData["exercises"][0]["previousRecord"].isNull)
        val sessionId = firstData["id"].asText()
        val sessionExerciseId = firstData["exercises"][0]["id"].asText()

        val hidden = command("/api/v1/workout-sessions/$sessionId", HttpMethod.GET, stranger, null)
        assertEquals(HttpStatus.NOT_FOUND, hidden.statusCode)

        val started = command(
            "/api/v1/workout-sessions/$sessionId/start", HttpMethod.POST, owner,
            mapOf("version" to 0),
        )
        assertEquals(HttpStatus.OK, started.statusCode, started.body.toString())
        assertEquals("IN_PROGRESS", started.body!!["data"]["status"].asText())
        assertEquals(1, started.body!!["data"]["version"].asInt())

        val recorded = command(
            "/api/v1/workout-sessions/$sessionId/exercises/$sessionExerciseId/sets/1", HttpMethod.PUT, owner,
            mapOf("weightKg" to 22.5, "repetitions" to 12, "completed" to true, "version" to 0),
        )
        assertEquals(HttpStatus.OK, recorded.statusCode, recorded.body.toString())
        assertEquals(22.5, recorded.body!!["data"]["weightKg"].asDouble())
        assertEquals(12, recorded.body!!["data"]["repetitions"].asInt())
        assertEquals(1, recorded.body!!["data"]["version"].asInt())

        val completed = command(
            "/api/v1/workout-sessions/$sessionId/complete", HttpMethod.POST, owner,
            mapOf("version" to 1),
        )
        assertEquals(HttpStatus.OK, completed.statusCode, completed.body.toString())
        assertEquals("COMPLETED", completed.body!!["data"]["status"].asText())

        Thread.sleep(5)
        val next = createDirectWorkout(owner)
        val nextData = next.body!!["data"]
        val previous = nextData["exercises"][0]["previousRecord"]
        assertEquals(sessionId, previous["workoutSessionId"].asText())
        assertEquals(22.5, previous["sets"][0]["weightKg"].asDouble())
        assertEquals(12, previous["sets"][0]["repetitions"].asInt())

        val previousEndpoint = command(
            "/api/v1/exercises/30000000-0000-0000-0000-000000000001/previous-record?beforeSessionId=${nextData["id"].asText()}",
            HttpMethod.GET,
            owner,
            null,
        )
        assertEquals(HttpStatus.OK, previousEndpoint.statusCode, previousEndpoint.body.toString())
        assertEquals(sessionId, previousEndpoint.body!!["data"]["workoutSessionId"].asText())

        val nextSessionId = nextData["id"].asText()
        val nextExerciseId = nextData["exercises"][0]["id"].asText()
        assertEquals(HttpStatus.OK, command(
            "/api/v1/workout-sessions/$nextSessionId/start", HttpMethod.POST, owner, mapOf("version" to 0),
        ).statusCode)
        assertEquals(HttpStatus.OK, command(
            "/api/v1/workout-sessions/$nextSessionId/exercises/$nextExerciseId/sets/1", HttpMethod.PUT, owner,
            mapOf("weightKg" to 25, "repetitions" to 10, "completed" to true, "version" to 0),
        ).statusCode)
        assertEquals(HttpStatus.OK, command(
            "/api/v1/workout-sessions/$nextSessionId/complete", HttpMethod.POST, owner, mapOf("version" to 1),
        ).statusCode)

        val report = command("/api/v1/workout-sessions/$nextSessionId/report", HttpMethod.GET, owner, null)
        assertEquals(HttpStatus.OK, report.statusCode, report.body.toString())
        assertEquals("BASIC_READY", report.body!!["data"]["reportStatus"].asText())
        assertEquals(1, report.body!!["data"]["completedSetCount"].asInt())
        assertEquals(10, report.body!!["data"]["totalRepetitions"].asInt())
        assertEquals(250.0, report.body!!["data"]["totalVolumeKg"].asDouble())
        val comparison = report.body!!["data"]["exercises"][0]["previousComparison"]
        assertEquals(270.0, comparison["previousVolumeKg"].asDouble())
        assertEquals(-20.0, comparison["volumeChangeKg"].asDouble())

        val completedList = command(
            "/api/v1/workout-sessions?status=COMPLETED&cursor=0&size=20", HttpMethod.GET, owner, null,
        )
        assertEquals(HttpStatus.OK, completedList.statusCode, completedList.body.toString())
        assertEquals(2, completedList.body!!["data"]["items"].size())

        val cancellable = createDirectWorkout(owner).body!!["data"]
        val canceled = command(
            "/api/v1/workout-sessions/${cancellable["id"].asText()}/cancel", HttpMethod.POST, owner,
            mapOf("version" to 0, "cancelReason" to "사용자 취소"),
        )
        assertEquals(HttpStatus.OK, canceled.statusCode, canceled.body.toString())
        assertEquals("CANCELED", canceled.body!!["data"]["status"].asText())
    }

    private fun createDirectWorkout(token: String) = command(
        "/api/v1/workout-sessions", HttpMethod.POST, token,
        mapOf(
            "exercises" to listOf(mapOf(
                "exerciseId" to "30000000-0000-0000-0000-000000000001",
                "sequenceNo" to 1,
                "targetSets" to 2,
                "targetWeightKg" to 20,
                "targetRepetitions" to 10,
            )),
        ),
    )

    private fun signup(): String {
        val body = mapOf(
            "email" to "workout-${UUID.randomUUID()}@example.com",
            "password" to "test-password-123",
            "nickname" to "workout-${UUID.randomUUID().toString().take(20)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
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
