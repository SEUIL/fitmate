package com.fitmate.backend.admin.application

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.admin.domain.*
import com.fitmate.backend.admin.repository.*
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.reporting.domain.*
import com.fitmate.backend.reporting.repository.UserReportRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
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
class AdminApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: AdminRoleRepository
    @Autowired private lateinit var reports: UserReportRepository
    @Autowired private lateinit var sanctions: AdminSanctionRepository
    @Autowired private lateinit var expiry: AccountSuspensionService
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test fun `real HTTP decisions persist details and overlapping suspension stays blocked`() {
        val (admin, adminId) = signup()
        val (member, memberId) = signup()
        assertEquals(HttpStatus.FORBIDDEN, call("admin/dashboard", member).statusCode)
        roles.saveAndFlush(AdminRoleAssignment(adminId, AdminRole.ADMIN, Instant.now()))
        assertTrue(call("admin/access", admin).body!!["data"]["eligible"].asBoolean())
        fun newReport(): UUID = requireNotNull(reports.saveAndFlush(UserReport(
            id = UUID.randomUUID(), reporterUserId = signup().second, targetId = memberId,
            detail = "Reporter statement", reason = ReportReason.SPAM)).id)
        fun decide(id: UUID, days: Int): JsonNode {
            val claim = call("admin/reports/$id/claim", admin, mapOf("expectedVersion" to 0))
            assertEquals(HttpStatus.OK, claim.statusCode)
            val version = claim.body!!["data"]["report"]["version"].asLong()
            val body = mapOf("expectedVersion" to version, "resolution" to "RESOLVED", "action" to "SUSPEND",
                "reasonCode" to "SPAM", "publicReason" to "Test sanction", "internalNote" to "Operator note", "durationDays" to days)
            val key = UUID.randomUUID().toString()
            val result = call("admin/reports/$id/decisions", admin, body, key)
            assertEquals(HttpStatus.OK, result.statusCode)
            val retry = call("admin/reports/$id/decisions", admin, body, key)
            assertEquals(HttpStatus.OK, retry.statusCode)
            assertEquals(1, sanctions.findAll().count { it.reportId == id })
            assertEquals(HttpStatus.CONFLICT, call("admin/reports/$id/decisions", admin, body + ("publicReason" to "Changed"), key).statusCode)
            val detail = call("admin/reports/$id", admin).body!!["data"]
            assertEquals("Operator note", detail["decision"]["internalNote"].asText())
            assertFalse(detail["decision"]["sanctionEndsAt"].isNull)
            assertEquals("Reporter statement", detail["evidence"]["reporterStatement"].asText())
            assertEquals("UNAVAILABLE", detail["evidence"]["availability"].asText())
            assertTrue(detail["allowedActions"].isEmpty)
            return detail
        }
        val longId = newReport(); decide(longId, 30)
        assertEquals(HttpStatus.FORBIDDEN, call("users/me", member).statusCode)
        val shortId = newReport(); decide(shortId, 1)
        val short = sanctions.findFirstByReportIdOrderByCreatedAtDesc(shortId)!!
        short.startsAt = Instant.now().minusSeconds(172800)
        short.endsAt = Instant.now().minusSeconds(86400)
        sanctions.saveAndFlush(short)
        assertEquals(AccountStatus.SUSPENDED, expiry.restoreIfExpired(users.findById(memberId).orElseThrow()).accountStatus)
        val long = sanctions.findFirstByReportIdOrderByCreatedAtDesc(longId)!!
        long.startsAt = Instant.now().minusSeconds(172800)
        long.endsAt = Instant.now().minusSeconds(1)
        sanctions.saveAndFlush(long)
        assertEquals(AccountStatus.ACTIVE, expiry.restoreIfExpired(users.findById(memberId).orElseThrow()).accountStatus)
        assertEquals(HttpStatus.BAD_REQUEST, call("admin/reports?cursor=LTE", admin).statusCode)
    }

    private fun signup(): Pair<String, UUID> {
        val email = "admin-test-${UUID.randomUUID()}@example.com"
        val response = http.postForEntity("/api/v1/auth/signup", mapOf(
            "email" to email, "password" to UUID.randomUUID().toString(), "nickname" to "admin-${UUID.randomUUID().toString().take(18)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["data"]["tokens"]["accessToken"].asText() to
            requireNotNull(users.findByEmailNormalizedAndAccountStatusNot(email, AccountStatus.WITHDRAWN)!!.id)
    }
    private fun call(path: String, token: String, body: Any? = null, key: String? = null) = http.exchange(
        "/api/v1/$path", if (body == null) HttpMethod.GET else HttpMethod.POST,
        HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token); if (key != null) it.set("Idempotency-Key", key) }), JsonNode::class.java)
}
