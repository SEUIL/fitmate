package com.fitmate.backend.routine.api

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.routine.domain.Routine
import com.fitmate.backend.routine.domain.RoutineSourceType
import com.fitmate.backend.routine.repository.RoutineRepository
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
class RoutineQueryApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var routines: RoutineRepository
    @Autowired private lateinit var sports: SportRepository

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test
    fun `authenticated user can get exact recommendation templates and template detail`() {
        val account = signup(GoalCode.FITNESS, SkillLevel.BEGINNER)

        val recommended = get("/api/v1/routines/recommended?sportCode=HEALTH", account.token)
        assertEquals("40000000-0000-0000-0000-000000000001", recommended["id"].asText())
        assertEquals("초급 전신 루틴", recommended["name"].asText())
        assertEquals(4, recommended["items"].size())
        assertEquals(listOf(1, 2, 3, 4), recommended["items"].map { it["sequenceNo"].asInt() })

        val templates = get("/api/v1/routines/templates", account.token)["items"]
        assertEquals(5, templates.size())
        assertTrue(templates.all { it["template"].asBoolean() })
        assertEquals(0, get("/api/v1/routines", account.token)["items"].size())

        val detail = get("/api/v1/routines/${recommended["id"].asText()}", account.token)
        assertEquals(recommended, detail)
    }

    @Test
    fun `recommendation uses goal-wide match then beginner fallback`() {
        val habit = signup(GoalCode.HABIT, SkillLevel.OVER_THREE_YEARS)
        assertEquals("40000000-0000-0000-0000-000000000005",
            get("/api/v1/routines/recommended?sportCode=HEALTH", habit.token)["id"].asText())

        val noMatch = signup(GoalCode.RECORD_IMPROVEMENT, SkillLevel.OVER_THREE_YEARS)
        assertEquals("40000000-0000-0000-0000-000000000001",
            get("/api/v1/routines/recommended?sportCode=HEALTH", noMatch.token)["id"].asText())
    }

    @Test
    fun `personal routine list and detail are isolated by authenticated owner`() {
        val owner = signup(GoalCode.HABIT, SkillLevel.BEGINNER)
        val stranger = signup(GoalCode.HABIT, SkillLevel.BEGINNER)
        val health = sports.findByCodeAndIsActiveTrue(SportCode.HEALTH)!!
        val routineId = UUID.randomUUID()
        routines.saveAndFlush(Routine(
            id = UuidBinaryConverter.toMysqlOrderedUuid(routineId), ownerUserId = owner.userId, sport = health, name = "내 루틴",
            sourceType = RoutineSourceType.MANUAL, isTemplate = false, isActive = true,
        ))

        val ownerList = get("/api/v1/routines", owner.token)["items"]
        assertTrue(ownerList.any { it["id"].asText() == routineId.toString() })
        val strangerList = get("/api/v1/routines", stranger.token)["items"]
        assertFalse(strangerList.any { it["id"].asText() == routineId.toString() })

        assertEquals("내 루틴", get("/api/v1/routines/$routineId", owner.token)["name"].asText())
        val hidden = exchange("/api/v1/routines/$routineId", stranger.token)
        assertEquals(HttpStatus.NOT_FOUND, hidden.statusCode)
        assertEquals("RESOURCE_NOT_FOUND", hidden.body!!["error"]["code"].asText())
    }

    @Test
    fun `reference catalog exposes logical ids that its detail endpoints accept`() {
        val sport = http.getForEntity("/api/v1/sports", JsonNode::class.java)
        assertEquals("01000000-0000-0000-0000-000000000001", sport.body!!["data"]["items"][0]["id"].asText())
        val exerciseId = "30000000-0000-0000-0000-000000000001"
        val exercise = http.getForEntity("/api/v1/exercises/$exerciseId", JsonNode::class.java)
        assertEquals(HttpStatus.OK, exercise.statusCode, exercise.body.toString())
        assertEquals(exerciseId, exercise.body!!["data"]["id"].asText())
        val substitutions = http.getForEntity("/api/v1/exercises/$exerciseId/substitutions", JsonNode::class.java)
        assertEquals(HttpStatus.OK, substitutions.statusCode, substitutions.body.toString())
        assertTrue(substitutions.body!!["data"]["items"].size() > 0)
    }

    @Test
    fun `routine queries require authentication and reject inactive sport`() {
        listOf("/api/v1/routines", "/api/v1/routines/templates", "/api/v1/routines/recommended?sportCode=HEALTH")
            .forEach {
                val response = http.getForEntity(it, JsonNode::class.java)
                assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
                assertEquals("INVALID_ACCESS_TOKEN", response.body!!["error"]["code"].asText())
            }
        val account = signup(GoalCode.FITNESS, SkillLevel.BEGINNER)
        val inactive = exchange("/api/v1/routines/recommended?sportCode=RUNNING", account.token)
        assertEquals(HttpStatus.BAD_REQUEST, inactive.statusCode)
        assertEquals("VALIDATION_ERROR", inactive.body!!["error"]["code"].asText())
    }

    private fun signup(goal: GoalCode, skill: SkillLevel): Account {
        val body = mapOf(
            "email" to "routine-${UUID.randomUUID()}@example.com",
            "password" to "test-password-123",
            "nickname" to "routine-${UUID.randomUUID().toString().take(20)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to skill.name)),
            "goals" to listOf(mapOf("goalCode" to goal.name, "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"),
        )
        val response = http.postForEntity("/api/v1/auth/signup", body, JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode, response.body.toString())
        val data = response.body!!["data"]
        return Account(UUID.fromString(data["user"]["id"].asText()), data["tokens"]["accessToken"].asText())
    }

    private fun get(path: String, token: String): JsonNode {
        val response = exchange(path, token)
        assertEquals(HttpStatus.OK, response.statusCode, response.body.toString())
        return response.body!!["data"]
    }

    private fun exchange(path: String, token: String) = http.exchange(
        path, HttpMethod.GET, HttpEntity<Void>(HttpHeaders().also { it.setBearerAuth(token) }), JsonNode::class.java,
    )

    private data class Account(val userId: UUID, val token: String)
    private enum class GoalCode { FITNESS, HABIT, RECORD_IMPROVEMENT }
    private enum class SkillLevel { BEGINNER, OVER_THREE_YEARS }
}
