package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.ApiExercise
import com.fitmate.app.ui.theme.*
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutRedesignDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun content(dark: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = dark) { WorkoutDesignTheme(journey = true) {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) { body() }
                } }
            }
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-redesign").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test fun lightHomeKeepsStartAndPersonalHistoryDestinations() {
        var starts = 0
        var records = 0
        var communities = 0
        val social = SocialState(emptyList(), emptyList())
        content {
            SocialHome(Modifier, UserProfile("테스트", "", emptySet()), social, null,
                start = { starts++ }, mates = {}, community = { communities++ }, dm = {}, inbody = {}, history = { records++ })
        }
        screenshot("home-light")
        compose.onNodeWithText("운동 시작").assertIsDisplayed().performClick()
        compose.onNodeWithText("운동 기록").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, records); assertEquals(0, communities) }
        compose.onNodeWithText("더보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, communities) }
    }

    @Test fun darkHomeResumesWithoutAddingAStartAction() {
        var resumed = false
        content(dark = true) {
            SocialHome(Modifier, UserProfile("테스트", "", emptySet()), remember { SocialState(emptyList(), emptyList()) }, null,
                start = { resumed = true }, mates = {}, community = {}, dm = {}, inbody = {}, resuming = true,
                resumeDescription = "스쿼트 · 기록 1세트")
        }
        compose.onNodeWithText("운동 시작").assertDoesNotExist()
        screenshot("home-dark")
        compose.onNodeWithText("운동 이어하기").performClick()
        compose.runOnIdle { assertTrue(resumed) }
    }

    @Test fun routineRowsShowOnlyActualTargetsAndOpenTheCorrectExercise() {
        val row = JsonParser.parseString("""{"name":"전신 기본 루틴","items":[
            {"exerciseId":"squat","exerciseName":"스쿼트","targetSets":3,"targetWeightKg":20,"targetRepsMin":8,"targetRepsMax":12},
            {"exerciseId":"plank","exerciseName":"플랭크","targetSets":3}] }""").asJsonObject
        var selected = ""
        content(dark = true) { Screen { WorkoutCard { RoutineExerciseList(row, "추천 루틴", true) { selected = it } } } }
        compose.onNodeWithText("3세트 · 20.0 kg · 8–12회").assertIsDisplayed()
        compose.onNodeWithText("3세트", substring = false).assertIsDisplayed()
        screenshot("routine-dark")
        compose.onNodeWithText("플랭크").performClick()
        compose.runOnIdle { assertEquals("plank", selected) }
    }

    @Test fun routineFailureDoesNotMasqueradeAsAnEmptyLibrary() {
        content { ServerRoutineLibrary(Modifier, FitMateApiClient("http://127.0.0.1:1"), true, {}, {}) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("다시 불러오기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("아직 만든 루틴이 없어요").assertDoesNotExist()
        compose.onNodeWithText("로그인이 필요합니다.").assertExists()
    }

    private fun session() = WorkoutSession("스쿼트", false).apply {
        sessionExercises.addAll(listOf("벤치 프레스", "랫 풀다운", "플랭크"))
        elapsedSeconds = 872; isTimerRunning = false
        weightText = "20"; repetitionText = "10"; rpeText = "7"; restPresetSeconds = 60
        recordedSets.add(WorkoutSet("스쿼트", 20.0, 10, 7))
    }

    @Test fun lightWorkoutValidatesThenEditsTheExistingSetWithUnitConversion() {
        val session = session()
        content { WorkoutScreen(Modifier, session, emptyList(), emptyList(), null, {}, { true }, {}, {}, {}) }
        screenshot("workout-light")
        compose.onNodeWithText("중량 (kg)").performClick().performTextReplacement("-1")
        compose.waitForIdle()
        screenshot("workout-keyboard")
        compose.onNodeWithText("이 세트 기록").assertIsDisplayed()
        compose.onNodeWithText("이 세트 기록").performClick()
        compose.runOnIdle { assertEquals(1, session.recordedSets.size); assertNotNull(session.error) }
        compose.onNodeWithText("수정", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("중량 (kg)").performScrollTo().performTextReplacement("22.5")
        compose.onNodeWithText("lb", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("세트 수정", substring = false).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, session.recordedSets.size)
            assertEquals(22.5, session.recordedSets.single().weightKg, 0.01)
            assertEquals(-1, session.editingSetIndex)
            assertTrue(session.isRestTimerRunning)
            session.isRestTimerRunning = false
        }
    }

    @Test fun darkWorkoutFailedCompletionKeepsSetsAndRetryFinishesOnce() {
        val session = session()
        var attempts = 0
        var finished = 0
        content(dark = true) { WorkoutScreen(Modifier, session, emptyList(), emptyList(), null,
            { attempts++; if (attempts == 1) error("테스트 저장 오류") }, { true }, {}, {}, { finished++ }) }
        screenshot("workout-dark")
        compose.onNodeWithText("운동 종료", substring = false).performClick()
        compose.onNodeWithText("여기까지 기록할까요?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, attempts) }
        screenshot("finish-confirmation-dark")
        compose.onNodeWithText("기록 저장하고 종료").performClick()
        compose.onNode(hasText("테스트 저장 오류") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, finished); assertEquals(1, session.recordedSets.size) }
        compose.onNodeWithText("다시 저장").performClick()
        compose.runOnIdle { assertEquals(2, attempts); assertEquals(1, finished); assertEquals(1, session.recordedSets.size) }
    }

    @Test fun largeTextInSmallViewportKeepsInputsAndRecordActionReachable() {
        val session = session()
        content {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp).height(600.dp)) {
                    WorkoutScreen(Modifier, session, emptyList(), emptyList(), null, {}, { true }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("체감 강도 · 메모").performScrollTo().performClick()
        compose.onNodeWithText("RPE", substring = false).performScrollTo().assertIsDisplayed().performTextReplacement("8")
        compose.onNodeWithText("이 세트 기록").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, session.recordedSets.size); assertEquals(8, session.recordedSets.last().rpe); session.isRestTimerRunning = false }
        screenshot("workout-large-text")
    }

    @Test fun excludedContentKeepsInheritedTheme() {
        var checked = false
        compose.setContent { FitmateTheme(darkTheme = true) {
            val original = MaterialTheme.colorScheme
            WorkoutDesignTheme(enabled = false) {
                val inherited = MaterialTheme.colorScheme
                SideEffect { assertSame(original, inherited); checked = true }
            }
        } }
        compose.runOnIdle { assertTrue(checked) }
    }

    @Test fun setCompletionShowsRestAndEditKeepsTheOriginalSet() {
        val session = session().apply { isTimerRunning = true }
        content { WorkoutScreen(Modifier, session, emptyList(), emptyList(), null, {}, { true }, {}, {}, {}) }
        compose.onNodeWithText("이 세트 기록").performClick()
        compose.onNodeWithText("잠깐, 숨을 고르세요.").assertIsDisplayed()
        screenshot("rest-light")
        compose.onNodeWithText("방금 기록 수정").performScrollTo().performClick()
        compose.onNodeWithText("중량 (kg)").performScrollTo().performTextReplacement("25")
        compose.onNodeWithText("세트 수정").performClick()
        compose.runOnIdle { assertEquals(2, session.recordedSets.size); assertEquals(25.0, session.recordedSets.last().weightKg, .01) }
        compose.onNodeWithText("휴식 건너뛰기").performClick()
        compose.onNodeWithText("운동 종료", substring = false).performClick()
        compose.onNodeWithText("운동 계속하기").performClick()
        compose.onNodeWithText("이 세트 기록").assertIsDisplayed()
    }

    @Test fun animatedWorkoutCanRecordRestReviewAndFinish() {
        val motion = exerciseMotions.first()
        val session = WorkoutSession(motion.name, false)
        val exercise = ApiExercise(motion.id, "HEALTH", motion.name, "LEGS", "BARBELL", null)
        var saves = 0
        content {
            var result by remember { mutableStateOf<WorkoutSummary?>(null) }
            if (result != null) WorkoutJourneyPage { WorkoutSavedHeader(requireNotNull(result)) }
            else WorkoutScreen(Modifier, session, emptyList(), listOf(exercise), null,
                { saves++ }, { true }, {}, {}, { result = it })
        }
        compose.onNodeWithText("동작 일시정지").assertExists()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-redesign").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "animated-active-light.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithText("이 세트 기록").performClick()
        compose.onNodeWithText("잠깐, 숨을 고르세요.").assertIsDisplayed()
        compose.onNodeWithText("휴식 건너뛰기").performClick()
        compose.onNodeWithText("운동 종료", substring = false).performClick()
        screenshot("finish-light")
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText("기록 저장하고 종료").performClick()
        compose.onNodeWithText("저장 완료", substring = false).assertIsDisplayed()
        screenshot("saved-light")
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun sportSelectionOffersEqualEntryPoints() {
        var choice = ""
        content { WorkoutJourneyPage {
            PlanHeading("오늘의 운동", "어떤 운동을 할까요?")
            WorkoutSportChoice(false, "헬스", "내 페이스로, 한 세트씩.", "루틴 선택") { choice = "health" }
            WorkoutSportChoice(true, "러닝", "거리와 시간을 나만의 목표로.", "목표 설정") { choice = "running" }
        } }
        screenshot("sport-selection-light")
        compose.onNodeWithText("루틴 선택").performClick()
        compose.runOnIdle { assertEquals("health", choice) }
        compose.onNodeWithText("목표 설정").performClick()
        compose.runOnIdle { assertEquals("running", choice) }
    }
}
