package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalWorkoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun content(state: RunningState, large: Boolean = false, home: (Boolean) -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalAppearancePreference provides AppearancePreference(AppearanceMode.DARK) {},
                LocalDensity provides Density(density.density, if (large) 2f else 1f)
            ) {
                FitmateTheme { WorkoutDesignTheme {
                    Surface(Modifier.widthIn(max = if (large) 320.dp else 600.dp).fillMaxHeight().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                        RunningFlow(Modifier, state, false, {}, remember { FitMateApiClient("http://127.0.0.1:1") }, home)
                    }
                } }
            }
        }
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text, substring = false)
        if (text !in listOf("일시정지", "재개", "종료")) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }

    @Test fun goalsKeepTheirInputsAndInvalidDistanceCannotStart() {
        val state = RunningState()
        content(state)
        click("시간")
        click("60분")
        compose.runOnIdle { assertEquals("60", state.targetMinutes) }
        click("페이스")
        click("6:00/km")
        compose.runOnIdle { assertEquals("360", state.targetPace) }
        click("인터벌")
        click("3분")
        compose.runOnIdle { assertEquals("3", state.intervalMinutes) }
        click("자유")
        compose.onNodeWithText("목표 거리 (km)").assertDoesNotExist()
        click("거리")
        compose.onNodeWithText("목표 거리 (km)").performTextReplacement("0")
        compose.runOnIdle { assertEquals("0", state.targetDistance) }
        click("러닝 시작")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("목표 거리는 0.1~100 km로 입력하세요.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("목표 거리는 0.1~100 km로 입력하세요.").assertExists()
        compose.runOnIdle { assertEquals("설정", state.phase) }
        compose.onNodeWithText("나만 보기").assertDoesNotExist()
        click("경로 기록")
        compose.onNodeWithText("나만 보기").assertExists()
        click("요약만 공유")
        compose.runOnIdle { assertEquals("요약만 공유", state.visibility) }
    }

    @Test fun gpsLossPauseAndFinishKeepMeasuredValues() {
        val state = RunningState().apply { phase = "진행"; demo = true; signal = "끊김"; seconds = 720; meters = 2000; laps = listOf(RunLap(1, 1000, 360), RunLap(2, 1000, 360)) }
        content(state)
        compose.onNodeWithText("현재 페이스").assertExists()
        compose.onNodeWithText("—", substring = false).assertExists()
        click("일시정지")
        compose.runOnIdle { assertTrue(state.paused); assertEquals(2000, state.meters) }
        click("재개")
        compose.runOnIdle { assertFalse(state.paused) }
        click("종료")
        compose.onNodeWithText("저장 전 · 오늘의 기록을 확인하세요.").assertExists()
        compose.runOnIdle { assertEquals("리포트", state.phase); assertEquals(2, state.laps.size); assertNull(state.pendingUpload) }
    }

    @Test fun failedSaveRetriesSamePayloadAndKeepsReport() {
        val state = RunningState().apply { phase = "리포트"; seconds = 361; meters = 1050; laps = listOf(RunLap(1, 1000, 340)); memo = "테스트 메모" }
        var left = false
        content(state, home = { left = true })
        compose.onNodeWithText("마지막 구간 · 50m", substring = true).assertExists()
        click("10")
        click("기록 저장하고 홈으로")
        compose.waitUntil(5_000) { state.error != null }
        val first = state.pendingUpload
        assertNotNull(first)
        val body = JsonParser.parseString(first).asJsonObject
        assertEquals(10, body.recordInt("rpe"))
        assertEquals("테스트 메모", body.text("memo"))
        assertTrue(body.text("clientRecordId").isNotBlank())
        compose.onNodeWithText("메모", substring = false).assertDoesNotExist()
        click("기록 저장하고 홈으로")
        compose.waitUntil(5_000) { state.error != null }
        compose.runOnIdle { assertEquals(first, state.pendingUpload); assertEquals("리포트", state.phase); assertFalse(left) }
    }

    @Test fun largeTextAllowsLastRpeAndZeroDistanceReport() {
        val state = RunningState().apply { phase = "리포트"; seconds = 60; meters = 0; goal = "자유" }
        content(state, large = true)
        click("10")
        compose.onNodeWithText("10", substring = false).assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { assertEquals("10", state.rpe) }
        compose.onNodeWithText("거리 기록이 없어요. 시간과 메모만 저장할 수 있습니다.").assertExists()
        compose.onNodeWithText("기록 저장하고 홈으로").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
}
