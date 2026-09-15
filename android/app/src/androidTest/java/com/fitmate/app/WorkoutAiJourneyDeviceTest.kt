package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Opt-in, synthetic private account. Real production screen components and API adapters, no saved login changes. */
class WorkoutAiJourneyDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recommendationThroughWorkoutCompletionAndIntegratedReport() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAiJourney") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val api = FitMateApiClient("http://127.0.0.1:8080/api/v1")
        api.authenticate("auth/signup", mapOf("email" to "journey-${UUID.randomUUID()}@example.com", "password" to ("T!9a" + UUID.randomUUID()),
            "nickname" to "통합검증-${UUID.randomUUID().toString().take(8)}", "visibility" to "PRIVATE",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "ONE_TO_THREE_YEARS")),
            "goals" to listOf(mapOf("goalCode" to "MUSCLE_GAIN", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        var page by mutableStateOf("plan")
        var routine: JsonObject? = null
        var session: WorkoutSession? = null
        var completedId: String? = null
        var actionError: Throwable? = null
        compose.setContent { CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(AppearanceMode.LIGHT) {}) {
            FitmateTheme(darkTheme = false) { WorkoutDesignTheme {
                val scope = rememberCoroutineScope()
                val view = LocalView.current
                DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
                when (page) {
                    "plan" -> WorkoutPlanFlow(Modifier.safeDrawingPadding(), api, {}, { routine = it; page = "prepare" })
                    "prepare" -> ServerWorkoutPreparation(Modifier.safeDrawingPadding(), api, requireNotNull(routine).text("id"), {}, { samsung ->
                        scope.launch { try {
                            val response = withContext(Dispatchers.IO) { api.request("POST", "workout-sessions", mapOf("routineId" to requireNotNull(routine).text("id"))).asJsonObject }
                            session = serverWorkoutSession(response, samsung); page = "workout"
                        } catch (failure: Exception) { actionError = failure } }
                    })
                    "workout" -> {
                        val current = requireNotNull(session)
                        val server = remember(current) { ServerWorkout(api, current) }
                        WorkoutScreen(Modifier.safeDrawingPadding(), current, emptyList(), emptyList(), server,
                            completeServer = { summary -> withContext(Dispatchers.IO) { server.complete(WorkoutUpload(summary.sets, current.isTimerRunning)) } },
                            saveRoutine = { false }, onCancel = {}, onBack = {}, finish = {
                                completedId = JsonParser.parseString(current.serverState).asJsonObject.text("id"); page = "report"
                            })
                    }
                    "report" -> Screen(Modifier.safeDrawingPadding()) { ServerRecordDetail(api, requireNotNull(completedId)) }
                }
            } }
        } }
        await("주 운동 횟수 · 1~7일")
        compose.onNodeWithText("주 운동 횟수 · 1~7일").performTextReplacement("4")
        compose.onNodeWithText("회당 가능한 시간 · 20~180분").performTextReplacement("30")
        compose.onNodeWithText("이번 추천의 외부 AI 전송에 동의").performScrollTo()
        compose.onAllNodes(isToggleable()).onLast().performClick()
        compose.onNodeWithText("AI 맞춤 추천 만들기").performClick()
        await("나의 추천 결과")
        compose.onNodeWithText("AI 구성 · 서버 검증 완료").performScrollTo().assertExists()
        compose.onAllNodes(hasText("교체", substring = true) and hasClickAction()).onFirst().performScrollTo().performClick()
        await("운동 교체")
        compose.onAllNodes(hasText("교체 후", substring = true) and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("교체하고 다시 검증").performClick()
        await("나의 추천 결과")
        compose.onNodeWithText("내 루틴에 저장").performClick()
        await("저장한 루틴으로 운동")
        compose.onNodeWithText("저장한 루틴으로 운동").performClick()
        await("오늘은 1일차.")
        compose.onNodeWithText("1일차 운동 시작").performClick()
        await("이 세트 기록")
        assertNull(actionError)
        val current = requireNotNull(session)
        val expected = requireNotNull(routine).rows("items").filter { it.recordInt("splitDayNo") == 1 }
        assertEquals(expected.map { it.text("exerciseName") }, current.sessionExercises.toList())
        assertTrue(current.sessionExercises.size < requireNotNull(routine).rows("items").size)
        for ((index, item) in expected.withIndex()) {
            if (index > 0) {
                compose.onNodeWithText("전체 운동 보기").performScrollTo().performClick()
                compose.onNode(hasText(item.text("exerciseName")) and hasClickAction()).performScrollTo().performClick()
                compose.onNodeWithText("‹ 운동 진행으로 돌아가기").performScrollTo().performClick()
            }
            compose.onNodeWithText("중량 (kg)").performScrollTo().performTextReplacement("10")
            compose.onNodeWithText("횟수").performTextReplacement("10")
            compose.onNodeWithText("RPE").performTextReplacement("7")
            repeat(item.recordInt("targetSets") ?: 1) { compose.onNodeWithText("이 세트 기록").performClick() }
        }
        compose.onNodeWithText("운동 종료").performClick()
        await("운동 리포트 해석 보기")
        val id = requireNotNull(completedId)
        val storedSession = api.request("GET", "workout-sessions/$id").asJsonObject
        assertEquals("COMPLETED", storedSession.text("status"))
        assertEquals(expected.sumOf { it.recordInt("targetSets") ?: 1 }, storedSession.rows("exercises").sumOf { e -> e.rows("sets").count { it.get("completed").asBoolean } })
        compose.onNodeWithText("운동 리포트 해석 보기").performScrollTo().performClick()
        await("기본 해석 보기")
        compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()
        compose.onNodeWithText("AI 해석 받기").performClick()
        compose.waitUntil(90_000) { compose.onAllNodesWithText("AI 종합 리포트").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("기록 기반 기본 해석").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("기록 기반 기본 해석").fetchSemanticsNodes().isNotEmpty()) {
            val first = api.request("GET", "workout-sessions/$id/insights").asJsonObject
            assertEquals("RULE_BASED", first.getAsJsonObject("result").text("source"))
            compose.onNodeWithText("정보 선택 후 다시 분석").performClick()
            await("기본 해석 보기")
            compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()
            compose.onNodeWithText("AI 해석 받기").performClick()
            await("AI 종합 리포트")
            assertNotEquals(first.text("id"), api.request("GET", "workout-sessions/$id/insights").asJsonObject.text("id"))
        }
        await("AI 종합 리포트")
        val saved = api.request("GET", "workout-sessions/$id/insights").asJsonObject
        assertEquals("AI", saved.getAsJsonObject("result").text("source"))
        assertEquals("grounded-narrative-v2", saved.getAsJsonObject("result").text("methodVersion"))
        assertEquals(saved, api.request("GET", "workout-sessions/$id/insights/${saved.text("id")}").asJsonObject)
        val reloaded = api.request("GET", "routines/${requireNotNull(routine).text("id")}").asJsonObject
        assertEquals(2, reloaded.recordInt("nextSplitDayNo"))
        assertEquals(requireNotNull(routine).get("recommendation"), reloaded.get("recommendation"))
    }
    private fun await(text: String) { compose.waitUntil(90_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
}
