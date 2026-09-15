package com.fitmate.backend.admin.application

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.admin.domain.AdminRoleAssignment
import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
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
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".*/fitmate_admin_tools_test.*")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}", "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class AdminToolsApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: AdminRoleRepository
    @Autowired private lateinit var reports: com.fitmate.backend.reporting.repository.UserReportRepository
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test fun `notice lifecycle includes user visibility and duplicate requests`() {
        val admin = signup(AdminRole.ADMIN).first
        val member = signup().first
        val draft = create(admin)
        val id = draft["id"].asText()
        assertEquals(HttpStatus.NOT_FOUND, call("notices/$id", member).statusCode)
        val key = UUID.randomUUID().toString()
        val publish = mapOf("expectedVersion" to draft["version"].asLong())
        val published = call("admin/notices/$id/publish", admin, publish, key).body!!["data"]
        assertEquals("PUBLISHED", published["status"].asText())
        assertEquals(published, call("admin/notices/$id/publish", admin, publish, key).body!!["data"])
        assertEquals(HttpStatus.OK, call("notices/$id", member).statusCode)
        val edited = call("admin/notices/$id", admin, mapOf("title" to "Updated notice", "body" to "Updated content",
            "expectedVersion" to published["version"].asLong()), method = HttpMethod.PUT)
        assertEquals(HttpStatus.OK, edited.statusCode)
        assertEquals("Updated content", call("notices/$id", member).body!!["data"]["body"].asText())
        val ended = call("admin/notices/$id/end", admin, mapOf("expectedVersion" to edited.body!!["data"]["version"].asLong()))
        assertEquals(HttpStatus.OK, ended.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, call("notices/$id", member).statusCode)
        assertEquals(HttpStatus.CONFLICT, call("admin/notices/$id/publish", admin,
            mapOf("expectedVersion" to ended.body!!["data"]["version"].asLong())).statusCode)
    }

    @Test fun `regular members and moderators cannot operate tools`() {
        for (role in listOf(null, AdminRole.MODERATOR)) {
            val token = signup(role).first
            for (path in listOf("admin/notices", "admin/categories", "admin/system/summary")) {
                assertEquals(HttpStatus.FORBIDDEN, call(path, token).statusCode)
            }
            assertEquals(HttpStatus.FORBIDDEN, call("admin/notices", token, noticeRequest()).statusCode)
        }
    }

    @Test fun `notice writes require versions and validate content`() {
        val token = signup(AdminRole.ADMIN).first
        assertEquals(HttpStatus.BAD_REQUEST, call("admin/notices", token, mapOf("title" to "Title", "body" to "Body")).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, call("admin/notices", token, noticeRequest(" ")).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, call("admin/notices", token, noticeRequest("x".repeat(101))).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, call("admin/notices", token,
            mapOf("title" to "Title", "body" to "x".repeat(5001), "expectedVersion" to 0)).statusCode)
    }

    @Test fun `idempotency survives a lost response but never bypasses revoked access`() {
        val (token, userId) = signup(AdminRole.ADMIN)
        val key = UUID.randomUUID().toString()
        val first = call("admin/notices", token, noticeRequest(), key)
        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(first.body, call("admin/notices", token, noticeRequest(), key).body)
        assertEquals(HttpStatus.CONFLICT, call("admin/notices", token, noticeRequest("Different"), key).statusCode)
        val role = roles.findById(userId).orElseThrow()
        role.revokedAt = Instant.now(); roles.saveAndFlush(role)
        assertEquals(HttpStatus.FORBIDDEN, call("admin/notices", token, noticeRequest(), key).statusCode)
    }

    @Test fun `two operators editing the same version cannot overwrite each other`() {
        val first = signup(AdminRole.ADMIN).first
        val second = signup(AdminRole.SUPER_ADMIN).first
        val draft = create(first)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf(first, second).mapIndexed { index, token -> Callable {
                call("admin/notices/${draft["id"].asText()}", token, mapOf("title" to "Revision $index", "body" to "Body $index",
                    "expectedVersion" to draft["version"].asLong()), method = HttpMethod.PUT).statusCode
            } }).map { it.get() }
            assertEquals(1, results.count { it == HttpStatus.OK })
            assertEquals(1, results.count { it == HttpStatus.CONFLICT })
        } finally { pool.shutdownNow() }
    }

    @Test fun `category order rejects duplicates and stale versions and keeps names and activation`() {
        val token = signup(AdminRole.ADMIN).first
        val before = call("admin/categories", token).body!!["data"]
        val items = before["items"].toList()
        val ids = items.map { it["id"].asText() }.reversed()
        val version = before["version"].asLong()
        assertEquals(HttpStatus.BAD_REQUEST, call("admin/categories/order", token,
            mapOf("ids" to ids.map { ids.first() }, "expectedVersion" to version), method = HttpMethod.PUT).statusCode)
        val key = UUID.randomUUID().toString()
        val command = mapOf("ids" to ids, "expectedVersion" to version)
        try {
        val changed = call("admin/categories/order", token, command, key, HttpMethod.PUT)
        assertEquals(HttpStatus.OK, changed.statusCode, changed.body.toString())
        assertEquals(ids, changed.body!!["data"]["items"].map { it["id"].asText() })
        assertEquals(changed.body, call("admin/categories/order", token, command, key, HttpMethod.PUT).body)
        assertEquals(HttpStatus.CONFLICT, call("admin/categories/order", token, command, method = HttpMethod.PUT).statusCode)
        val original = items.associateBy { it["id"].asText() }
        changed.body!!["data"]["items"].forEach {
            assertEquals(original[it["id"].asText()]!!["active"], it["active"])
            assertEquals(original[it["id"].asText()]!!["name"], it["name"])
        }
        } finally {
            val current = call("admin/categories", token).body!!["data"]["version"].asLong()
            assertEquals(HttpStatus.OK, call("admin/categories/order", token,
                mapOf("ids" to items.map { it["id"].asText() }, "expectedVersion" to current), method = HttpMethod.PUT).statusCode)
        }
    }

    @Test fun `system summary returns aggregate only and validates range`() {
        val token = signup(AdminRole.ADMIN).first
        val response = call("admin/system/summary", token)
        assertEquals(HttpStatus.OK, response.statusCode)
        val data = response.body!!["data"]
        assertEquals(5, data["statuses"].size())
        val statuses = data["statuses"].associate { it["code"].asText() to it["count"].asLong() }
        val finished = listOf("FAILED", "COMPLETED", "REVIEW_REQUIRED").sumOf { statuses[it] ?: 0 }
        if (finished == 0L) assertTrue(data["failureRate"].isNull)
        else assertEquals((statuses["FAILED"] ?: 0).toDouble() / finished, data["failureRate"].asDouble(), 0.000001)
        val empty = call("admin/system/summary?from=1900-01-01T00:00:00Z&to=1900-01-02T00:00:00Z", token)
        assertEquals(HttpStatus.OK, empty.statusCode)
        assertTrue(empty.body!!["data"]["statuses"].all { it["count"].asLong() == 0L })
        assertTrue(empty.body!!["data"]["failureRate"].isNull)
        assertEquals("UNAVAILABLE", data["activeUsers"]["availability"].asText())
        assertEquals(HttpStatus.BAD_REQUEST, call("admin/system/summary?from=2026-01-01T00:00:00Z&to=2026-03-01T00:00:00Z", token).statusCode)
    }

    @Test fun `report pagination and status filters retain missing evidence and reject missing user sanctions`() {
        val (token, reporterId) = signup(AdminRole.SUPER_ADMIN)
        val prefix = UUID.randomUUID().toString().take(8)
        val ids = (1..25).map {
            val id = UUID.fromString(prefix + UUID.randomUUID().toString().drop(8))
            reports.saveAndFlush(com.fitmate.backend.reporting.domain.UserReport(id, reporterId,
                com.fitmate.backend.reporting.domain.ReportTargetType.POST, UUID.randomUUID(),
                com.fitmate.backend.reporting.domain.ReportReason.SELF_HARM, "Synthetic missing evidence"))
            id.toString()
        }
        val first = call("admin/reports?query=$prefix&status=RECEIVED&size=20", token).body!!["data"]
        assertEquals(20, first["items"].size())
        assertTrue(first["hasNext"].asBoolean())
        val second = call("admin/reports?query=$prefix&status=RECEIVED&size=20&cursor=${first["nextCursor"].asText()}", token).body!!["data"]
        assertEquals(5, second["items"].size())
        assertFalse(second["hasNext"].asBoolean())
        assertEquals(ids.toSet(), (first["items"].toList() + second["items"].toList()).map { it["id"].asText() }.toSet())
        val id = ids.first()
        val claimed = call("admin/reports/$id/claim", token, mapOf("expectedVersion" to 0)).body!!["data"]
        assertEquals("UNAVAILABLE", claimed["evidence"]["availability"].asText())
        assertEquals("Synthetic missing evidence", claimed["evidence"]["reporterStatement"].asText())
        assertEquals(listOf("NONE"), claimed["allowedActions"].map { it.asText() })
        assertEquals(listOf(id), call("admin/reports?query=$id&status=REVIEWING", token).body!!["data"]["items"].map { it["id"].asText() })
        assertEquals(0, call("admin/reports?query=$id&status=RECEIVED", token).body!!["data"]["items"].size())
        val dismissed = call("admin/reports/$id/decisions", token, mapOf("expectedVersion" to claimed["report"]["version"].asLong(),
            "resolution" to "DISMISSED", "action" to "NONE", "reasonCode" to "INSUFFICIENT_EVIDENCE", "publicReason" to "Missing evidence"))
        assertEquals(HttpStatus.OK, dismissed.statusCode)
        assertEquals(listOf(id), call("admin/reports?query=$id&status=DISMISSED", token).body!!["data"]["items"].map { it["id"].asText() })
        val missingUser = call("reports", token, mapOf("targetType" to "USER", "targetId" to UUID.randomUUID(), "reason" to "OTHER")).body!!["data"]["reportId"].asText()
        val reviewing = call("admin/reports/$missingUser/claim", token, mapOf("expectedVersion" to 0)).body!!["data"]
        assertEquals(listOf("NONE"), reviewing["allowedActions"].map { it.asText() })
        assertEquals(HttpStatus.FORBIDDEN, call("admin/reports/$missingUser/decisions", token, mapOf(
            "expectedVersion" to reviewing["report"]["version"].asLong(), "resolution" to "RESOLVED", "action" to "WARNING",
            "reasonCode" to "SPAM", "publicReason" to "Must not persist")).statusCode)
        assertEquals("REVIEWING", call("admin/reports/$missingUser", token).body!!["data"]["report"]["status"].asText())
    }

    private fun noticeRequest(title: String = "Test notice") = mapOf("title" to title, "body" to "Test body", "expectedVersion" to 0)
    private fun create(token: String): JsonNode {
        val result = call("admin/notices", token, noticeRequest())
        assertEquals(HttpStatus.OK, result.statusCode, result.body.toString())
        return result.body!!["data"]
    }
    private fun signup(role: AdminRole? = null): Pair<String, UUID> {
        val email = "tools-${UUID.randomUUID()}@example.com"
        val response = http.postForEntity("/api/v1/auth/signup", mapOf("email" to email, "password" to UUID.randomUUID().toString(),
            "nickname" to "tools-${UUID.randomUUID().toString().take(18)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val id = requireNotNull(users.findByEmailNormalizedAndAccountStatusNot(email, AccountStatus.WITHDRAWN)!!.id)
        if (role != null) roles.saveAndFlush(AdminRoleAssignment(id, role, Instant.now()))
        return response.body!!["data"]["tokens"]["accessToken"].asText() to id
    }
    private fun call(path: String, token: String, body: Any? = null, key: String = UUID.randomUUID().toString(),
        method: HttpMethod = if (body == null) HttpMethod.GET else HttpMethod.POST) = http.exchange("/api/v1/$path", method,
        HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token); if (body != null) it.set("Idempotency-Key", key) }), JsonNode::class.java)
}
