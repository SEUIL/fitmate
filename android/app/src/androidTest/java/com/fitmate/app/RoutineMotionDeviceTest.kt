package com.fitmate.app.ui

import android.app.Activity
import android.media.MediaMetadataRetriever
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import android.graphics.Bitmap
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.fitmate.app.data.remote.ApiExercise
import com.fitmate.app.ui.theme.FitmateTheme
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RoutineMotionDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun content(body: @Composable () -> Unit) {
        compose.setContent { FitmateTheme(darkTheme = false) { WorkoutDesignTheme(journey = true) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) { body() }
        } } }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "routine-redesign").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun routinesStartCollapsedAndCollapsePreservesChoice() {
        val row = JsonParser.parseString("""{"id":"routine-1","name":"테스트 루틴","template":true,"items":[{"exerciseId":"unknown","exerciseName":"테스트 운동","targetSets":3,"sequenceNo":1}]}""").asJsonObject
        MateUiTestServer { _, path, _ ->
            when {
                path.startsWith("/routines/recommended") -> 200 to row
                path.startsWith("/exercises?") -> 200 to mapOf("items" to emptyList<Any>())
                else -> 404 to "unexpected request"
            }
        }.use { server ->
            val api = server.api()
            content { ServerRoutineList(Modifier.fillMaxSize(), api, true, {}, {}, {}) }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("테스트 루틴 펼치기").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("모두 펼치기").assertDoesNotExist()
            compose.onNodeWithText("테스트 운동").assertDoesNotExist()
            screenshot("routines-collapsed")
            compose.onNodeWithText("진행할 루틴을 선택해주세요").assertIsNotEnabled()
            compose.onNodeWithContentDescription("테스트 루틴 선택").performClick()
            compose.onNodeWithText("이 루틴으로 운동").assertIsEnabled()
            compose.onNodeWithContentDescription("테스트 루틴 펼치기").performClick()
            compose.onNodeWithText("테스트 운동").assertExists()
            compose.onNodeWithContentDescription("테스트 루틴 접기").performScrollTo().performClick()
            compose.onNodeWithText("테스트 운동").assertDoesNotExist()
            compose.onNodeWithContentDescription("테스트 루틴 선택").assertIsSelected()
            compose.onNodeWithText("이 루틴으로 운동").assertIsEnabled()
        }
    }

    @Test fun bulkExpansionHandlesPartialSelectionAndResetsAcrossTabs() {
        val name = "꾸준히 이어가는 월요일 전신 근력 운동 루틴"
        val rows = listOf(name, "두 번째 루틴").mapIndexed { i, title ->
            JsonParser.parseString("""{"id":"routine-$i","name":"$title","template":true,"items":[{"exerciseId":"exercise-$i","exerciseName":"운동 $i","targetSets":3,"sequenceNo":1}]}""").asJsonObject
        }
        MateUiTestServer { _, path, _ ->
            when {
                path.startsWith("/routines/recommended") -> 200 to rows.first()
                path.startsWith("/routines/templates") || path == "/routines" -> 200 to mapOf("items" to rows)
                path.startsWith("/exercises?") -> 200 to mapOf("items" to emptyList<Any>())
                else -> 404 to "unexpected request"
            }
        }.use { server ->
            val api = server.api()
            content { ServerRoutineList(Modifier.fillMaxSize(), api, true, {}, {}, {}) }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("$name 펼치기").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("기본 루틴", substring = false).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("기본 루틴 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("모두 펼치기").assertIsDisplayed()
            screenshot("routine-long-title-unclipped")
            compose.onNodeWithContentDescription("$name 선택").performClick()
            compose.onNodeWithContentDescription("$name 펼치기").performClick()
            compose.onNodeWithText("모두 펼치기").performScrollTo().performClick()
            compose.onNodeWithText("운동 0").assertExists()
            compose.onNodeWithText("운동 1").assertExists()
            compose.onNodeWithText("모두 접기").performScrollTo().performClick()
            compose.onNodeWithText("운동 0").assertDoesNotExist()
            compose.onNodeWithText("운동 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("$name 선택").assertIsSelected()
            compose.onNodeWithText("내 루틴", substring = false).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("내 루틴 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("운동 0").assertDoesNotExist()
            compose.onNodeWithText("모두 펼치기").assertIsDisplayed()
        }
    }

    @Test fun homeCountsAllServerPagesWithHealthFilter() {
        MateUiTestServer { _, path, _ ->
            when {
                path.startsWith("/workout-sessions?status=COMPLETED&sportCode=HEALTH") -> {
                    val first = path.contains("cursor=0")
                    200 to mapOf("items" to List(if (first) 2 else 1) { mapOf("id" to "$first-$it") },
                        "hasNext" to first, "nextCursor" to if (first) "1" else null)
                }
                path.startsWith("/running-records?") -> 200 to mapOf("items" to emptyList<Any>(), "hasNext" to false)
                else -> 400 to "Unexpected query"
            }
        }.use { server ->
            val api = server.api()
            content { HomeWorkoutCounts(api) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("3회").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("누적 운동 기록").assertIsDisplayed()
            compose.onNodeWithText("0회").assertIsDisplayed()
        }
    }

    @Test fun homeCountFailureIsNotZeroAndRetryRecovers() {
        val failing = java.util.concurrent.atomic.AtomicBoolean(true)
        MateUiTestServer { _, path, _ ->
            if (failing.get()) 503 to "Temporary failure"
            else 200 to mapOf("items" to List(if (path.startsWith("/running-records")) 1 else 2) { mapOf("id" to "$it") }, "hasNext" to false)
        }.use { server ->
            val api = server.api()
            content { HomeWorkoutCounts(api) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("횟수 다시 불러오기").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("0회").assertDoesNotExist()
            failing.set(false)
            compose.onNodeWithText("횟수 다시 불러오기").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("2회").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1회").assertIsDisplayed()
        }
    }

    @Test fun editorSearchSelectRemoveAndSaveValidation() {
        val known = exerciseMotions.first()
        val items = listOf(ApiExercise(known.id, "HEALTH", known.name, "LEGS", "BARBELL", null),
            ApiExercise("unknown", "HEALTH", "케틀벨 스윙", "FULL", "KETTLEBELL", null))
        var saved: List<String>? = null
        content {
            var name by remember { mutableStateOf("") }
            var selected by remember { mutableStateOf(emptyList<String>()) }
            RoutineEditorSheet(false, name, { name = it }, items, selected, { selected = it }, emptyMap(),
                false, null, {}, {}, { saved = selected })
        }
        compose.onNodeWithText("운동을 선택해주세요").assertIsNotEnabled()
        compose.onNodeWithText("루틴 이름").performTextInput("실기기 루틴")
        compose.onNodeWithText("운동 이름 검색").performScrollTo().performTextInput("케틀벨")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("routine-exercise-list").performScrollToNode(hasContentDescription("케틀벨 스윙 추가"))
        compose.onNodeWithContentDescription("케틀벨 스윙 추가").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        screenshot("routine-editor")
        compose.onNodeWithText("1개 운동으로 루틴 만들기").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("unknown"), saved) }
        compose.onNodeWithTag("routine-exercise-list").performScrollToNode(hasContentDescription("케틀벨 스윙 선택 해제"))
        compose.onNodeWithContentDescription("케틀벨 스윙 선택 해제").performClick()
        compose.onNodeWithText("운동을 선택해주세요").assertIsNotEnabled()
    }

    @Test fun unknownExerciseHasIntroductionWithoutWrongAnimation() {
        content { Column { ExerciseIntroduction("unknown", "케틀벨 스윙", "FULL", "KETTLEBELL") } }
        compose.onNodeWithText("동작 애니메이션 준비 중").assertIsDisplayed()
        compose.onNodeWithText("어떤 운동인가요?").assertIsDisplayed()
        compose.onNodeWithText("동작 일시정지").assertDoesNotExist()
        screenshot("unsupported-exercise")
        assertNull(exerciseMotion("unknown", exerciseMotions.first().name))
    }

    @Test fun repeatedExerciseInExistingRoutineCanRemoveOneOccurrence() {
        val known = exerciseMotions.first()
        val item = ApiExercise(known.id, "HEALTH", known.name, "LEGS", "BARBELL", null)
        var saved: List<String>? = null
        content {
            var selected by remember { mutableStateOf(listOf(known.id, known.id)) }
            RoutineEditorSheet(true, "분할 루틴", {}, listOf(item), selected, { selected = it }, emptyMap(),
                false, null, {}, {}, { saved = selected })
        }
        compose.onNodeWithText("선택한 운동 2개").assertExists()
        compose.onNodeWithContentDescription("선택한 운동 1 제거").performClick()
        compose.onNodeWithText("1개 운동으로 루틴 저장").performClick()
        compose.runOnIdle { assertEquals(listOf(known.id), saved) }
    }

    private fun video(view: View): VideoView? = if (view is VideoView) view else
        (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { video(group.getChildAt(it)) } }

    private fun playing(): Boolean {
        var result = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<Activity>().any { video(it.window.decorView)?.isPlaying == true }
        }
        return result
    }

    @Test fun animationActuallyPlaysAndPausesOnDevice() {
        val motion = exerciseMotions.first()
        content { ExerciseAnimation(motion.id, motion.name) }
        compose.waitUntil(15_000) { playing() }
        screenshot("animation-playing")
        compose.onNodeWithText("동작 일시정지").performClick()
        compose.waitUntil(5_000) { !playing() }
        compose.onNodeWithText("동작 재생").performClick()
        compose.waitUntil(5_000) { playing() }
    }

    @Test fun all24PackagedVideosHaveReadableMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(24, exerciseMotions.size)
        assertEquals(24, exerciseMotions.map { it.id }.distinct().size)
        exerciseMotions.forEach { motion ->
            context.resources.openRawResourceFd(motion.video).use { fd ->
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    assertEquals(motion.name, "448", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                    assertTrue(motion.name, reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() > 3000)
                } finally { reader.release() }
            }
        }
    }
}
