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
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".*/fitmate_admin_tools_test.*")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}", "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}", "fitmate.admin.demo-auth-enabled=true",
])
class AdminOperatorApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: AdminRoleRepository
    @Autowired private lateinit var receipts: OperatorReauthRepository
    @Autowired private lateinit var attempts: OperatorAuthAttemptsRepository
    companion object {
        private val demoCode = SecureRandom().nextInt(1000000).toString().padStart(6, '0')
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
            registry.add("fitmate.admin.demo-code") { demoCode }
        }
    }
    private data class Account(val token: String, val id: UUID, val email: String, val password: String)

    @Test fun `only super admin can inspect roles or request test authentication`() {
        for (role in listOf(null, AdminRole.ADMIN, AdminRole.MODERATOR)) {
            val account = signup(role)
            assertEquals(HttpStatus.FORBIDDEN, call("operators", account).statusCode)
            assertEquals(HttpStatus.FORBIDDEN, call("operators/reauth", account, mapOf("code" to demoCode)).statusCode)
        }
    }
    @Test fun `grant retry change and revoke preserve receipts and invalidate target sessions`() {
        val actor = signup(AdminRole.SUPER_ADMIN)
        val subject = signup()
        val reauth = authenticate(actor)
        val key = UUID.randomUUID().toString()
        val request = changeRequest(version(actor), AdminRole.MODERATOR)
        val first = change(actor, subject.id, request, reauth, key)
        assertEquals(HttpStatus.OK, first.statusCode, first.body.toString())
        assertEquals(first.body, change(actor, subject.id, request, reauth, key).body)
        assertEquals(1, users.findById(subject.id).orElseThrow().authVersion)
        assertEquals(HttpStatus.UNAUTHORIZED, call("access", subject).statusCode)
        val relogged = login(subject)
        assertEquals("MODERATOR", call("access", relogged).body!!["data"]["role"].asText())
        assertEquals(HttpStatus.CONFLICT, change(actor, subject.id, changeRequest(version(actor), AdminRole.ADMIN), reauth, key).statusCode)
        val updated = change(actor, subject.id, changeRequest(version(actor), AdminRole.ADMIN), reauth)
        assertEquals(HttpStatus.OK, updated.statusCode)
        val revoked = change(actor, subject.id, changeRequest(version(actor), AdminRole.ADMIN, false), reauth)
        assertEquals(HttpStatus.OK, revoked.statusCode)
        assertNotNull(roles.findById(subject.id).orElseThrow().revokedAt)
        assertFalse(call("access", login(subject)).body!!["data"]["eligible"].asBoolean())
    }
    @Test fun `self mutation and stale catalog version are rejected`() {
        val actor = signup(AdminRole.SUPER_ADMIN)
        val subject = signup()
        val auth = authenticate(actor)
        val version = version(actor)
        assertEquals(HttpStatus.FORBIDDEN, change(actor, actor.id, changeRequest(version, AdminRole.ADMIN), auth).statusCode)
        assertEquals(HttpStatus.OK, change(actor, subject.id, changeRequest(version, AdminRole.ADMIN), auth).statusCode)
        assertEquals(HttpStatus.CONFLICT, change(actor, subject.id, changeRequest(version, AdminRole.MODERATOR), auth).statusCode)
        assertEquals(AdminRole.SUPER_ADMIN, roles.findById(actor.id).orElseThrow().role)
    }
    @Test fun `two super admins cannot revoke each other at the same time`() {
        val first = signup(AdminRole.SUPER_ADMIN)
        val second = signup(AdminRole.SUPER_ADMIN)
        val auths = listOf(authenticate(first), authenticate(second))
        val request = changeRequest(version(first), AdminRole.SUPER_ADMIN, false)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf(Callable { change(first, second.id, request, auths[0]).statusCode },
                Callable { change(second, first.id, request, auths[1]).statusCode })).map { it.get() }
            assertEquals(1, results.count { it == HttpStatus.OK })
            assertEquals(1, results.count { it == HttpStatus.FORBIDDEN || it == HttpStatus.UNAUTHORIZED })
            assertEquals(1, listOf(first, second).count { roles.findById(it.id).orElseThrow().revokedAt == null })
        } finally { pool.shutdownNow() }
    }
    @Test fun `authentication is scoped to its owner session and expiry`() {
        val actor = signup(AdminRole.SUPER_ADMIN)
        val other = signup(AdminRole.SUPER_ADMIN)
        val subject = signup()
        val auth = authenticate(actor)
        val request = changeRequest(version(actor), AdminRole.ADMIN)
        assertEquals(HttpStatus.FORBIDDEN, change(other, subject.id, request, auth).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, change(login(actor), subject.id, request, auth).statusCode)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(auth.toByteArray()).joinToString("") { "%02x".format(it) }
        val receipt = receipts.findById(hash).orElseThrow()
        receipt.expiresAt = Instant.now().minusSeconds(1); receipts.saveAndFlush(receipt)
        assertEquals("OPERATOR_REAUTH_REQUIRED", change(actor, subject.id, request, auth).body!!["error"]["code"].asText())
    }
    @Test fun `five wrong codes lock only that operator and expire after five minutes`() {
        val actor = signup(AdminRole.SUPER_ADMIN)
        val wrong = if (demoCode == "000000") "111111" else "000000"
        repeat(5) { assertEquals(HttpStatus.BAD_REQUEST, call("operators/reauth", actor, mapOf("code" to wrong)).statusCode) }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, call("operators/reauth", actor, mapOf("code" to demoCode)).statusCode)
        val state = attempts.findById(actor.id).orElseThrow()
        state.blockedUntil = Instant.now().minusSeconds(1); attempts.saveAndFlush(state)
        assertEquals(HttpStatus.OK, call("operators/reauth", actor, mapOf("code" to demoCode)).statusCode)
    }
    private fun changeRequest(version: Long, role: AdminRole, enabled: Boolean = true) = mapOf("expectedVersion" to version,
        "role" to role.name, "enabled" to enabled, "reason" to "Synthetic operator integration verification")
    private fun version(actor: Account) = call("operators", actor).body!!["data"]["version"].asLong()
    private fun authenticate(actor: Account): String {
        val response = call("operators/reauth", actor, mapOf("code" to demoCode))
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!["data"]["token"].asText()
    }
    private fun change(actor: Account, target: UUID, body: Any, auth: String, key: String = UUID.randomUUID().toString()) =
        call("operators/$target", actor, body, HttpMethod.PUT, auth, key)
    private fun call(path: String, actor: Account, body: Any? = null, method: HttpMethod = if (body == null) HttpMethod.GET else HttpMethod.POST,
        auth: String? = null, key: String = UUID.randomUUID().toString()) = http.exchange("/api/v1/admin/$path", method,
        HttpEntity(body, HttpHeaders().also { it.setBearerAuth(actor.token); it.set("Idempotency-Key", key); auth?.let { value -> it.set("X-Admin-Reauth", value) } }), JsonNode::class.java)
    private fun login(account: Account): Account {
        val response = http.postForEntity("/api/v1/auth/login", mapOf("email" to account.email, "password" to account.password), JsonNode::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return account.copy(token = response.body!!["data"]["tokens"]["accessToken"].asText())
    }
    private fun signup(role: AdminRole? = null): Account {
        val email = "operators-${UUID.randomUUID()}@example.com"
        val password = UUID.randomUUID().toString()
        val response = http.postForEntity("/api/v1/auth/signup", mapOf("email" to email, "password" to password,
            "nickname" to "ops-${UUID.randomUUID().toString().take(18)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val id = requireNotNull(users.findByEmailNormalizedAndAccountStatusNot(email, AccountStatus.WITHDRAWN)!!.id)
        if (role != null) roles.saveAndFlush(AdminRoleAssignment(id, role, Instant.now()))
        return Account(response.body!!["data"]["tokens"]["accessToken"].asText(), id, email, password)
    }
}
