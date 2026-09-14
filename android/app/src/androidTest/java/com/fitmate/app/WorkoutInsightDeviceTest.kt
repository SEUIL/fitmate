package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.ui.theme.FitmateTheme
import com.fitmate.app.ui.theme.AppearanceMode
import com.fitmate.app.ui.theme.AppearancePreference
import com.fitmate.app.ui.theme.LocalAppearancePreference
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonNull
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class WorkoutInsightDeviceTest {
    @get:Rule val compose = createComposeRule()

    /** Displays a saved real AI response from the explicitly seeded synthetic demo account. */
    @Test fun showFullSyntheticLiveResult() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fullInsightDemo") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = com.google.gson.JsonParser.parseString(File(context.getExternalFilesDir(null), "insight-full-demo.json").readText()).asJsonObject
        assertTrue(saved.get("synthetic").asBoolean)
        assertEquals("AI", saved.getAsJsonObject("result").text("source"))
        MateUiTestServer { _, path, _ -> if (path.endsWith("/options")) 200 to options() else 200 to saved }.use { server ->
            val api = server.api()
            compose.setContent { CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = false) { WorkoutDesignTheme {
                    val view = LocalView.current
                    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
                    WorkoutInsightFlow(Modifier.safeDrawingPadding(), api, "synthetic") {}
                } }
            } }
            await(if (saved.getAsJsonObject("result").text("methodVersion") == "grounded-narrative-v2") "AI 종합 리포트" else "AI 해석 · 기록 근거 검증")
            capture("insight-full-demo-top")
            compose.onNodeWithText("사용한 정보와 해석의 한계").performScrollTo().performClick()
            capture("insight-full-demo-context")
            compose.onNodeWithText("Samsung Health 활동 시간과 합산하지 않습니다.", substring = true).performScrollTo().assertExists()
            capture("insight-full-demo-details")
        }
    }

    /** Opt-in real server/DB/AI test. Uses a new synthetic account and never the app's saved login. */
    @Test fun liveAiGenerationPersistsAndCanBeReopened() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveWorkoutInsight") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val api = FitMateApiClient("http://127.0.0.1:${arguments.getString("insightTestPort", "8080")}/api/v1")
        api.authenticate("auth/signup", mapOf("email" to "insight-device-${UUID.randomUUID()}@example.com", "password" to ("T!9a" + UUID.randomUUID()),
            "nickname" to "리포트검증-${UUID.randomUUID().toString().take(8)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        var session = api.request("POST", "workout-sessions", mapOf("exercises" to listOf(mapOf(
            "exerciseId" to "30000000-0000-0000-0000-000000000001", "sequenceNo" to 1, "targetSets" to 1)))).asJsonObject
        val id = session.text("id"); val path = "workout-sessions/$id"
        session = api.request("POST", "$path/start", mapOf("version" to session.recordInt("version"))).asJsonObject
        val exercise = session.rows("exercises").first()
        api.request("PUT", "$path/exercises/${exercise.text("id")}/sets/1", mapOf("version" to exercise.rows("sets").first().recordInt("version"), "weightKg" to 10, "repetitions" to 10, "completed" to true))
        session = api.request("GET", path).asJsonObject
        api.request("POST", "$path/complete", mapOf("version" to session.recordInt("version")))
        var screenKey by mutableIntStateOf(0)
        compose.setContent { FitmateTheme { WorkoutDesignTheme { key(screenKey) {
            val view = LocalView.current
            DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
            WorkoutInsightFlow(Modifier.safeDrawingPadding(), api, id) {}
        } } } }
        await("기본 해석 보기")
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        await("AI 해석 받기")
        compose.onNodeWithText("AI 해석 받기").performClick()
        compose.waitUntil(90_000) { compose.onAllNodesWithText("AI 종합 리포트").fetchSemanticsNodes().isNotEmpty() }
        val stored = api.request("GET", "$path/insights").asJsonObject
        assertEquals("READY", stored.text("status"))
        assertEquals("AI", stored.getAsJsonObject("result").text("source"))
        assertEquals(1, stored.getAsJsonObject("result").getAsJsonObject("snapshot").getAsJsonObject("report").recordInt("completedSetCount"))
        assertEquals(stored, api.request("GET", "$path/insights/${stored.text("id")}").asJsonObject)
        compose.runOnIdle { screenKey++ }
        await("AI 종합 리포트")
        compose.onNodeWithText("정보 선택 후 다시 분석").assertIsEnabled()
        capture("insight-live-ai-result")
    }

    @Test fun basicGenerationShowsEvidenceAndNeverSendsConsentByDefault() {
        var sent = false
        MateUiTestServer { method, path, body -> when {
            path.endsWith("/options") -> 200 to options()
            method == "GET" -> 200 to JsonNull.INSTANCE
            else -> { assertFalse(body.get("externalAiConsent").asBoolean); assertFalse(body.get("includeSamsungHealth").asBoolean); sent = true; 200 to result() }
        } }.use { server ->
            val api = server.api()
            compose.setContent { CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = false) { WorkoutDesignTheme { WorkoutInsightFlow(Modifier.safeDrawingPadding(), api, "synthetic") {} } }
            } }
            await("기본 해석 보기")
            compose.onAllNodes(isToggleable())[0].assertIsNotEnabled()
            compose.onNodeWithText("연결된 요약이 없어요. 운동 기록에서 먼저 연결해 주세요.").assertExists()
            compose.onNodeWithText("기본 해석 보기").performClick()
            await("기록 기반 기본 해석"); assertTrue(sent)
            compose.onNodeWithText("인바디 미사용").performScrollTo().assertExists()
            compose.onAllNodesWithContentDescription("이전").assertCountEquals(1)
            capture("insight-result-light")
        }
    }
    @Test fun selectedMeasurementDateSurvivesScreenStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val id = "40000000-0000-0000-0000-000000000001"
        MateUiTestServer { _, path, _ -> when {
            path.endsWith("/options") -> 200 to options()
            path.endsWith("inbody-records") -> 200 to mapOf("items" to listOf(mapOf("id" to id, "measuredDate" to "2026-09-01")))
            else -> 200 to JsonNull.INSTANCE
        } }.use { server ->
            val api = server.api()
            restoration.setContent { FitmateTheme { WorkoutDesignTheme { WorkoutInsightFlow(Modifier.safeDrawingPadding(), api, "synthetic") {} } } }
            await("인바디 · 사용 안 함")
            compose.onNodeWithText("인바디 · 사용 안 함").performScrollTo().performClick()
            compose.onNodeWithText("내 인바디 불러오기").performScrollTo().performClick()
            await("2026-09-01")
            compose.onAllNodes(isSelectable()).onFirst().performScrollTo().performClick()
            compose.onNodeWithText("인바디 · 2026-09-01").performScrollTo().performClick()
            restoration.emulateSavedInstanceStateRestore()
            await("인바디 · 2026-09-01")
            compose.onNodeWithText("내 인바디 불러오기").assertDoesNotExist()
        }
    }
    @Test fun generatingCanBeCanceledAndConditionsAreAvailableAgain() {
        MateUiTestServer { method, path, _ -> when {
            path.endsWith("/options") -> 200 to options()
            path.endsWith("/cancel") -> 200 to mapOf("id" to "synthetic", "status" to "CANCELED", "result" to null)
            method == "GET" && path.endsWith("/insights") -> 200 to JsonNull.INSTANCE
            else -> 200 to mapOf("id" to "synthetic", "status" to "GENERATING", "result" to null)
        } }.use { server ->
            val api = server.api()
            compose.setContent { FitmateTheme { WorkoutDesignTheme { WorkoutInsightFlow(Modifier.safeDrawingPadding(), api, "synthetic") {} } } }
            await("기본 해석 보기"); compose.onNodeWithText("기본 해석 보기").performClick()
            await("분석 취소"); compose.onNodeWithText("분석 취소").performClick()
            await("분석을 취소했어요"); compose.onNodeWithText("정보 선택 후 다시 분석").performClick()
            await("기본 해석 보기")
        }
    }
    @Test fun savedAiResultReadableInDarkLargeText() {
        MateUiTestServer { _, path, _ -> if (path.endsWith("/options")) 200 to options() else 200 to result("AI") }.use { server ->
            val api = server.api()
            compose.setContent { CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(AppearanceMode.DARK) {}) { FitmateTheme(darkTheme = true) { WorkoutDesignTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    WorkoutInsightFlow(Modifier.safeDrawingPadding(), api, "synthetic") {}
                }
            } } } }
            await("AI 해석 · 기록 근거 검증")
            compose.onNodeWithText("사용한 정보와 해석의 한계").performScrollTo().performClick()
            compose.onNodeWithText("운동 시간은 합산하지 않습니다.").performScrollTo().assertExists()
            compose.onNodeWithText("정보 선택 후 다시 분석").assertIsEnabled()
            capture("insight-result-dark-large")
        }
    }
    private fun options() = mapOf("aiAvailable" to true, "consentVersion" to "workout-insight-demo-v1", "consentNotice" to "합성 테스트용 전송 안내")
    private fun result(source: String = "RULE_BASED"): Map<String, Any> {
        val point = mapOf("id" to "recorded", "title" to "오늘의 운동을 기록했어요", "explanation" to "다음 운동의 비교 기준이 생겼어요.", "evidence" to listOf("합성 기록 · 2세트"))
        val action = point + ("title" to "체감 강도도 기록해요")
        return mapOf("id" to "synthetic", "status" to "READY", "result" to mapOf("source" to source, "summary" to point,
            "observations" to listOf(point + ("title" to "기록 비교 기준")), "nextActions" to listOf(action),
            "snapshot" to mapOf("goal" to "FITNESS", "skillLevel" to "BEGINNER", "inbody" to null, "notices" to listOf("운동 시간은 합산하지 않습니다."))))
    }
    private fun await(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
