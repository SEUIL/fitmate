package com.fitmate.backend.sportrecord.api

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.sportrecord.application.SportRecordPersistenceService
import com.fitmate.backend.sportrecord.domain.SportRecord
import com.fitmate.backend.sportrecord.repository.SportRecordRepository
import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
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
class SportRecordApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var sessions: WorkoutSessionRepository
    @Autowired private lateinit var records: SportRecordRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @SpyBean private lateinit var persistence: SportRecordPersistenceService

    @AfterEach
    fun resetPersistenceSpy() = reset(persistence)

    @Test
    fun `four sports save time only and results then read details and lists`() {
        val owner = signup()
        val cases = listOf(
            Triple("BADMINTON", "DOUBLES", mapOf("gameCount" to 3, "winCount" to 2)),
            Triple("CLIMBING", "BOULDERING", mapOf("attemptCount" to 5, "completionCount" to 3)),
            Triple("HIKING", "HIKE", mapOf("distanceMeters" to 4_200, "courseName" to "북한산")),
            Triple("GOLF", "FIELD", mapOf("holeCount" to 9, "strokeCount" to 42)),
        )

        cases.forEachIndexed { index, (sport, type, results) ->
            val timeOnly = create(owner, sport, type, UUID.randomUUID(), "time-$index")
            assertEquals(HttpStatus.CREATED, timeOnly.statusCode, timeOnly.body.toString())
            results.keys.forEach { field -> assertTrue(timeOnly.body!!["data"][field].isNull) }

            val withResults = create(owner, sport, type, UUID.randomUUID(), "result-$index", results)
            assertEquals(HttpStatus.CREATED, withResults.statusCode, withResults.body.toString())
            val id = withResults.body!!["data"]["id"].asText()
            val detail = command("/api/v1/sport-records/$id", HttpMethod.GET, owner, null)
            assertEquals(HttpStatus.OK, detail.statusCode, detail.body.toString())
            assertEquals(sport, detail.body!!["data"]["sportCode"].asText())
            results.forEach { (field, value) -> assertEquals(value.toString(), detail.body!!["data"][field].asText()) }

            val list = command("/api/v1/sport-records?sportCode=$sport&cursor=0&size=20", HttpMethod.GET, owner, null)
            assertEquals(HttpStatus.OK, list.statusCode, list.body.toString())
            assertEquals(2, list.body!!["data"]["items"].size())
        }
    }

    @Test
    fun `same id same body concurrent posts create one parent and detail`() {
        val owner = signup(); val id = UUID.randomUUID(); val gate = saveGate(2)
        val results = concurrent(owner, listOf(payload("BADMINTON", "DOUBLES", id, "same"), payload("BADMINTON", "DOUBLES", id, "same")), gate)

        assertEquals(listOf(200, 201), results.map { it.statusCode.value() }.sorted(), results.toString())
        assertTrue(sessions.existsById(dbId(id)))
        assertTrue(records.existsById(dbId(id)))
        assertEquals(1, sessions.findAll().count { it.id == dbId(id) })
        assertEquals(1, records.findAll().count { it.workoutSessionId == dbId(id) })
    }

    @Test
    fun `same id different body concurrent posts return conflict without overwrite`() {
        val owner = signup(); val id = UUID.randomUUID(); val gate = saveGate(2)
        val results = concurrent(owner, listOf(payload("BADMINTON", "DOUBLES", id, "first"), payload("BADMINTON", "DOUBLES", id, "second")), gate)

        assertEquals(listOf(201, 409), results.map { it.statusCode.value() }.sorted(), results.toString())
        val created = results.single { it.statusCode == HttpStatus.CREATED }.body!!["data"]
        val detail = command("/api/v1/sport-records/${id}", HttpMethod.GET, owner, null)
        assertEquals(created["memo"].asText(), detail.body!!["data"]["memo"].asText())
        assertEquals(1, sessions.findAll().count { it.id == dbId(id) })
        assertEquals(1, records.findAll().count { it.workoutSessionId == dbId(id) })
    }

    @Test
    fun `other user and owners other record ids are classified in normal and recovery paths`() {
        val owner = signup(); val stranger = signup()
        val normalId = UUID.randomUUID()
        assertEquals(HttpStatus.CREATED, create(owner, "BADMINTON", "DOUBLES", normalId, "owner").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, create(stranger, "BADMINTON", "DOUBLES", normalId, "other").statusCode)

        val ownerOtherId = UUID.randomUUID()
        insertOtherRecord(owner, ownerOtherId)
        assertEquals(HttpStatus.CONFLICT, create(owner, "BADMINTON", "DOUBLES", ownerOtherId, "normal").statusCode)

        val recoveryOtherUserId = UUID.randomUUID()
        insertBeforeSave { insertOtherRecord(stranger, recoveryOtherUserId) }
        assertEquals(HttpStatus.NOT_FOUND, create(owner, "BADMINTON", "DOUBLES", recoveryOtherUserId, "recovery-other").statusCode)

        reset(persistence)
        val recoveryOwnerId = UUID.randomUUID()
        insertBeforeSave { insertOtherRecord(owner, recoveryOwnerId) }
        assertEquals(HttpStatus.CONFLICT, create(owner, "BADMINTON", "DOUBLES", recoveryOwnerId, "recovery-owner").statusCode)
    }

    @Test
    fun `detail database failure rolls back parent session`() {
        val owner = signup(); val id = UUID.randomUUID()
        doAnswer { invocation ->
            (invocation.arguments[1] as SportRecord).activeDurationSeconds = 0
            invocation.callRealMethod()
        }.`when`(persistence).save(anyValue(), anyValue())

        val failed = create(owner, "BADMINTON", "DOUBLES", id, "rollback")
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.statusCode, failed.body.toString())
        assertFalse(sessions.existsById(dbId(id)))
        assertFalse(records.existsById(dbId(id)))
    }

    private data class SaveGate(val entered: CountDownLatch, val release: CountDownLatch)

    private fun saveGate(parties: Int): SaveGate {
        val entered = CountDownLatch(parties); val release = CountDownLatch(1)
        doAnswer { invocation ->
            entered.countDown()
            assertTrue(release.await(20, TimeUnit.SECONDS), "both requests must pass the pre-query before INSERT")
            invocation.callRealMethod()
        }.`when`(persistence).save(anyValue(), anyValue())
        return SaveGate(entered, release)
    }

    private fun concurrent(owner: String, bodies: List<Map<String, Any>>, gate: SaveGate): List<org.springframework.http.ResponseEntity<JsonNode>> {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = bodies.map { body -> executor.submit<org.springframework.http.ResponseEntity<JsonNode>> { command("/api/v1/sport-records", HttpMethod.POST, owner, body) } }
            assertTrue(gate.entered.await(20, TimeUnit.SECONDS), "both requests did not reach actual persistence")
            gate.release.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
    }

    private fun insertBeforeSave(action: () -> Unit) {
        doAnswer { invocation -> action(); invocation.callRealMethod() }.`when`(persistence).save(anyValue(), anyValue())
    }

    private fun insertOtherRecord(token: String, id: UUID) {
        val userId = userId(token)
        TransactionTemplate(transactionManager).also { it.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }
            .executeWithoutResult {
                sessions.saveAndFlush(WorkoutSession(id = dbId(id), userId = userId, sportCode = SportCode.HEALTH,
                    status = WorkoutSessionStatus.COMPLETED, startedAt = Instant.parse("2026-09-14T10:00:00Z"),
                    endedAt = Instant.parse("2026-09-14T10:01:00Z"), clientUpdatedAt = Instant.parse("2026-09-14T10:01:00Z")))
            }
    }

    private fun create(token: String, sport: String, type: String, id: UUID, memo: String, results: Map<String, Any> = emptyMap()) =
        command("/api/v1/sport-records", HttpMethod.POST, token, payload(sport, type, id, memo) + results)

    private fun payload(sport: String, type: String, id: UUID, memo: String) = mapOf(
        "clientRecordId" to id.toString(), "sportCode" to sport, "activityType" to type,
        "startedAt" to "2026-09-14T10:00:00Z", "completedAt" to "2026-09-14T10:10:00Z",
        "activeDurationSeconds" to 540, "memo" to memo,
    )

    private fun signup(): String {
        val response = http.postForEntity("/api/v1/auth/signup", mapOf(
            "email" to "sport-${UUID.randomUUID()}@example.com", "password" to "test-password-123",
            "nickname" to "sport-${UUID.randomUUID().toString().take(20)}",
            "sports" to listOf(mapOf("sportCode" to "BADMINTON", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"),
        ), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode, response.body.toString())
        return response.body!!["data"]["tokens"]["accessToken"].asText()
    }

    private fun userId(token: String): UUID = java.util.Base64.getUrlDecoder().decode(token.split('.')[1]).let { bytes ->
        com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(bytes)["sub"].asText().let(UUID::fromString)
    }

    private fun command(path: String, method: HttpMethod, token: String, body: Any?) = http.exchange(
        path, method, HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token) }), JsonNode::class.java,
    )

    private fun dbId(id: UUID) = UuidBinaryConverter.toMysqlOrderedUuid(id)
    @Suppress("UNCHECKED_CAST") private fun <T> anyValue(): T = org.mockito.ArgumentMatchers.any<T>()

    companion object {
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }
}
