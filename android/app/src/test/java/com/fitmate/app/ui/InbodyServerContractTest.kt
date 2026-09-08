package com.fitmate.app.ui

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class InbodyServerContractTest {
    @Test fun `manual entry clears previous analysis identity`() {
        val state = InbodyState().apply { analysisId = "old-analysis"; createUncertain = true }
        state.openManual()
        assertNull(state.analysisId)
        assertFalse(state.createUncertain)
        state.analysisId = "another-analysis"
        state.selectPhoto("content://new-photo")
        assertNull(state.analysisId)
    }

    @Test fun `save maps edited values and negative weight control`() {
        val state = InbodyState().apply {
            openManual(); date = "2026.09.01"; weight = "71.2"; muscle = "30.5"; fat = "21.4"
            extras = mapOf("체중 조절 (kg)" to "-2.3", "신장 (cm)" to "")
        }
        assertNull(validateBody(state.date, state.weight, state.muscle, state.fat, state.extras))
        val body = inbodySaveBody(state)
        assertNull(body["analysisId"])
        assertEquals("2026-09-01", body["measuredDate"])
        assertEquals(71.2, body["weightKg"])
        assertEquals(mapOf("WEIGHT_CONTROL_KG" to -2.3), body["optionalValues"])
    }

    @Test fun `record keeps server UUID date and optional fields`() {
        val record = recordEntry(JsonParser.parseString("""
            {"id":"server-record","measuredDate":"2026-09-01","weightKg":71.2,
             "skeletalMuscleMassKg":30.5,"bodyFatPercentage":21.4,
             "optionalValues":{"WEIGHT_CONTROL_KG":-2.3},"sourceType":"AI_OCR"}
        """).asJsonObject)
        assertEquals("server-record", record.id)
        assertEquals(-2.3, record.extras["체중 조절 (kg)"])
        assertEquals("사진 판독", record.source)
    }
}
