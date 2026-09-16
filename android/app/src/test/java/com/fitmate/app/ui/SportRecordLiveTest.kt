package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in; creates synthetic records in a dedicated new account, never the phone user's account. */
class SportRecordLiveTest {
    @Test fun fourSportsSaveIdempotentlyAndAppearInCommonHistory() {
        val url = System.getenv("FITMATE_SPORT_TEST_URL")
        assumeTrue(!url.isNullOrBlank())
        val api = FitMateApiClient(requireNotNull(url))
        api.authenticate("auth/signup", mapOf(
            "email" to "sport-ui-${UUID.randomUUID()}@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "sport-${UUID.randomUUID().toString().take(12)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")))
        val active = api.fetchSports().map { it.code }
        additionalSports.forEach { sport ->
            assertTrue(active.contains(sport.name))
            val end = System.currentTimeMillis(); val id = UUID.randomUUID().toString()
            val draft = SportDraft(sport.name, sportTypes(sport.name).keys.first(), "결과", id,
                started = end - 120000, ended = end, accumulated = 90000,
                memo = "Automated multi-sport UI integration check",
                fields = when (sport.name) {
                    "BADMINTON" -> mapOf("gameCount" to "3", "winCount" to "2")
                    "CLIMBING" -> mapOf("attemptCount" to "5", "completionCount" to "3")
                    "HIKING" -> mapOf("distanceMeters" to "1.234")
                    else -> mapOf("shotCount" to "20")
                })
            val body = draft.payload()
            repeat(2) { assertTrue(sportRecordMatches(body, api.request("POST", "sport-records", body).asJsonObject)) }
            assertTrue(sportRecordMatches(body, api.request("GET", "sport-records/$id").asJsonObject))
            val items = ServerRecordApi(api).page("workout-sessions?status=COMPLETED&sportCode=${sport.name}").rows("items")
            assertEquals(listOf(id), items.map { it.text("id") })
        }
        assertEquals(4, ServerRecordApi(api).completedCount("workout-sessions?status=COMPLETED"))
    }
}
