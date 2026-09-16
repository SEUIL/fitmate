package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.ui.theme.FitmateTheme
import com.fitmate.app.ui.theme.AppearanceMode
import com.fitmate.app.ui.theme.AppearancePreference
import com.fitmate.app.ui.theme.LocalAppearancePreference
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SportRecordDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun content(dark: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) { FitmateTheme(darkTheme = dark) { WorkoutDesignTheme(journey = true) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) { body() }
        } } } }
    }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "sport-records").apply { mkdirs() }
        val roots = compose.onAllNodes(isRoot())
        roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asAndroidBitmap().let { image ->
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }
    private fun completed(code: String, type: String) = SportDraft(code, type, "결과", java.util.UUID.randomUUID().toString(),
        started = 1_700_000_000_000, ended = 1_700_000_120_000, accumulated = 90_000)
    private fun response(body: JsonObject) = body.deepCopy().apply {
        addProperty("id", body.text("clientRecordId")); remove("clientRecordId"); addProperty("elapsedDurationSeconds", 120)
    }
    @Test fun selectionIncludesOnlyActiveSupportedSports() {
        MateUiTestServer { _, path, _ ->
            if (path == "/sports") 200 to mapOf("items" to listOf("HEALTH", "RUNNING", "BADMINTON", "CLIMBING", "HIKING", "GOLF", "OTHER").map {
                mapOf("id" to it, "code" to it, "name" to it, "displayOrder" to 0)
            }) else 404 to "not found"
        }.use { server ->
            val api = server.api(); var selected = ""
            content { SportSelectionScreen(Modifier.fillMaxSize(), api) { selected = it } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("배드민턴").fetchSemanticsNodes().isNotEmpty() }
            capture("selection")
            click("골프"); compose.runOnIdle { assertEquals("GOLF", selected) }
            compose.onNodeWithText("OTHER").assertDoesNotExist()
        }
    }
    private fun saveSport(code: String, type: String, key: String, label: String, value: String, expected: Int, dark: Boolean = false) {
        val state = SportRecordState(completed(code, type)); var posted: JsonObject? = null
        MateUiTestServer { method, path, body ->
            if (method == "POST" && path == "/sport-records") { posted = body; 201 to response(body) } else 404 to "not found"
        }.use { server ->
            val api = server.api()
            content(dark) { SportRecordFlow(Modifier.fillMaxSize(), api, state, 1, { true }, {}, {}) }
            compose.onNodeWithText(label).performScrollTo().performTextInput(value)
            compose.onNodeWithText(label).performImeAction()
            capture("$code-result")
            click("기록 저장하고 종료")
            compose.waitUntil(10000) { compose.onAllNodesWithText("기록 저장 완료").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(expected, posted!!.get(key).asInt); assertNull(state.draft)
            capture("$code-saved")
        }
    }
    @Test fun badmintonSaves() = saveSport("BADMINTON", "SINGLES", "gameCount", "게임 수", "3", 3)
    @Test fun climbingSavesInDarkMode() = saveSport("CLIMBING", "BOULDERING", "attemptCount", "시도 수", "5", 5, true)
    @Test fun hikingConvertsKilometersExactly() = saveSport("HIKING", "HIKE", "distanceMeters", "거리 (km)", "1.234", 1234)
    @Test fun golfSavesPracticeCount() = saveSport("GOLF", "PRACTICE", "shotCount", "타격 수", "20", 20)
    @Test fun acceptedPostWithLostResponseRecoversById() {
        val state = SportRecordState(completed("HIKING", "TREKKING")); var stored: JsonObject? = null
        MateUiTestServer { method, path, body -> when {
            method == "POST" && path == "/sport-records" -> { stored = response(body); 503 to "response lost" }
            path.startsWith("/sport-records/") -> 200 to requireNotNull(stored)
            else -> 404 to "not found"
        } }.use { server ->
            val api = server.api(); var durable: SportDraft? = null
            content { SportRecordFlow(Modifier.fillMaxSize(), api, state, 1, { durable = state.draft; true }, {}, {}) }
            click("기록 저장하고 종료")
            compose.waitUntil(10000) { compose.onAllNodesWithText("기록 저장 완료").fetchSemanticsNodes().isNotEmpty() }
            assertNull(state.draft); assertNull(durable)
        }
    }
    @Test fun uncertainSaveKeepsPayloadLockedAndRetriesIdentically() {
        val state = SportRecordState(completed("GOLF", "PRACTICE")); val requests = mutableListOf<String>()
        MateUiTestServer { method, path, body -> when {
            method == "POST" && path == "/sport-records" -> {
                requests.add(body.toString()); if (requests.size == 1) 503 to "offline" else 200 to response(body)
            }
            else -> 404 to "not found"
        } }.use { server ->
            val api = server.api()
            content { SportRecordFlow(Modifier.fillMaxSize(), api, state, 1, { true }, {}, {}) }
            click("기록 저장하고 종료")
            compose.waitUntil(10000) { compose.onAllNodesWithText("offline").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("타격 수").assertIsNotEnabled()
            click("같은 기록으로 다시 시도")
            compose.waitUntil(10000) { compose.onAllNodesWithText("기록 저장 완료").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(2, requests.size); assertEquals(requests[0], requests[1])
        }
    }
    @Test fun timerCanPauseResumeAndFinish() {
        val state = SportRecordState(); state.prepare("BADMINTON")
        MateUiTestServer { _, _, _ -> 404 to "unused" }.use { server ->
            val api = server.api()
            content { SportRecordFlow(Modifier.fillMaxSize(), api, state, 1, { true }, {}, {}) }
            capture("badminton-prep"); click("배드민턴 시작")
            compose.onNodeWithText("지금 움직이는 중").assertExists()
            capture("badminton-active"); click("일시정지"); compose.onNodeWithText("잠시 쉬는 중").assertExists()
            click("다시 시작"); click("운동 종료")
            compose.onNodeWithText("배드민턴 운동 결과").assertExists()
        }
    }
    @Test fun pendingPayloadSurvivesDiskRestoreAndIsAccountScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val user = java.util.UUID.randomUUID().toString()
        val original = completed("CLIMBING", "LEAD").let { it.copy(pending = it.payload().toString()) }
        assertTrue(FrontendLocalStore(context, user).save(FrontendSnapshot(sportRecord = original)))
        assertEquals(original, FrontendLocalStore(context, user).load().sportRecord)
        assertNull(FrontendLocalStore(context, java.util.UUID.randomUUID().toString()).load().sportRecord)
    }
    @Test fun commonRecordListRoutesNewSportToItsDetail() {
        val d = completed("BADMINTON", "DOUBLES")
        MateUiTestServer { _, path, _ -> when {
            path.startsWith("/workout-sessions?") -> 200 to mapOf("items" to listOf(mapOf("id" to d.id, "sportCode" to "BADMINTON", "endedAt" to "2023-11-14T22:15:20Z")), "hasNext" to false)
            path == "/sport-records/${d.id}" -> 200 to response(d.payload())
            else -> 404 to "incorrect detail route"
        } }.use { server ->
            val api = server.api()
            content { WorkoutJourneyPage { ServerRecordList(api) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("완료한 운동 · 상세 기록 보기").fetchSemanticsNodes().isNotEmpty() }
            click("완료한 운동 · 상세 기록 보기")
            compose.waitUntil(10000) { compose.onAllNodesWithText("배드민턴 · 복식").fetchSemanticsNodes().isNotEmpty() }
            capture("record-detail")
        }
    }
}
