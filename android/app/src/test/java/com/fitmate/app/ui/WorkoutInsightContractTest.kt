package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WorkoutInsightContractTest {
    @Test fun noConsentOmitsConsentVersionAndOptionalBody() {
        val result = workoutInsightPayload(UUID.randomUUID().toString(), null, false, false, "")
        assertEquals(setOf("requestId", "includeSamsungHealth", "externalAiConsent", "historyWeeks", "inbodyRecordIds", "includePreviousReports"), result.keys)
        assertEquals(false, result["externalAiConsent"])
        assertEquals(4, result["historyWeeks"])
        assertEquals(emptyList<String>(), result["inbodyRecordIds"])
        assertEquals(false, result["includePreviousReports"])
    }
    @Test fun explicitSelectionOnlySendsIdentifiersAndFlags() {
        val body = UUID.randomUUID().toString()
        val result = workoutInsightPayload(UUID.randomUUID().toString(), body, true, true, "workout-insight-demo-v1")
        assertEquals(body, result["inbodyRecordId"])
        assertEquals("workout-insight-demo-v1", result["consentVersion"])
        assertEquals(8, result.size)
    }
    @Test(expected = IllegalArgumentException::class) fun consentWithoutVersionRejected() {
        workoutInsightPayload(UUID.randomUUID().toString(), null, true, true, "")
    }
    @Test fun multiBodyAndPeriodAreExplicit() {
        val ids = List(3) { UUID.randomUUID().toString() }
        val value = workoutInsightPayload(UUID.randomUUID().toString(), null, true, true, "workout-insight-demo-v3", 8, ids, true)
        assertEquals(ids, value["inbodyRecordIds"])
        assertEquals(8, value["historyWeeks"])
        assertEquals(true, value["includePreviousReports"])
        assertFalse(value.containsKey("inbodyRecordId"))
    }
    @Test fun invalidLongitudinalSelectionRejected() {
        val id = UUID.randomUUID().toString()
        listOf({ workoutInsightPayload(id, null, false, false, "", 5) },
            { workoutInsightPayload(id, null, false, false, "", 4, listOf(id, id)) },
            { workoutInsightPayload(id, id, false, false, "", 4, listOf(id)) },
            { workoutInsightPayload(id, null, false, false, "", 4, List(13) { UUID.randomUUID().toString() }) }).forEach { operation ->
            assertThrows(IllegalArgumentException::class.java) { operation() }
        }
    }
}
