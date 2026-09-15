package com.fitmate.app.ui

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ServerWorkoutSessionTest {
    @Test fun preparationUsesServerDayAndDoesNotGuessMissingDay() {
        val routine = JsonParser.parseString("""{"nextSplitDayNo":2,"items":[{"splitDayNo":1,"exerciseName":"A"},{"splitDayNo":2,"exerciseName":"B"},{"splitDayNo":3,"exerciseName":"C"}]}""").asJsonObject
        assertEquals(listOf("B"), nextRoutineItems(routine)!!.map { it.text("exerciseName") })
        routine.remove("nextSplitDayNo")
        assertNull(nextRoutineItems(routine))
    }
    @Test fun threeDayRoutineStartsOnlyTheFourExercisesReturnedForToday() {
        val allDays = (1..3).associateWith { day -> (1..4).map { "Day $day exercise $it" } }
        for ((day, names) in allDays) {
            val response = Gson().toJsonTree(mapOf(
                "routineSplitDayNo" to day, "routineSplitCount" to 3,
                "exercises" to names.map { mapOf("exerciseName" to it, "sets" to listOf(mapOf("setNo" to 1), mapOf("setNo" to 2))) },
            )).asJsonObject
            val session = serverWorkoutSession(response, true)
            assertEquals(names, session.sessionExercises.toList())
            assertEquals(4, session.sessionExercises.size)
            assertEquals(names.first(), session.exerciseName)
            assertEquals(0, session.selectedExerciseIndex)
            assertEquals(8, session.targetSetCount)
            assertTrue(session.useSamsung)
            assertEquals(response, JsonParser.parseString(session.serverState))
            assertTrue(session.recordedSets.isEmpty())
        }
    }

    @Test fun emptyServerDayCannotStartAFullRoutineByFallback() {
        assertThrows(IllegalArgumentException::class.java) {
            serverWorkoutSession(JsonParser.parseString("""{"exercises":[]}""").asJsonObject, false)
        }
    }
}
