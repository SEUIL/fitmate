package com.fitmate.backend.member.api

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import java.util.concurrent.CompletableFuture

// Opt in with a dedicated, disposable MySQL database. Flyway applies the actual migrations.
@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=root",
    "spring.datasource.password=",
])
class CustomSportsApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var users: UserRepository

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test
    fun `signup and profile round trip keeps custom interests separate from managed sports`() {
        val catalogBefore = http.getForEntity("/api/v1/sports", JsonNode::class.java).body
        val signedUp = signup(listOf(" 필라테스 ", "크로스핏"))
        val token = signedUp["tokens"]["accessToken"].asText()
        assertEquals(setOf("필라테스", "크로스핏"), names(signedUp["user"]))
        val initial = get(token)
        assertEquals(setOf("필라테스", "크로스핏"), names(initial))
        assertEquals("HEALTH", initial["sports"][0]["sportCode"].asText())

        val updated = patch(token, mapOf(
            "version" to initial["profile"]["version"].asLong(),
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "UNDER_6_MONTHS")),
            "customSports" to listOf(mapOf("name" to "  CrossFit  Club ")),
        ))
        assertEquals(HttpStatus.OK, updated.statusCode, updated.body.toString())
        val after = get(token)
        assertEquals(setOf("CrossFit Club"), names(after))
        assertEquals(initial["profile"]["version"].asLong() + 1, after["profile"]["version"].asLong())
        assertEquals("UNDER_6_MONTHS", after["sports"][0]["skillLevel"].asText())

        val omitted = patch(token, mapOf("version" to after["profile"]["version"].asLong(), "bio" to "운동 소개"))
        assertEquals(HttpStatus.OK, omitted.statusCode)
        assertEquals(setOf("CrossFit Club"), names(get(token)))
        val cleared = patch(token, mapOf("version" to omitted.body!!["data"]["profile"]["version"].asLong(), "customSports" to emptyList<Any>()))
        assertEquals(HttpStatus.OK, cleared.statusCode)
        assertTrue(names(get(token)).isEmpty())
        assertEquals(catalogBefore, http.getForEntity("/api/v1/sports", JsonNode::class.java).body)
    }

    @Test
    fun `same custom name belongs independently to each authenticated user`() {
        val first = signup(listOf("필라테스"))["tokens"]["accessToken"].asText()
        val second = signup(listOf("필라테스"))["tokens"]["accessToken"].asText()
        val response = patch(first, mapOf("version" to get(first)["profile"]["version"].asLong(), "customSports" to emptyList<Any>()))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(names(get(first)).isEmpty())
        assertEquals(setOf("필라테스"), names(get(second)))
        assertTrue(http.getForEntity("/api/v1/users/me", JsonNode::class.java).statusCode.is4xxClientError)
    }

    @Test
    fun `invalid names and normalized duplicates reject signup without leaving an account`() {
        listOf(listOf(" "), listOf("a".repeat(51)), listOf("CrossFit", " ｃｒｏｓｓｆｉｔ "), (1..21).map { "운동$it" }).forEach { names ->
            val request = signupRequest(names)
            val result = http.postForEntity("/api/v1/auth/signup", request, JsonNode::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, result.statusCode, result.body.toString())
            assertEquals("VALIDATION_ERROR", result.body!!["error"]["code"].asText())
            assertFalse(users.existsByEmailNormalizedAndAccountStatusNot(request["email"].toString(), AccountStatus.WITHDRAWN))
        }
        val nullItemRequest = signupRequest(emptyList()) + ("customSports" to listOf(null))
        val nullItemResponse = http.postForEntity("/api/v1/auth/signup", nullItemRequest, JsonNode::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, nullItemResponse.statusCode, nullItemResponse.body.toString())
        assertFalse(users.existsByEmailNormalizedAndAccountStatusNot(nullItemRequest["email"].toString(), AccountStatus.WITHDRAWN))
    }

    @Test
    fun `legacy signup defaults to empty custom interests and invalid later fields roll back replacement`() {
        val legacy = signupRequest(emptyList()).filterKeys { it != "customSports" }
        val result = http.postForEntity("/api/v1/auth/signup", legacy, JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, result.statusCode, result.body.toString())
        val token = result.body!!["data"]["tokens"]["accessToken"].asText()
        val before = get(token)
        assertTrue(names(before).isEmpty())

        val rejected = patch(token, mapOf("version" to before["profile"]["version"].asLong(),
            "customSports" to listOf(mapOf("name" to "필라테스")), "goals" to emptyList<Any>()))
        assertEquals(HttpStatus.BAD_REQUEST, rejected.statusCode)
        val after = get(token)
        assertTrue(names(after).isEmpty())
        assertEquals(before["profile"]["version"], after["profile"]["version"])
        assertEquals(HttpStatus.BAD_REQUEST, patch(token, mapOf("customSports" to emptyList<Any>())).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, patch(token, mapOf("version" to null, "customSports" to emptyList<Any>())).statusCode)
    }

    @Test
    fun `concurrent custom-only edits with same version accept exactly one request`() {
        val token = signup(listOf("필라테스"))["tokens"]["accessToken"].asText()
        val version = get(token)["profile"]["version"].asLong()
        val requests = listOf("클라이밍", "크로스핏").map { name ->
            CompletableFuture.supplyAsync { patch(token, mapOf("version" to version, "customSports" to listOf(mapOf("name" to name)))) }
        }
        val responses = requests.map { it.join() }
        assertEquals(1, responses.count { it.statusCode == HttpStatus.OK }, responses.toString())
        val conflict = responses.single { it.statusCode == HttpStatus.CONFLICT }
        assertEquals("VERSION_CONFLICT", conflict.body!!["error"]["code"].asText())
        assertEquals(version + 1, get(token)["profile"]["version"].asLong())
    }

    private fun signupRequest(names: List<String>): Map<String, Any> = mapOf(
        "email" to "custom-sports-${UUID.randomUUID()}@example.com",
        "password" to "test-password-123",
        "nickname" to "test-${UUID.randomUUID().toString().take(20)}",
        "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
        "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
        "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"),
        "customSports" to names.map { mapOf("name" to it) },
    )

    private fun signup(names: List<String>): JsonNode {
        val response = http.postForEntity("/api/v1/auth/signup", signupRequest(names), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode, response.body.toString())
        return response.body!!["data"]
    }

    private fun get(token: String): JsonNode {
        val response = http.exchange("/api/v1/users/me", HttpMethod.GET, HttpEntity<Void>(headers(token)), JsonNode::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, response.body.toString())
        return response.body!!["data"]
    }

    private fun patch(token: String, request: Map<String, Any?>): ResponseEntity<JsonNode> = http.exchange(
        "/api/v1/users/me/profile", HttpMethod.PATCH, HttpEntity(request, headers(token)), JsonNode::class.java,
    )

    private fun headers(token: String) = HttpHeaders().also { it.setBearerAuth(token) }
    private fun names(node: JsonNode): Set<String> = node["customSports"].map { it["name"].asText() }.toSet()
}
