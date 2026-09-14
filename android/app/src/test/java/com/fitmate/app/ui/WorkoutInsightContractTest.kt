package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WorkoutInsightContractTest {
    @Test fun noConsentOmitsConsentVersionAndOptionalBody() {
        val result = workoutInsightPayload(UUID.randomUUID().toString(), null, false, false, "")
        assertEquals(setOf("requestId", "includeSamsungHealth", "externalAiConsent"), result.keys)
        assertEquals(false, result["externalAiConsent"])
    }
    @Test fun explicitSelectionOnlySendsIdentifiersAndFlags() {
        val body = UUID.randomUUID().toString()
        val result = workoutInsightPayload(UUID.randomUUID().toString(), body, true, true, "workout-insight-demo-v1")
        assertEquals(body, result["inbodyRecordId"])
        assertEquals("workout-insight-demo-v1", result["consentVersion"])
        assertEquals(5, result.size)
    }
    @Test(expected = IllegalArgumentException::class) fun consentWithoutVersionRejected() {
        workoutInsightPayload(UUID.randomUUID().toString(), null, true, true, "")
    }
}
