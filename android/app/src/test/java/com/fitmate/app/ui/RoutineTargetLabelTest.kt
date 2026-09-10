package com.fitmate.app.ui

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RoutineTargetLabelTest {
    @Test fun optionalTargetsDoNotBecomeInventedWeightOrRepetitions() {
        assertEquals("3세트", routineTargetLabel(JsonParser.parseString("""{"targetSets":3,"targetWeightKg":null,"targetRepsMin":null}""").asJsonObject))
        assertEquals("목표 값 미지정", routineTargetLabel(JsonParser.parseString("{}").asJsonObject))
    }

    @Test fun repetitionRangeAndOneSidedBoundsRetainMeaning() {
        assertEquals("8–12회", routineTargetLabel(JsonParser.parseString("""{"targetRepsMin":8,"targetRepsMax":12}""").asJsonObject))
        assertEquals("10회", routineTargetLabel(JsonParser.parseString("""{"targetRepsMin":10,"targetRepsMax":10}""").asJsonObject))
        assertEquals("8회 이상", routineTargetLabel(JsonParser.parseString("""{"targetRepsMin":8}""").asJsonObject))
        assertEquals("12회 이하", routineTargetLabel(JsonParser.parseString("""{"targetRepsMax":12}""").asJsonObject))
    }
}
