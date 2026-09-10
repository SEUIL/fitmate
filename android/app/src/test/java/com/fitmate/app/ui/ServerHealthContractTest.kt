package com.fitmate.app.ui

import com.fitmate.app.data.remote.StoredExternalWorkout
import com.fitmate.app.data.remote.externalWorkoutContentMatches
import com.fitmate.app.data.remote.toStoredExternalWorkout
import com.fitmate.app.samsunghealth.ExternalWorkoutSummary
import com.google.gson.JsonParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ServerHealthContractTest {
    private val now = Instant.parse("2026-09-10T01:00:00.123456Z")

    @Test fun `normalizes put decimal and timestamps before lost response comparison`() {
        val request = summary(BigDecimal("123.456"))
        val response = stored(summary(BigDecimal("123.46")).copy(
            startedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
            endedAt = now.plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
        ))
        assertTrue(externalWorkoutContentMatches(request, response))
        assertFalse(externalWorkoutContentMatches(request, stored(summary(BigDecimal("123.47")))))
    }

    @Test fun `treats null as null and preserves modified time`() {
        val request = summary(null)
        assertTrue(externalWorkoutContentMatches(request, stored(request)))
        assertFalse(externalWorkoutContentMatches(request, stored(request.copy(sourceModifiedAt = now.plusSeconds(1)))))
    }

    @Test fun `parses omitted optional fields and numeric equivalents`() {
        val parsed = JsonParser.parseString(
            """{"sessionId":"session","provider":"SAMSUNG_HEALTH_DATA_SDK","sourceRecordId":"Case Sensitive UID ","exerciseType":"BENCH_PRESS","startedAt":"2026-09-10T01:00:00.123Z","endedAt":"2026-09-10T01:01:00.123Z","activeDurationSeconds":60,"caloriesKcal":1.0,"meanHeartRate":100,"version":0,"savedAt":"2026-09-10T01:02:00Z","updatedAt":"2026-09-10T01:02:00Z"}""",
        ).asJsonObject.toStoredExternalWorkout().let { value -> value.copy(summary = value.summary.copy(sourceModifiedAt = now)) }
        assertTrue(externalWorkoutContentMatches(summary(BigDecimal("1")), parsed))
        assertTrue(parsed.summary.distanceMeters == null && parsed.summary.minHeartRate == null && parsed.summary.maxHeartRate == null)
    }

    private fun summary(calories: BigDecimal?) = ExternalWorkoutSummary(
        sourceRecordId = "Case Sensitive UID ", sourceAppId = null, sourceModifiedAt = now,
        exerciseType = "BENCH_PRESS", startedAt = now, endedAt = now.plusSeconds(60), activeDurationSeconds = 60,
        distanceMeters = null, caloriesKcal = calories, meanHeartRate = BigDecimal("100"), minHeartRate = null, maxHeartRate = null,
    )
    private fun stored(summary: ExternalWorkoutSummary) = StoredExternalWorkout("session", summary.normalized(), 0, now, now)
}
