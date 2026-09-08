package com.fitmate.app.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule

/** Opt in with liveRecords=true and adb reverse tcp:8080 tcp:8080. Creates isolated test data. */
class LiveRecordIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun deviceStoresAndReloadsRunAndWorkoutReport() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveRecords") == "true")
        val api = FitMateApiClient(BuildConfig.API_BASE_URL)
        val unique = UUID.randomUUID().toString()
        api.authenticate("auth/signup", mapOf(
            "email" to "device-$unique@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "device-${unique.take(16)}",
            "sports" to listOf("HEALTH", "RUNNING").map { mapOf("sportCode" to it, "skillLevel" to "BEGINNER") },
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")
        ))
        val records = ServerRecordApi(api)
        val state = RunningState().apply { goal = "거리"; target = 1000; seconds = 360; meters = 1000; laps = listOf(RunLap(1, 1000, 360)) }
        val body = Gson().toJsonTree(runningPayload(state, System.currentTimeMillis()) + ("clientRecordId" to UUID.randomUUID().toString())).asJsonObject
        val run = records.saveRun(body)
        assertEquals(run.text("id"), records.saveRun(body).text("id"))
        assertEquals(1, records.page("running-records").rows("items").size)
        assertEquals(1000, records.run(run.text("id")).recordInt("distanceMeters"))
        val exerciseId = api.fetchExercises().first().id
        val session = api.request("POST", "workout-sessions", mapOf("exercises" to listOf(mapOf(
            "exerciseId" to exerciseId, "sequenceNo" to 1, "targetSets" to 1
        )))).asJsonObject
        val id = session.text("id")
        val started = api.request("POST", "workout-sessions/$id/start", mapOf("version" to session.recordInt("version"))).asJsonObject
        api.request("PUT", "workout-sessions/$id/exercises/${session.rows("exercises").single().text("id")}/sets/1",
            mapOf("weightKg" to 20, "repetitions" to 10, "completed" to true,
                "version" to session.rows("exercises").single().rows("sets").single().recordInt("version")))
        api.request("POST", "workout-sessions/$id/complete", mapOf("version" to started.recordInt("version")))
        assertTrue(records.page("workout-sessions?status=COMPLETED").rows("items").any { it.text("id") == id })
        assertEquals(1, records.report(id).recordInt("completedSetCount"))
        assertEquals(200.0, records.report(id).get("totalVolumeKg").asDouble, 0.01)
        assertEquals("COMPLETED", records.session(id).text("status"))
        compose.setContent { MaterialTheme { Column { ServerRecordList(api) } } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("헬스 운동").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("헬스 운동").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("200", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
