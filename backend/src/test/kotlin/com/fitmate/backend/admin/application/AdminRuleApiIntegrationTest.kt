package com.fitmate.backend.admin.application

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.admin.domain.*
import com.fitmate.backend.admin.repository.*
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.community.domain.PostVisibility
import com.fitmate.backend.community.repository.CommunityPostRepository
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
class AdminRuleApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: AdminRoleRepository
    @Autowired private lateinit var rules: ModerationRuleRepository
    @Autowired private lateinit var detections: RuleDetectionRepository
    @Autowired private lateinit var revisions: RuleRevisionRepository
    @Autowired private lateinit var posts: CommunityPostRepository
    @Autowired private lateinit var engine: ModerationRuleEngine
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }
    private data class Account(val token: String, val id: UUID)
    @Test fun `real public post creates candidate without sanction and current private content is hidden`() {
        val admin = signup(AdminRole.ADMIN); val member = signup()
        val term = "marker${UUID.randomUUID()}"
        val rule = createRule(admin, "WORD", term)
        try {
            val created = call("community/posts", member, mapOf("content" to "Text $term", "visibility" to "PUBLIC", "mediaIds" to emptyList<String>()))
            assertEquals(HttpStatus.OK, created.statusCode, created.body.toString())
            val id = UUID.fromString(created.body!!["data"]["postId"].asText())
            val candidate = detections.findAll().single { it.ruleId == rule && it.contentId == id }
            assertEquals(AccountStatus.ACTIVE, users.findById(member.id).orElseThrow().accountStatus)
            assertEquals("Text $term", call("admin/rules/detections/${candidate.id}", admin).body!!["data"]["currentText"].asText())
            val post = posts.findById(id).orElseThrow(); post.visibility = PostVisibility.MUTUAL; posts.saveAndFlush(post)
            assertTrue(call("admin/rules/detections/${candidate.id}", admin).body!!["data"]["currentText"].isNull)
            val key = UUID.randomUUID().toString()
            val path = "admin/rules/detections/${candidate.id}/review"
            val reviewed = call(path, admin, mapOf("reason" to "Checked"), key = key)
            assertEquals(HttpStatus.OK, reviewed.statusCode)
            assertEquals(reviewed.body, call(path, admin, mapOf("reason" to "Checked"), key = key).body)
            assertEquals(AccountStatus.ACTIVE, users.findById(member.id).orElseThrow().accountStatus)
        } finally { disable(rule) }
    }
    @Test fun `repeat and rate count distinct new content while edits private content and other scopes do not count`() {
        val admin = signup(AdminRole.ADMIN); val member = signup()
        val repeat = createRule(admin, "REPEAT", ""); val rate = createRule(admin, "RATE", "")
        try {
            val first = UUID.randomUUID(); val second = UUID.randomUUID()
            engine.inspect(member.id, RuleScope.POST, first, "ＡＢＣ   hello", true, true)
            engine.inspect(member.id, RuleScope.POST, first, "abc hello", true, false)
            engine.inspect(member.id, RuleScope.POST, UUID.randomUUID(), "abc hello", false, true)
            engine.inspect(member.id, RuleScope.COMMENT, UUID.randomUUID(), "abc hello", true, true)
            assertFalse(detections.findAll().any { it.ruleId in listOf(repeat, rate) && it.userId == member.id })
            engine.inspect(member.id, RuleScope.POST, second, "abc hello", true, true)
            assertEquals(2, detections.findAll().count { it.ruleId in listOf(repeat, rate) && it.contentId == second })
            engine.inspect(member.id, RuleScope.POST, second, "abc hello", true, false)
            assertEquals(2, detections.findAll().count { it.ruleId in listOf(repeat, rate) && it.contentId == second })
        } finally { disable(repeat); disable(rate) }
    }
    @Test fun `rule preview does not persist and mutations require manager version reason and stable key`() {
        val admin = signup(AdminRole.ADMIN); val moderator = signup(AdminRole.MODERATOR)
        val id = UUID.randomUUID(); val body = ruleBody("WORD", "HELLO")
        assertEquals(HttpStatus.FORBIDDEN, call("admin/rules", moderator).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, call("admin/rules/$id", moderator, body, HttpMethod.PUT).statusCode)
        val before = rules.count(); val detectedBefore = detections.count()
        val preview = call("admin/rules/preview", admin, mapOf("rule" to body, "sampleText" to "hello ＨＥＬＬＯ"))
        assertEquals(HttpStatus.OK, preview.statusCode)
        assertTrue(preview.body!!["data"]["matched"].asBoolean())
        assertEquals(before, rules.count()); assertEquals(detectedBefore, detections.count())
        val key = UUID.randomUUID().toString()
        val saved = call("admin/rules/$id", admin, body, HttpMethod.PUT, key)
        assertEquals(HttpStatus.OK, saved.statusCode)
        try {
            assertEquals(saved.body, call("admin/rules/$id", admin, body, HttpMethod.PUT, key).body)
            assertEquals(HttpStatus.CONFLICT, call("admin/rules/$id", admin, body + ("term" to "different"), HttpMethod.PUT, key).statusCode)
            assertEquals(HttpStatus.OK, call("admin/rules/$id", admin, body + ("enabled" to false), HttpMethod.PUT).statusCode)
            assertEquals(HttpStatus.CONFLICT, call("admin/rules/$id", admin, body, HttpMethod.PUT).statusCode)
            assertEquals(2, call("admin/rules/$id/history", admin).body!!["data"]["items"].size())
            assertEquals(HttpStatus.BAD_REQUEST, call("admin/rules/${UUID.randomUUID()}", admin, body + ("term" to "  "), HttpMethod.PUT).statusCode)
        } finally { disable(id) }
    }
    @Test fun `simultaneous public posts are counted without losing a write or duplicating candidates`() {
        val admin = signup(AdminRole.ADMIN); val member = signup(); val rule = createRule(admin, "RATE", "")
        engine.beginWrite(member.id)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll((1..2).map { java.util.concurrent.Callable {
                call("community/posts", member, mapOf("content" to "Concurrent $it", "visibility" to "PUBLIC", "mediaIds" to emptyList<String>()))
            } }).map { it.get() }
            results.forEach { assertEquals(HttpStatus.OK, it.statusCode, it.body.toString()) }
            assertEquals(2, results.map { it.body!!["data"]["postId"].asText() }.toSet().size)
            assertEquals(1, detections.findAll().count { it.ruleId == rule && it.userId == member.id })
        } finally { executor.shutdownNow(); disable(rule) }
    }
    private fun createRule(admin: Account, kind: String, term: String): UUID {
        val id = UUID.randomUUID()
        val result = call("admin/rules/$id", admin, ruleBody(kind, term), HttpMethod.PUT)
        assertEquals(HttpStatus.OK, result.statusCode, result.body.toString()); return id
    }
    private fun ruleBody(kind: String, term: String) = mapOf("expectedVersion" to 0, "name" to "Synthetic rule", "kind" to kind,
        "scope" to "POST", "term" to term, "thresholdCount" to 2, "windowSeconds" to 60, "enabled" to true, "reason" to "Integration verification")
    private fun disable(id: UUID) { val row = rules.findById(id).orElseThrow(); row.enabled = false; rules.saveAndFlush(row) }
    private fun call(path: String, actor: Account, body: Any? = null, method: HttpMethod = if (body == null) HttpMethod.GET else HttpMethod.POST,
        key: String = UUID.randomUUID().toString()) = http.exchange("/api/v1/$path", method, HttpEntity(body,
        HttpHeaders().also { it.setBearerAuth(actor.token); it.set("Idempotency-Key", key) }), JsonNode::class.java)
    private fun signup(role: AdminRole? = null): Account {
        val email = "rules-${UUID.randomUUID()}@example.com"
        val response = http.postForEntity("/api/v1/auth/signup", mapOf("email" to email, "password" to UUID.randomUUID().toString(),
            "nickname" to "rule-${UUID.randomUUID().toString().take(16)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val id = requireNotNull(users.findByEmailNormalizedAndAccountStatusNot(email, AccountStatus.WITHDRAWN)!!.id)
        if (role != null) roles.saveAndFlush(AdminRoleAssignment(id, role, Instant.now()))
        return Account(response.body!!["data"]["tokens"]["accessToken"].asText(), id)
    }
}
