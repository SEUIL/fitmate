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
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
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
        assertEquals(8, templates.size())
        assertTrue(templates.all { it["template"].asBoolean() })
        assertEquals(0, get("/api/v1/routines", account.token)["items"].size())

        val detail = get("/api/v1/routines/${recommended["id"].asText()}", account.token)
        assertEquals(recommended, detail)
    }

    @Test
    fun `recommendation prefers experience then goal-wide match and beginner fallback`() {
        val habit = signup(GoalCode.HABIT, SkillLevel.OVER_THREE_YEARS)
        assertEquals("40000000-0000-0000-0000-000000000008",
            get("/api/v1/routines/recommended?sportCode=HEALTH", habit.token)["id"].asText())
        val earlyHabit = signup(GoalCode.HABIT, SkillLevel.BEGINNER)
        assertEquals("40000000-0000-0000-0000-000000000005",
            get("/api/v1/routines/recommended?sportCode=HEALTH", earlyHabit.token)["id"].asText())

        val noMatch = signup(GoalCode.RECORD_IMPROVEMENT, SkillLevel.BEGINNER)
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
        val sportItems = sport.body!!["data"]["items"]
        assertEquals(listOf("HEALTH", "RUNNING"), sportItems.map { it["code"].asText() })
        assertEquals(
            listOf(
                "01000000-0000-0000-0000-000000000001",
                "01000000-0000-0000-0000-000000000002",
            ),
            sportItems.map { it["id"].asText() },
        )
        val exerciseId = "30000000-0000-0000-0000-000000000001"
        val exercise = http.getForEntity("/api/v1/exercises/$exerciseId", JsonNode::class.java)
        assertEquals(HttpStatus.OK, exercise.statusCode, exercise.body.toString())
        assertEquals(exerciseId, exercise.body!!["data"]["id"].asText())
        val substitutions = http.getForEntity("/api/v1/exercises/$exerciseId/substitutions", JsonNode::class.java)
        assertEquals(HttpStatus.OK, substitutions.statusCode, substitutions.body.toString())
        assertTrue(substitutions.body!!["data"]["items"].size() > 0)
    }

    @Test
    fun `routine queries require authentication and reject running for health routines`() {
        listOf("/api/v1/routines", "/api/v1/routines/templates", "/api/v1/routines/recommended?sportCode=HEALTH")
            .forEach {
                val response = http.getForEntity(it, JsonNode::class.java)
                assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
                assertEquals("INVALID_ACCESS_TOKEN", response.body!!["error"]["code"].asText())
            }
        val account = signup(GoalCode.FITNESS, SkillLevel.BEGINNER)
        val running = exchange("/api/v1/routines/recommended?sportCode=RUNNING", account.token)
        assertEquals(HttpStatus.BAD_REQUEST, running.statusCode)
        assertEquals("VALIDATION_ERROR", running.body!!["error"]["code"].asText())
        assertEquals("추천 루틴은 헬스 종목만 지원합니다.", running.body!!["error"]["message"].asText())
    }

    @Test
    fun `owner can create update and delete a manual routine`() {
        val owner = signup(GoalCode.FITNESS, SkillLevel.BEGINNER)
        val stranger = signup(GoalCode.FITNESS, SkillLevel.BEGINNER)
        val createBody = mapOf(
            "name" to "나의 전신 루틴",
            "sourceType" to "MANUAL",
            "sportCode" to "HEALTH",
            "items" to listOf(
                mapOf(
                    "exerciseId" to "30000000-0000-0000-0000-000000000001",
                    "sequenceNo" to 1,
                    "targetSets" to 3,
                    "targetRepsMin" to 8,
                    "targetRepsMax" to 12,
                    "restSeconds" to 60,
                ),
                mapOf(
                    "exerciseId" to "30000000-0000-0000-0000-000000000002",
                    "sequenceNo" to 2,
                    "targetSets" to 3,
                ),
            ),
        )
        val created = command("/api/v1/routines", HttpMethod.POST, owner.token, createBody)
        assertEquals(HttpStatus.CREATED, created.statusCode, created.body.toString())
        val routineId = created.body!!["data"]["id"].asText()
        assertEquals("MANUAL", created.body!!["data"]["sourceType"].asText())
        assertEquals(2, created.body!!["data"]["items"].size())

        val forbidden = command(
            "/api/v1/routines/$routineId", HttpMethod.PATCH, stranger.token,
            mapOf("version" to 0, "name" to "가로채기"),
        )
        assertEquals(HttpStatus.NOT_FOUND, forbidden.statusCode)

        val updated = command(
            "/api/v1/routines/$routineId", HttpMethod.PATCH, owner.token,
            mapOf(
                "version" to 0,
                "name" to "수정한 루틴",
                "items" to listOf(mapOf(
                    "exerciseId" to "30000000-0000-0000-0000-000000000003",
                    "sequenceNo" to 1,
                    "targetSets" to 4,
                )),
            ),
        )
        assertEquals(HttpStatus.OK, updated.statusCode, updated.body.toString())
        assertEquals("수정한 루틴", updated.body!!["data"]["name"].asText())
        assertEquals(1, updated.body!!["data"]["items"].size())
        assertEquals(1, updated.body!!["data"]["version"].asInt())

        val itemsOnly = command(
            "/api/v1/routines/$routineId", HttpMethod.PATCH, owner.token,
            mapOf(
                "version" to 1,
                "items" to listOf(mapOf(
                    "exerciseId" to "30000000-0000-0000-0000-000000000004",
                    "sequenceNo" to 1,
                    "targetSets" to 3,
                )),
            ),
        )
        assertEquals(HttpStatus.OK, itemsOnly.statusCode, itemsOnly.body.toString())
        assertEquals(2, itemsOnly.body!!["data"]["version"].asInt())

        val stale = command(
            "/api/v1/routines/$routineId", HttpMethod.PATCH, owner.token,
            mapOf("version" to 1, "name" to "오래된 수정"),
        )
        assertEquals(HttpStatus.CONFLICT, stale.statusCode)
        assertEquals("VERSION_CONFLICT", stale.body!!["error"]["code"].asText())

        val deleted = command(
            "/api/v1/routines/$routineId?version=2", HttpMethod.DELETE, owner.token, null,
        )
        assertEquals(HttpStatus.NO_CONTENT, deleted.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, exchange("/api/v1/routines/$routineId", owner.token).statusCode)
    }

    @Test
    fun `owner can copy a system template`() {
        val account = signup(GoalCode.FITNESS, SkillLevel.BEGINNER)
        val body = mapOf(
            "name" to "내 초급 루틴",
            "sourceType" to "USER_COPY",
            "sourceRoutineId" to "40000000-0000-0000-0000-000000000001",
        )
        val copied = command("/api/v1/routines", HttpMethod.POST, account.token, body)
        assertEquals(HttpStatus.CREATED, copied.statusCode, copied.body.toString())
        assertEquals("USER_COPY", copied.body!!["data"]["sourceType"].asText())
        assertEquals(4, copied.body!!["data"]["items"].size())
        assertFalse(copied.body!!["data"]["template"].asBoolean())
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

    private fun command(path: String, method: HttpMethod, token: String, body: Any?) = http.exchange(
        path,
        method,
        HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token) }),
        JsonNode::class.java,
    )

    private data class Account(val userId: UUID, val token: String)
    private enum class GoalCode { FITNESS, HABIT, RECORD_IMPROVEMENT }
    private enum class SkillLevel { BEGINNER, OVER_THREE_YEARS }
}
