package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test

class SportRecordStateTest {
    @Test fun timerExcludesPausesAndKeepsTheSameId() {
        val s = SportRecordState(); s.prepare("BADMINTON"); s.start(100000, 1000, 2)
        val id = s.draft!!.id
        s.pause(4500); assertEquals(3500L, s.draft!!.millis(9000))
        s.resume(10000); s.finish(113000, 14000)
        assertEquals(7500L, s.draft!!.accumulated); assertEquals(id, s.draft!!.id)
        assertEquals(7, s.draft!!.payload().get("activeDurationSeconds").asInt)
    }
    @Test fun restoreContinuesSameBootButPausesAfterReboot() {
        val d = SportDraft("HIKING", "HIKE", accumulated = 10000, anchor = 20000, checkpoint = 20000, boot = 3)
        assertEquals(15000L, d.restore(25000, 3).millis(25000))
        assertNull(d.restore(25000, 4).anchor)
        assertEquals(10000L, d.restore(500, 4).accumulated)
    }
    @Test fun distanceUsesExactDecimalsAndRejectsInvalidValues() {
        assertEquals(1234, distanceInMeters("1.234")); assertEquals(1000000, distanceInMeters("1000"))
        listOf("-1", "1.0001", "NaN", "1e3", "9999999999999").forEach { assertNull(distanceInMeters(it)) }
        assertTrue(SportDraft("HIKING", "HIKE", fields = mapOf("distanceMeters" to "1000.001")).errors().isNotEmpty())
    }
    @Test fun validatesCountsAndClearsFieldsOnTypeChange() {
        assertTrue(SportDraft("BADMINTON", "SINGLES", fields = mapOf("winCount" to "2")).errors().containsKey("gameCount"))
        assertTrue(SportDraft("CLIMBING", "LEAD", fields = mapOf("attemptCount" to "1", "completionCount" to "2")).errors().containsKey("completionCount"))
        assertTrue(SportDraft("GOLF", "FIELD", fields = mapOf("holeCount" to "19")).errors().containsKey("holeCount"))
        assertTrue(SportDraft("GOLF", "PRACTICE", fields = mapOf("shotCount" to "50")).changeType("FIELD").fields.isEmpty())
    }
    @Test fun optionalValuesStayAbsentAndRecoveryChecksContents() {
        val d = SportDraft("GOLF", "PRACTICE", id = "id", started = 1000, ended = 4000, accumulated = 2000)
        val body = d.payload(); assertFalse(body.has("shotCount"))
        val response = body.deepCopy().apply { remove("clientRecordId"); addProperty("id", "id") }
        assertTrue(sportRecordMatches(body, response))
        response.addProperty("activeDurationSeconds", 3); assertFalse(sportRecordMatches(body, response))
    }
}
