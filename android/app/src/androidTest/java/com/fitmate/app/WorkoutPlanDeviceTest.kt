package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class WorkoutPlanDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingOptionsFailureCanRetryWithoutLosingConditions() {
        var calls = 0
        MateUiTestServer { _, path, _ ->
            if (path.endsWith("/options")) { calls++; if (calls == 1) 503 to "조건을 불러오지 못했습니다." else 200 to options() }
            else 404 to "fixture route missing"
        }.use { server ->
            show(server.api())
            await("조건 다시 불러오기")
            compose.onNodeWithText("조건 다시 불러오기").performClick()
            await("주 운동 횟수 · 1~7일")
            compose.onNodeWithText("주 운동 횟수 · 1~7일").performTextReplacement("0")
            compose.onNodeWithText("기본 추천 만들기").performClick()
            await("주 운동 횟수는 1~7일로 입력해 주세요.")
            assertEquals(2, calls)
        }
    }

    @Test fun generatingWithNullResultCanBeCanceledAndKeepsConditions() {
        var canceled = false
        MateUiTestServer { _, path, body ->
            when {
                path.endsWith("/options") -> 200 to options()
                path.endsWith("/cancel") -> { canceled = true; 200 to mapOf("status" to "CANCELED", "result" to null) }
                else -> 200 to mapOf("id" to body.text("requestId"), "status" to "GENERATING", "version" to 0, "result" to null)
            }
        }.use { server ->
            show(server.api())
            await("주 운동 횟수 · 1~7일")
            compose.onNodeWithText("주 운동 횟수 · 1~7일").performTextReplacement("2")
            compose.onNodeWithText("기본 추천 만들기").performClick()
            await("추천 구성 중")
            compose.onNodeWithText("추천 취소·조건으로 돌아가기").performClick()
            await("주 운동 횟수 · 1~7일")
            compose.onNodeWithText("주 운동 횟수 · 1~7일").assertTextContains("2")
            assertTrue(canceled)
        }
    }

    @Test fun liveLightGenerationReplacementSaveAndStart() = live(dark = false, large = false)
    @Test fun liveDarkLargeGenerationSaveAndStart() = live(dark = true, large = true)
    @Test fun preparationShowsOnlyTheServerSelectedDay() {
        val routine = mapOf("id" to "synthetic", "name" to "3일 전신 루틴", "splitCount" to 3, "nextSplitDayNo" to 2,
            "items" to (1..3).flatMap { day -> (1..4).map { number -> mapOf("splitDayNo" to day,
                "exerciseName" to "${day}일 운동 $number", "targetSets" to 2, "targetRepsMin" to 10, "targetRepsMax" to 10, "restSeconds" to 60) } })
        MateUiTestServer { _, _, _ -> 200 to routine }.use { server ->
            val api = server.api()
            var wentBack = false
            compose.setContent { FitmateTheme { WorkoutDesignTheme {
                ServerWorkoutPreparation(Modifier.safeDrawingPadding(), api, "synthetic", { wentBack = true }, {})
            } } }
            await("오늘은 2일차.")
            compose.onNodeWithText("2일 운동 1").assertExists()
            compose.onNodeWithText("2일 운동 4").assertExists()
            compose.onNodeWithText("1일 운동 1").assertDoesNotExist()
            compose.onNodeWithText("3일 운동 1").assertDoesNotExist()
            compose.onNodeWithText("2일차 운동 시작").assertIsEnabled()
            compose.onAllNodesWithContentDescription("이전").assertCountEquals(1)
            capture("plan-preparation-day-two")
            compose.onNodeWithContentDescription("이전").performClick()
            assertTrue(wentBack)
        }
    }
    @Test fun liveAiGenerationReplacementSaveAndStart() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveWorkoutPlanAi") == "true")
        live(dark = false, large = false, ai = true)
    }

    private fun live(dark: Boolean, large: Boolean, ai: Boolean = false) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveWorkoutPlan") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val api = FitMateApiClient("http://127.0.0.1:${InstrumentationRegistry.getArguments().getString("planTestPort", "18084")}/api/v1")
        api.authenticate("auth/signup", mapOf("email" to "plan-device-${UUID.randomUUID()}@example.com", "password" to ("T!9a" + UUID.randomUUID()),
            "nickname" to "추천검증-${UUID.randomUUID().toString().take(8)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        var selected: JsonObject? = null
        show(api, dark, large) { selected = it }
        await("주 운동 횟수 · 1~7일")
        capture(if (large) "plan-conditions-dark-large" else "plan-conditions-light")
        compose.onNodeWithText("주 운동 횟수 · 1~7일").performTextReplacement("2")
        compose.onNodeWithText("회당 가능한 시간 · 20~180분").performTextReplacement("30")
        if (ai) {
            compose.onNodeWithText("이번 추천의 외부 AI 전송에 동의").performScrollTo()
            compose.onAllNodes(isToggleable()).onLast().performClick()
        }
        compose.onNodeWithText(if (ai) "AI 맞춤 추천 만들기" else "기본 추천 만들기").performClick()
        await("나의 추천 결과")
        compose.onNodeWithText(if (ai) "AI 구성 · 서버 검증 완료" else "기본 추천 · 서버 검증 완료").performScrollTo().assertExists()
        capture(if (ai) "plan-result-live-ai" else if (large) "plan-result-dark-large" else "plan-result-light")
        if (!large) {
            val replaceButton = compose.onAllNodes(hasText("교체", substring = true) and hasClickAction()).onFirst()
            replaceButton.performScrollTo().performClick()
            await("운동 교체")
            compose.onAllNodes(hasText("교체 후", substring = true) and hasClickAction()).onFirst().performScrollTo().performClick()
            compose.onNodeWithText("교체하고 다시 검증").performClick()
            await("나의 추천 결과")
        }
        compose.onNodeWithText("내 루틴에 저장").performClick()
        await("저장한 루틴으로 운동")
        compose.onNodeWithText("저장한 루틴으로 운동").performClick()
        compose.waitUntil(15_000) { selected != null }
        val routine = requireNotNull(selected)
        assertTrue(routine.get("recommendation").isJsonObject)
        val reloaded = api.request("GET", "routines/${routine.text("id")}").asJsonObject
        assertEquals(routine.get("recommendation"), reloaded.get("recommendation"))
        assertEquals(if (ai) "AI" else "RULE_BASED", reloaded.getAsJsonObject("recommendation").text("source"))
        val session = api.request("POST", "workout-sessions", mapOf("routineId" to routine.text("id"))).asJsonObject
        val today = reloaded.rows("items").filter { it.get("splitDayNo").asInt == session.get("routineSplitDayNo").asInt }
        val appSession = serverWorkoutSession(session, false)
        assertEquals(today.map { it.text("exerciseName") }, appSession.sessionExercises.toList())
        assertEquals(today.first().text("exerciseName"), appSession.exerciseName)
        assertTrue(appSession.sessionExercises.size < reloaded.rows("items").size)
        val started = api.request("POST", "workout-sessions/${session.text("id")}/start", mapOf("version" to session.get("version").asLong)).asJsonObject
        assertEquals("IN_PROGRESS", started.text("status"))
        assertEquals(30, reloaded.getAsJsonObject("recommendation").getAsJsonObject("conditions").get("sessionMinutes").asInt)
    }
    private fun show(api: FitMateApiClient, dark: Boolean = false, large: Boolean = false, selected: (JsonObject) -> Unit = {}) {
        val testScreenKey = UUID.randomUUID().toString()
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val previous = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = previous } }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f),
                com.fitmate.app.ui.theme.LocalAppearancePreference provides com.fitmate.app.ui.theme.AppearancePreference(
                    if (dark) com.fitmate.app.ui.theme.AppearanceMode.DARK else com.fitmate.app.ui.theme.AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = dark) { WorkoutDesignTheme {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) { key(testScreenKey) { WorkoutPlanFlow(Modifier, api, {}, selected) } }
                } }
            }
        }
    }
    private fun options() = mapOf("goal" to "FITNESS", "skillLevel" to "BEGINNER", "exercises" to emptyList<Any>(),
        "aiAvailable" to false, "consentVersion" to "workout-plan-demo-v1", "consentNotice" to "개발 검증용 안내")
    private fun await(text: String) { compose.waitUntil(45_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
