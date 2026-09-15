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

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".*/fitmate_admin_tools_test.*")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}", "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class AppealApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: AdminRoleRepository
    @Autowired private lateinit var reports: UserReportRepository
    @Autowired private lateinit var sanctions: AdminSanctionRepository
    @Autowired private lateinit var sessions: AppealSessionRepository
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }
    private data class Account(val token: String, val id: UUID, val email: String, val password: String)

    @Test fun `suspended member can authenticate only for appeals and cannot access another member sanction`() {
        val operator = signup(AdminRole.ADMIN); val member = signup(); val other = signup()
        val sanction = sanction(operator, member)
        assertEquals(HttpStatus.FORBIDDEN, login(member).statusCode)
        val auth = appealAuth(member)
        assertEquals(HttpStatus.OK, call("appeal-access/sanctions", auth).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, call("users/me", auth).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, call("admin/access", auth).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, call("appeal-access/sanctions", "Bearer ${operator.token}").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, call("appeal-access/appeals", appealAuth(other), mapOf("sanctionId" to sanction, "statement" to "Other member")).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, http.postForEntity("/api/v1/auth/appeal-sessions", mapOf("email" to member.email, "password" to "incorrect"), JsonNode::class.java).statusCode)
    }
    @Test fun `accepting one overlapping sanction preserves suspension until remaining sanction is lifted`() {
        val operator = signup(AdminRole.ADMIN); val member = signup()
        val first = sanction(operator, member); val second = sanction(operator, member)
        val auth = appealAuth(member)
        val a = submit(auth, first); val b = submit(auth, second)
        decide(operator, claim(operator, a), "ACCEPTED")
        assertEquals(AccountStatus.SUSPENDED, users.findById(member.id).orElseThrow().accountStatus)
        assertNotNull(sanctions.findById(first).orElseThrow().revokedAt)
        assertNull(sanctions.findById(second).orElseThrow().revokedAt)
        assertEquals(HttpStatus.FORBIDDEN, login(member).statusCode)
        decide(operator, claim(operator, b), "ACCEPTED")
        assertEquals(AccountStatus.ACTIVE, users.findById(member.id).orElseThrow().accountStatus)
        assertEquals(HttpStatus.OK, login(member).statusCode)
        assertTrue(call("appeal-access/sanctions", auth).body!!["data"]["items"].all { !it["canAppeal"].asBoolean() })
    }
    @Test fun `submission and decision retries are idempotent and public results omit internal notes`() {
        val operator = signup(AdminRole.ADMIN); val member = signup(); val sanction = sanction(operator, member)
        val auth = appealAuth(member); val key = UUID.randomUUID().toString()
        val body = mapOf("sanctionId" to sanction, "statement" to "Please review the evidence")
        val first = call("appeal-access/appeals", auth, body, key)
        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(first.body, call("appeal-access/appeals", auth, body, key).body)
        assertEquals(HttpStatus.CONFLICT, call("appeal-access/appeals", auth, body + ("statement" to "changed"), key).statusCode)
        assertEquals(HttpStatus.CONFLICT, call("appeal-access/appeals", auth, body).statusCode)
        val claimed = claim(operator, first.body!!["data"])
        val response = decide(operator, claimed, "REJECTED")
        assertEquals("Private reviewer note", response["internalNote"].asText())
        val visible = call("appeal-access/sanctions", auth).body!!["data"]["items"][0]["latestAppeal"]
        assertEquals("REJECTED", visible["status"].asText())
        assertEquals("Review result", visible["publicReason"].asText())
        assertTrue(visible["internalNote"].isNull)
        assertFalse(visible["canReview"].asBoolean())
        assertEquals(AccountStatus.SUSPENDED, users.findById(member.id).orElseThrow().accountStatus)
        assertNull(sanctions.findById(sanction).orElseThrow().revokedAt)
        assertEquals(HttpStatus.OK, call("appeal-access/appeals", auth, body).statusCode)
    }
    @Test fun `moderator can read but cannot claim and stale decisions are rejected`() {
        val operator = signup(AdminRole.ADMIN); val moderator = signup(AdminRole.MODERATOR); val member = signup()
        val appeal = submit(appealAuth(member), sanction(operator, member))
        val id = appeal["id"].asText()
        assertEquals(HttpStatus.OK, call("admin/appeals/$id", "Bearer ${moderator.token}").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, call("admin/appeals/$id/claim", "Bearer ${moderator.token}", mapOf("expectedVersion" to 0)).statusCode)
        val claimed = claim(operator, appeal)
        assertEquals(HttpStatus.CONFLICT, call("admin/appeals/$id/decision", "Bearer ${operator.token}",
            mapOf("expectedVersion" to 0, "status" to "ACCEPTED", "publicReason" to "Result")).statusCode)
        assertEquals("REVIEWING", call("admin/appeals/$id", "Bearer ${operator.token}").body!!["data"]["status"].asText())
        decide(operator, claimed, "REJECTED")
    }
    @Test fun `limited authentication expires and auth version changes invalidate it`() {
        val member = signup(); val auth = appealAuth(member)
        val token = auth.removePrefix("Appeal ")
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        val session = sessions.findById(hash).orElseThrow()
        session.expiresAt = Instant.now().minusSeconds(1); sessions.saveAndFlush(session)
        assertEquals(HttpStatus.UNAUTHORIZED, call("appeal-access/sanctions", auth).statusCode)
        val fresh = appealAuth(member)
        val user = users.findById(member.id).orElseThrow(); user.authVersion++; users.saveAndFlush(user)
        assertEquals(HttpStatus.UNAUTHORIZED, call("appeal-access/sanctions", fresh).statusCode)
    }
    @Test fun `own appeal and other operator appeal require an independent super admin`() {
        val admin = signup(AdminRole.ADMIN); val moderator = signup(AdminRole.MODERATOR); val superAdmin = signup(AdminRole.SUPER_ADMIN)
        val own = submit(appealAuth(admin), sanction(admin, admin, ModerationAction.WARNING))
        assertEquals(HttpStatus.FORBIDDEN, call("admin/appeals/${own["id"].asText()}/claim", "Bearer ${admin.token}", mapOf("expectedVersion" to 0)).statusCode)
        val other = submit(appealAuth(moderator), sanction(admin, moderator, ModerationAction.WARNING))
        assertEquals(HttpStatus.FORBIDDEN, call("admin/appeals/${other["id"].asText()}/claim", "Bearer ${admin.token}", mapOf("expectedVersion" to 0)).statusCode)
        decide(superAdmin, claim(superAdmin, other), "ACCEPTED")
        val visible = call("appeal-access/appeals", appealAuth(moderator)).body!!["data"]["items"]
        assertEquals(1, visible.size()); assertEquals(moderator.id.toString(), visible[0]["userId"].asText()); assertTrue(visible[0]["internalNote"].isNull)
    }
    private fun submit(auth: String, sanction: UUID): JsonNode {
        val response = call("appeal-access/appeals", auth, mapOf("sanctionId" to sanction, "statement" to "Please review"))
        assertEquals(HttpStatus.OK, response.statusCode, response.body.toString())
        return response.body!!["data"]
    }
    private fun claim(operator: Account, appeal: JsonNode): JsonNode {
        val response = call("admin/appeals/${appeal["id"].asText()}/claim", "Bearer ${operator.token}", mapOf("expectedVersion" to appeal["version"].asLong()))
        assertEquals(HttpStatus.OK, response.statusCode, response.body.toString()); return response.body!!["data"]
    }
    private fun decide(operator: Account, appeal: JsonNode, status: String): JsonNode {
        val path = "admin/appeals/${appeal["id"].asText()}/decision"
        val body = mapOf("expectedVersion" to appeal["version"].asLong(), "status" to status, "publicReason" to "Review result", "internalNote" to "Private reviewer note")
        val key = UUID.randomUUID().toString()
        val first = call(path, "Bearer ${operator.token}", body, key)
        assertEquals(HttpStatus.OK, first.statusCode, first.body.toString())
        assertEquals(first.body, call(path, "Bearer ${operator.token}", body, key).body)
        assertEquals(HttpStatus.CONFLICT, call(path, "Bearer ${operator.token}", body + ("publicReason" to "changed"), key).statusCode)
        return first.body!!["data"]
    }
    private fun sanction(operator: Account, member: Account, action: ModerationAction = ModerationAction.SUSPEND): UUID {
        val report = reports.saveAndFlush(UserReport(id = UUID.randomUUID(), reporterUserId = signup().id, targetId = member.id, detail = "Test report", reason = ReportReason.SPAM))
        val row = sanctions.saveAndFlush(AdminSanction(UUID.randomUUID(), member.id, report.id, action,
            ModerationReason.SPAM, "Test suspension", Instant.now(), Instant.now().plusSeconds(86400), operator.id, Instant.now()))
        if (action == ModerationAction.SUSPEND) { val user = users.findById(member.id).orElseThrow(); user.accountStatus = AccountStatus.SUSPENDED; users.saveAndFlush(user) }
        return requireNotNull(row.id)
    }
    private fun appealAuth(member: Account): String {
        val response = http.postForEntity("/api/v1/auth/appeal-sessions", mapOf("email" to member.email, "password" to member.password), JsonNode::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, response.body.toString())
        return "Appeal ${response.body!!["data"]["token"].asText()}"
    }
    private fun call(path: String, auth: String, body: Any? = null, key: String = UUID.randomUUID().toString()) = http.exchange("/api/v1/$path",
        if (body == null) HttpMethod.GET else HttpMethod.POST, HttpEntity(body, HttpHeaders().also { it.set("Authorization", auth); it.set("Idempotency-Key", key) }), JsonNode::class.java)
    private fun login(account: Account) = http.postForEntity("/api/v1/auth/login", mapOf("email" to account.email, "password" to account.password), JsonNode::class.java)
    private fun signup(role: AdminRole? = null): Account {
        val email = "appeals-${UUID.randomUUID()}@example.com"; val password = UUID.randomUUID().toString()
        val response = http.postForEntity("/api/v1/auth/signup", mapOf("email" to email, "password" to password,
            "nickname" to "appeal-${UUID.randomUUID().toString().take(16)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val id = requireNotNull(users.findByEmailNormalizedAndAccountStatusNot(email, AccountStatus.WITHDRAWN)!!.id)
        if (role != null) roles.saveAndFlush(AdminRoleAssignment(id, role, Instant.now()))
        return Account(response.body!!["data"]["tokens"]["accessToken"].asText(), id, email, password)
    }
}
