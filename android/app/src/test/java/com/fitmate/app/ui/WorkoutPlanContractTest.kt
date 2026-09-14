package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test

class WorkoutPlanContractTest {
    @Test fun `invalid conditions never become a generation request`() {
        for ((days, minutes) in listOf("0" to "45", "8" to "45", "3" to "19", "3" to "181", "" to "")) {
            assertNotNull(workoutPlanConditionsError(days, minutes))
            assertThrows(IllegalArgumentException::class.java) { workoutPlanPayload("id", days, minutes, emptyList(), emptyList(), emptyList(), null, false, "v1") }
        }
    }
    @Test fun `retry keeps request identity and exclusions while consent is opt in`() {
        val payload = workoutPlanPayload("same-request", "2", "30", listOf("BODYWEIGHT"), listOf("preferred"), listOf("excluded"), null, false, "v1")
        assertEquals("same-request", payload["requestId"])
        assertEquals(listOf("excluded"), payload["excludedExerciseIds"])
        assertEquals(false, payload["externalAiConsent"])
        assertFalse(payload.containsKey("consentVersion"))
        assertFalse(payload.containsKey("inbodyRecordId"))
    }
    @Test fun `consenting request includes exact selected record and version only`() {
        val payload = workoutPlanPayload("id", "7", "180", emptyList(), emptyList(), emptyList(), "selected-record", true, "demo-v1")
        assertEquals("selected-record", payload["inbodyRecordId"])
        assertEquals("demo-v1", payload["consentVersion"])
        assertEquals(setOf("requestId", "daysPerWeek", "sessionMinutes", "equipmentTypes", "preferredExerciseIds", "excludedExerciseIds", "inbodyRecordId", "externalAiConsent", "consentVersion"), payload.keys)
    }
}
