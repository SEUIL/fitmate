package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test
import com.google.gson.Gson

class ServerRecordContractTest {
    @Test fun `all five run goals send required targets and private route`() {
        val state = RunningState().apply {
            seconds = 600
            meters = 1000
            target = 5000
            startedAt = 400_000L
        }
        listOf("자유", "거리", "시간", "페이스", "인터벌").forEach { goal ->
            state.goal = goal
            val body = runningPayload(state, 1_000_000L)
            assertEquals("PRIVATE", body["routeVisibility"])
            assertEquals("1970-01-01T00:06:40Z", body["startedAt"])
            assertEquals(goal in listOf("거리", "페이스"), body.containsKey("targetDistanceMeters"))
            assertEquals(goal in listOf("시간", "인터벌"), body.containsKey("targetDurationSeconds"))
            assertEquals(goal == "페이스", body.containsKey("targetPaceSecondsPerKm"))
            assertEquals(goal == "인터벌", body.containsKey("intervalSeconds"))
            assertFalse(body.containsKey("routePoints"))
        }
    }

    @Test fun `zero distance is valid but zero duration cannot be saved`() {
        val state = RunningState().apply { goal = "자유"; seconds = 1; startedAt = 400_000L }
        assertEquals(0, runningPayload(state, 1_000_000L)["distanceMeters"])
        state.seconds = 0
        assertThrows(IllegalArgumentException::class.java) { runningPayload(state, 1_000_000L) }
    }

    @Test fun `missing actual start time cannot be saved`() {
        val state = RunningState().apply { goal = "자유"; seconds = 1 }
        assertThrows(IllegalArgumentException::class.java) { runningPayload(state, 1_000_000L) }
    }

    @Test fun `pending request and server identity survive snapshot restore`() {
        val state = RunningState().apply {
            phase = "리포트"; seconds = 600; meters = 1000
            pendingUpload = "{\"clientRecordId\":\"frozen-request\"}"
            history.add(RunResult(1000, 600, emptyList(), "자유", 0, 5, 3, "", 1_000_000L, "server-record"))
        }
        val gson = Gson()
        val restored = gson.fromJson(gson.toJson(state.snapshot()), RunningSnapshot::class.java).restore()
        assertEquals(state.pendingUpload, restored.pendingUpload)
        assertEquals("server-record", restored.history.single().serverId)
        assertEquals("리포트", restored.phase)
    }
}
