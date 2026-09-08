package com.fitmate.backend.mate.api

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.mate.domain.MateParticipantRole
import com.fitmate.backend.mate.repository.MateParticipationRepository
import com.fitmate.backend.notification.application.NotificationWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class MateApplicationTransactionIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var participations: MateParticipationRepository
    @MockBean private lateinit var notificationWriter: NotificationWriter

    companion object {
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test
    fun `application is rolled back when its notification write fails`() {
        val host = signup()
        val applicant = signup()
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "롤백 검증", "body" to "알림 실패", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001",
            "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 2, "difficulty" to "BEGINNER",
        ), UUID.randomUUID())
        assertEquals(HttpStatus.CREATED, post.statusCode, post.body.toString())
        val postId = UUID.fromString(post.body!!["data"]["id"].asText())

        Mockito.doThrow(IllegalStateException("notification persistence failed")).`when`(notificationWriter).mate(
            anyUuid(), ArgumentMatchers.any(UUID::class.java), ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
        )
        val applied = command("/api/v1/mate-posts/$postId/applications", HttpMethod.POST, applicant, null)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, applied.statusCode, applied.body.toString())
        assertTrue(participations.findAllByPostId(postId).none { it.role == MateParticipantRole.MEMBER })
    }

    private fun anyUuid(): UUID = ArgumentMatchers.any(UUID::class.java) ?: UUID.randomUUID()

    private fun signup(): String = http.postForEntity("/api/v1/auth/signup", mapOf(
        "email" to "mate-tx-${UUID.randomUUID()}@example.com", "password" to "test-password-123",
        "nickname" to "mate-tx-${UUID.randomUUID().toString().take(12)}",
        "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
        "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
        "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"),
    ), JsonNode::class.java).also { assertEquals(HttpStatus.CREATED, it.statusCode, it.body.toString()) }.body!!["data"]["tokens"]["accessToken"].asText()

    private fun command(path: String, method: HttpMethod, token: String, body: Any?, key: UUID? = null) = http.exchange(
        path, method, HttpEntity(body, HttpHeaders().also { headers -> headers.setBearerAuth(token); key?.let { headers["Idempotency-Key"] = it.toString() } }), JsonNode::class.java,
    )
}
