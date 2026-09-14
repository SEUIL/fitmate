package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import com.fitmate.app.SamsungHealthNavigationHost
import com.fitmate.app.samsunghealth.*
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SamsungHealthDesignDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionToolbarBackPreservesSettingsWithoutRestartingApp() {
        verifyConnectionReturn(systemBack = false)
    }

    @Test fun connectionSystemBackPreservesSettingsWithoutRestartingApp() {
        verifyConnectionReturn(systemBack = true)
    }

    private fun verifyConnectionReturn(systemBack: Boolean) {
        var starts = 0
        var disposals = 0
        compose.setContent { FitmateTheme {
            var open by remember { mutableStateOf(false) }
            SamsungHealthNavigationHost(open, { open = false }, appContent = {
                DisposableEffect(Unit) {
                    starts++
                    onDispose { disposals++ }
                }
                var selection by remember { mutableStateOf("설정") }
                Column(Modifier.safeDrawingPadding()) {
                    Text(selection)
                    Button(onClick = { selection = "설정 상태 유지"; open = true }) { Text("삼성헬스 열기") }
                }
            }) {
                SamsungHealthConnectionScreen(SamsungHealthUiState(status = SamsungHealthStatus.READY),
                    {}, {}, {}, { open = false })
            }
        } }
        repeat(2) {
            compose.onNodeWithText("삼성헬스 열기").performClick()
            compose.onNodeWithText("권한 필요").assertExists()
            if (systemBack) pressBack()
            else compose.onNodeWithContentDescription("뒤로가기").performClick()
            compose.onNodeWithText("권한 필요").assertDoesNotExist()
            compose.onNodeWithText("설정 상태 유지").assertExists()
            compose.runOnIdle { assertEquals(1, starts); assertEquals(0, disposals) }
        }
    }

    @Test fun connectionShowsPermissionAndCallbacksWithoutRequestingBroadHealthAccess() {
        var permissions = 0
        var refreshes = 0
        compose.setContent { FitmateTheme {
            SamsungHealthConnectionScreen(SamsungHealthUiState(status = SamsungHealthStatus.READY),
                { refreshes++ }, { permissions++ }, {}, {})
        } }
        compose.onNodeWithText("권한 필요").assertExists()
        compose.onNodeWithText("운동 읽기 권한 관리").performScrollTo().performClick()
        compose.onNodeWithText("상태 새로고침").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, permissions); assertEquals(1, refreshes) }
    }

    @Test fun preparationIsOptionalAndShowsManualDeviceStartOnlyWhenEnabled() {
        compose.setContent { FitmateTheme { WorkoutDesignTheme {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                var selected by remember { mutableStateOf(false) }
                SamsungPreparationOption(selected) { selected = it }
            }
        } } }
        compose.onNodeWithText("기기에서 먼저 시작해 주세요").assertDoesNotExist()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("기기에서 먼저 시작해 주세요").assertExists()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("기록 연결 없이도 기본 리포트를 볼 수 있어요.").assertExists()
    }

    @Test fun completionAllowsBasicReportWithoutLoadingHealthRecords() {
        var loads = 0
        var skips = 0
        compose.setContent { FitmateTheme { WorkoutDesignTheme {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                SamsungEndGuidance({ loads++ }, { skips++ })
            }
        } } }
        compose.onNodeWithText("기기에서 운동 종료").assertExists()
        compose.onNodeWithText("기본 리포트 먼저 보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, loads); assertEquals(1, skips) }
        compose.onNodeWithText("운동 기록 불러오기").performClick()
        compose.runOnIdle { assertEquals(1, loads) }
    }

    @Test fun storedSummaryShowsRealValuesAndKeepsMissingMetricsMissing() {
        val summary = ExternalWorkoutSummary(sourceRecordId = "fixture", sourceAppId = null, sourceModifiedAt = null,
            exerciseType = "BENCH_PRESS", startedAt = Instant.parse("2026-09-14T09:31:00Z"), endedAt = Instant.parse("2026-09-14T09:54:00Z"),
            activeDurationSeconds = 1320, distanceMeters = null, caloriesKcal = BigDecimal("128.00"), meanHeartRate = BigDecimal("103.00"),
            minHeartRate = null, maxHeartRate = null)
        compose.setContent { FitmateTheme { WorkoutDesignTheme {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) { SamsungSummaryCard(summary, true) }
        } } }
        compose.onNodeWithText("연결 완료").assertExists()
        compose.onNodeWithText("22분").assertExists()
        compose.onNodeWithText("128 kcal").assertExists()
        compose.onNodeWithText("103 bpm").assertExists()
        compose.onNodeWithText("기록 없음 / 기록 없음").assertExists()
        compose.onNodeWithText("기기 측정값과 FitMate 기록은 따로 표시돼요.").assertExists()
    }

    @Test fun fullscreenPickerCannotDismissWhileSaving() {
        var backs = 0
        compose.setContent { FitmateTheme { WorkoutDesignTheme {
            SamsungPickerPage(busy = true, onBack = { backs++ }) { Text("원본 확인 및 저장 중…") }
        } } }
        compose.onNodeWithContentDescription("리포트로 돌아가기").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, backs) }
    }
}
