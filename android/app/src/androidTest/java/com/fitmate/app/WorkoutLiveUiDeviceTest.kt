package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.MainActivity
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.SessionTokenStore
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in real MainActivity -> HTTP -> MySQL regression; writes only a new synthetic account. */
class WorkoutLiveUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun lightRoutineEditWorkoutCompletionAndHistory() = exerciseFlow("LIGHT")
    @Test fun darkRoutineEditWorkoutCompletionAndHistory() = exerciseFlow("DARK")
    @Test fun lightRunningSetupFinishSaveAndHistory() = exerciseFlow("LIGHT", runningOnly = true)
    @Test fun darkRunningSetupFinishSaveAndHistory() = exerciseFlow("DARK", runningOnly = true)

    private fun waitText(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String, scroll: Boolean = true) {
        waitText(text)
        val node = compose.onNodeWithText(text, substring = false)
        if (scroll && text !in listOf("일시정지", "재개", "종료")) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "live-ui-review").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun exerciseFlow(mode: String, runningOnly: Boolean = false) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUi") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records")) { "Use the isolated test package." }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenStore = SessionTokenStore(context)
        val previousTokens = tokenStore.read()
        val appearance = context.getSharedPreferences("fitmate_appearance", 0)
        val previousMode = appearance.getString("mode", null)
        val api = FitMateApiClient(BuildConfig.API_BASE_URL, tokenStore = tokenStore)
        val unique = UUID.randomUUID().toString()
        val routineName = "UI 실기기 $mode ${unique.take(6)}"
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            api.authenticate("auth/signup", mapOf(
                "email" to "ui-$unique@example.com", "password" to UUID.randomUUID().toString(),
                "nickname" to "UI검증-${unique.take(6)}",
                "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
                "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
                "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }
            ))
            appearance.edit().putString("mode", mode).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            if (runningOnly) { runningFlow(api, mode); return }
            click("운동 시작")
            screenshot("selection-${mode.lowercase()}")
            click("헬스 시작")
            click("내 루틴으로 복사")
            waitText("수정")
            val copied = api.items("routines").single()
            val originalItems = api.request("GET", "routines/${copied.text("id")}").asJsonObject.rows("items")
            click("수정")
            compose.onNodeWithText("루틴 이름").performTextReplacement(routineName)
            click("서버에 저장")
            compose.waitUntil(20_000) { compose.onAllNodesWithText("루틴 이름").fetchSemanticsNodes().isEmpty() }
            waitText(routineName)
            val saved = api.request("GET", "routines/${copied.text("id")}").asJsonObject
            assertEquals(routineName, saved.text("name"))
            assertEquals(originalItems.map { it.text("exerciseId") }, saved.rows("items").map { it.text("exerciseId") })
            screenshot("routine-${mode.lowercase()}")
            click("이 루틴으로 운동")
            screenshot("preparation-${mode.lowercase()}")
            click("운동 시작")
            waitText("이 세트 기록")
            compose.onNodeWithText("중량 (kg)").performScrollTo().performTextReplacement("20")
            compose.onNodeWithText("횟수", substring = false).performTextReplacement("10")
            compose.onNodeWithText("RPE", substring = false).performTextReplacement("7")
            click("이 세트 기록", scroll = false)
            click("수정")
            compose.onNodeWithText("중량 (kg)").performScrollTo().performTextReplacement("22.5")
            click("세트 수정", scroll = false)
            compose.onNodeWithText("중량 (kg)").performScrollTo()
            screenshot("workout-${mode.lowercase()}")
            // Exercise the real root exit/restore path; the existing session must be reused.
            compose.onNodeWithContentDescription("뒤로가기").performClick()
            click("잠시 나가기", scroll = false)
            click("운동 이어하기")
            waitText("이 세트 기록")
            compose.onNodeWithText("총 1세트 · 225 kg").assertExists()
            click("운동 종료", scroll = false)
            waitText("헬스 리포트")
            compose.waitUntil(20_000) { compose.onAllNodesWithText("225", substring = true).fetchSemanticsNodes().isNotEmpty() }
            val completed = api.items("workout-sessions?status=COMPLETED").single()
            val report = api.request("GET", "workout-sessions/${completed.text("id")}/report").asJsonObject
            assertEquals(1, report.recordInt("completedSetCount"))
            assertEquals(225.0, report.get("totalVolumeKg").asDouble, 0.01)
            screenshot("report-${mode.lowercase()}")
            click("홈으로")
            click("운동 기록")
            waitText(routineName)
            screenshot("history-list-${mode.lowercase()}")
            click(routineName)
            waitText("헬스 리포트")
            compose.waitUntil(20_000) { compose.onAllNodesWithText("225", substring = true).fetchSemanticsNodes().isNotEmpty() }
            screenshot("history-${mode.lowercase()}")
        } finally {
            scenario?.close()
            tokenStore.write(previousTokens)
            appearance.edit().apply { if (previousMode == null) remove("mode") else putString("mode", previousMode) }.commit()
        }
    }

    private fun runningFlow(api: FitMateApiClient, mode: String) {
        click("운동 시작")
        click("러닝 설정")
        waitText("오늘의 목표")
        screenshot("running-setup-${mode.lowercase()}")
        compose.onNodeWithText("목표 거리 (km)").performTextReplacement("2")
        click("개발 및 테스트 도구")
        click("테스트용 예시 GPS 사용")
        click("러닝 시작")
        click("6분 진행 (+1 km, 정상 GPS)")
        click("6분 진행 (+1 km, 정상 GPS)")
        click("일시정지")
        compose.onNodeWithText("러닝 진행").performScrollTo()
        screenshot("running-active-${mode.lowercase()}")
        click("재개")
        click("종료")
        compose.onNodeWithText("저장 전 · 오늘의 기록을 확인하세요.").assertExists()
        compose.onNodeWithText("러닝 리포트").performScrollTo()
        screenshot("running-report-${mode.lowercase()}")
        click("8")
        compose.onNodeWithText("메모", substring = false).performScrollTo().performTextReplacement("UI 러닝 검증 $mode")
        screenshot("running-condition-${mode.lowercase()}")
        click("기록 저장하고 홈으로")
        waitText("운동 시작")
        val saved = api.items("running-records").single()
        val detail = ServerRecordApi(api).run(saved.text("id"))
        assertTrue(requireNotNull(detail.recordInt("distanceMeters")) >= 2000)
        assertEquals(8, detail.recordInt("rpe"))
        assertEquals("UI 러닝 검증 $mode", detail.text("memo"))
        assertEquals(2, detail.rows("laps").size)
        click("운동 기록")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("저장된 헬스 기록이 없어요.").fetchSemanticsNodes().isNotEmpty() }
        click("러닝")
        val distance = "${"%.2f".format(requireNotNull(detail.recordInt("distanceMeters")) / 1000.0)} km"
        waitText(distance)
        screenshot("running-history-${mode.lowercase()}")
        click(distance)
        waitText("러닝 리포트")
        waitText("UI 러닝 검증 $mode")
    }
}
