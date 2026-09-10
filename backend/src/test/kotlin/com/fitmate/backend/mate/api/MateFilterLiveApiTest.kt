package com.fitmate.backend.mate.api

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Opt-in local server regression: creates one synthetic account, then only reads recruitment data. */
@EnabledIfEnvironmentVariable(named = "FITMATE_LIVE_FILTER_TEST", matches = "true")
class MateFilterLiveApiTest {
    private val http = TestRestTemplate()
    private val base = "http://localhost:8080/api/v1"

    @Test fun `sport date and combined filters return matching rows without server errors`() {
        val terms = http.getForObject("$base/terms", JsonNode::class.java)!!["data"]["items"]
        val signup = http.postForEntity("$base/auth/signup", mapOf(
            "email" to "filter-${UUID.randomUUID()}@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "필터검증-${UUID.randomUUID().toString().take(8)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to terms.filter { it["required"].asBoolean() }.map { it["id"].asText() },
        ), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, signup.statusCode, "Synthetic account setup")
        val headers = HttpHeaders().apply { setBearerAuth(signup.body!!["data"]["tokens"]["accessToken"].asText()) }
        val today = LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        val ranges = listOf(null, today to today.plusSeconds(86400).minusNanos(1),
            today.plusSeconds(86400) to today.plusSeconds(172800).minusNanos(1), today to today.plusSeconds(604800).minusNanos(1))
        var checked = 0
        fun checkFilters(sport: String? = null, range: Pair<Instant, Instant>? = null,
                         difficulty: String? = null, status: String? = null, region: String? = null) {
            val filters = buildMap {
                sport?.let { put("sportCode", it) }; range?.let { put("from", it.first.toString()); put("to", it.second.toString()) }
                difficulty?.let { put("difficulty", it) }; status?.let { put("status", it) }; region?.let { put("regionId", it) }
            }
            val query = filters.entries.joinToString("&") { "${it.key}=${it.value}" }
            val response = http.exchange("$base/mate-posts?size=100&$query", HttpMethod.GET, HttpEntity<Void>(headers), JsonNode::class.java)
            assertEquals(HttpStatus.OK, response.statusCode, "Filter query: $query")
            val data = response.body!!["data"]
            assertTrue(data["items"].isArray)
            data["items"].forEach { row ->
                sport?.let { assertEquals(it, row["sportCode"].asText()) }
                difficulty?.let { assertEquals(it, row["difficulty"].asText()) }
                status?.let { assertEquals(it, row["status"].asText()) }
                region?.let { assertEquals(it, row["regionId"].asText()) }
                range?.let { val start = Instant.parse(row["scheduledStartAt"].asText()); assertTrue(start >= it.first && start <= it.second) }
            }
            checked++
        }
        listOf(null, "HEALTH", "RUNNING", "BADMINTON", "CLIMBING", "HIKING", "GOLF").forEach { sport ->
            ranges.forEach { range -> checkFilters(sport, range) }
        }
        listOf("BEGINNER", "INTERMEDIATE", "ADVANCED").forEach { checkFilters(difficulty = it) }
        listOf("RECRUITING", "FULL", "CLOSED").forEach { checkFilters(status = it) }
        val region = "10000000-0000-0000-0000-000000000001"
        checkFilters(region = region)
        checkFilters("HEALTH", ranges[1], "BEGINNER", "RECRUITING", region)
        assertEquals(36, checked)
        val invalid = http.exchange("$base/mate-posts?sportCode=INVALID", HttpMethod.GET, HttpEntity<Void>(headers), JsonNode::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, invalid.statusCode)
        assertEquals("VALIDATION_ERROR", invalid.body!!["error"]["code"].asText())
    }
}
