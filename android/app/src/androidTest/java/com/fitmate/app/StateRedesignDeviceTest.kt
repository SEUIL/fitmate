package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.ServerInbodyApi
import com.fitmate.app.ui.theme.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic HTTP responses only; no changes to the connected user's server data. */
class StateRedesignDeviceTest {
    @get:Rule val compose = createComposeRule()
    private class Fixture : AutoCloseable {
        @Volatile var failAccount = true
        @Volatile var failList = false
        @Volatile var holdList = false
        @Volatile var holdDelete = false
        @Volatile var analysisStatus = "PROCESSING"
        val releaseList = CountDownLatch(1)
        val releaseDelete = CountDownLatch(1)
        val requests = CopyOnWriteArrayList<String>()
        val records = CopyOnWriteArrayList<Map<String, Any>>()
        fun record() = mapOf("id" to "record", "measuredDate" to "2026-09-10", "weightKg" to 70.0,
            "skeletalMuscleMassKg" to 30.0, "bodyFatPercentage" to 20.0, "sourceType" to "MANUAL")
        val http = MateUiTestServer { method, path, _ ->
            requests += "$method $path"
            when {
                path == "/users/me" -> if (failAccount) 503 to "계정 조회 테스트 오류" else 200 to mapOf("id" to "self")
                path == "/inbody-records" && method == "GET" -> {
                    if (holdList) check(releaseList.await(8, TimeUnit.SECONDS))
                    if (failList) 503 to "기록 조회 테스트 오류" else 200 to mapOf("items" to records)
                }
                path == "/inbody-records" && method == "POST" -> 503 to "저장 응답 테스트 오류"
                path == "/inbody-records/record" && method == "DELETE" -> {
                    if (holdDelete) check(releaseDelete.await(8, TimeUnit.SECONDS))
                    records.clear(); 200 to emptyMap<String, Any>()
                }
                path == "/inbody-analyses/analysis" -> 200 to mapOf("id" to "analysis", "status" to analysisStatus,
                    "provider" to "MOCK", "fields" to listOf(
                        mapOf("code" to "MEASUREMENT_DATE", "value" to "2026-09-10", "status" to "RECOGNIZED"),
                        mapOf("code" to "WEIGHT_KG", "value" to "70", "status" to "RECOGNIZED"),
                        mapOf("code" to "SKELETAL_MUSCLE_MASS_KG", "value" to "30", "status" to "RECOGNIZED"),
                        mapOf("code" to "BODY_FAT_PERCENT", "value" to "20", "status" to "NEEDS_REVIEW")))
                else -> 200 to mapOf("items" to emptyList<Any>())
            }
        }
        val api = http.api()
        val inbody = ServerInbodyApi(api)
        override fun close() { releaseList.countDown(); releaseDelete.countDown(); http.close() }
    }
    private fun content(dark: Boolean, large: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val old = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = old } }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {},
                LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                FitmateTheme(darkTheme = dark) { WorkoutDesignTheme {
                    Surface(Modifier.widthIn(max = if (large) 320.dp else 600.dp).fillMaxHeight().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) { body() }
                } }
            }
        }
    }
    private fun click(label: String) {
        Espresso.closeSoftKeyboard(); compose.waitForIdle()
        val node = compose.onAllNodesWithText(label).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun top() { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0) }
    private fun capture(name: String, dark: Boolean) {
        Espresso.closeSoftKeyboard(); compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(200)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "states-ui-review").apply { mkdirs() }
        File(dir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun sessionLight() = session(false)
    @Test fun sessionDark() = session(true)
    private fun session(dark: Boolean) = Fixture().use { f ->
        content(dark) { ServerSessionGate(f.api, { Text("가입") }) { Text("계정 확인 완료") } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("계정 조회 테스트 오류").fetchSemanticsNodes().isNotEmpty() }
        capture("27b-session-error", dark)
        f.failAccount = false; click("다시 시도")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("계정 확인 완료").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, f.requests.count { it == "GET /users/me" })
    }
    @Test fun largeSessionCanReturnToLogin() = Fixture().use { f ->
        content(false, large = true) { ServerSessionGate(f.api, { Text("가입") }) { Text("계정 확인 완료") } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("다시 로그인").fetchSemanticsNodes().isNotEmpty() }
        click("다시 로그인")
        compose.onNodeWithText("이메일").assertExists()
        compose.onNodeWithText("로그인").assertIsNotEnabled()
        assertEquals(1, f.requests.count { it == "GET /users/me" })
    }
    @Test fun notificationsLight() = notifications(false)
    @Test fun notificationsDark() = notifications(true)
    private fun notifications(dark: Boolean) {
        val state = SocialState(emptyList(), emptyList())
        content(dark) { NotificationFlow(Modifier, state, {}) }
        compose.onNodeWithText("새로운 알림이 없어요").assertIsDisplayed()
        capture("30c-notifications-empty", dark)
        compose.onNodeWithContentDescription("알림 관리").performClick()
        compose.onNodeWithText("모두 읽음").assertIsNotEnabled()
        compose.onNodeWithText("전체 삭제").assertIsNotEnabled()
        Espresso.pressBack()
        compose.onNodeWithContentDescription("알림 설정").performClick()
        assertTrue(state.accounts.notices.isEmpty())
    }
    @Test fun emptyInbodyLight() = emptyInbody(false)
    @Test fun emptyInbodyDark() = emptyInbody(true)
    private fun emptyInbody(dark: Boolean) = Fixture().use { f ->
        val state = InbodyState()
        content(dark) { InbodyFlow(Modifier, state, f.inbody) }
        compose.waitUntil(8_000) { state.loaded }
        compose.onNodeWithText("아직 측정 기록이 없어요.").assertExists()
        compose.onNodeWithText("변화 차트").assertDoesNotExist()
        capture("23c-inbody-empty", dark)
        click("직접 입력"); compose.runOnIdle { assertEquals("검수", state.phase) }
        assertFalse(f.requests.any { it.startsWith("POST") })
    }
    @Test fun loadingAndFailureDoNotShowEmptyRecords(): Unit = Fixture().use { f ->
        f.holdList = true; f.failList = true
        val state = InbodyState()
        content(false) { InbodyFlow(Modifier, state, f.inbody) }
        compose.waitUntil(8_000) { f.requests.contains("GET /inbody-records") }
        compose.onNodeWithText("기록을 불러오고 있어요.").assertExists()
        compose.onNodeWithText("아직 측정 기록이 없어요.").assertDoesNotExist()
        compose.onNodeWithText("변화 차트").assertDoesNotExist()
        f.releaseList.countDown()
        compose.waitUntil(8_000) { state.listError != null }
        compose.onNodeWithText("기록 조회 테스트 오류").assertExists()
        compose.onNodeWithText("아직 측정 기록이 없어요.").assertDoesNotExist()
        f.failList = false; click("기록 새로고침")
        compose.waitUntil(8_000) { state.loaded && state.listError == null }
        compose.onNodeWithText("아직 측정 기록이 없어요.").assertExists()
    }
    @Test fun processingLight() = processing(false)
    @Test fun processingDark() = processing(true)
    private fun processing(dark: Boolean) = Fixture().use { f ->
        val state = InbodyState().apply { phase = "판독 중"; analysisId = "analysis"; source = "사진 판독" }
        content(dark) { InbodyFlow(Modifier, state, f.inbody) }
        compose.waitUntil(8_000) { f.requests.contains("GET /inbody-analyses/analysis") }
        capture("25c-inbody-processing", dark)
        click("목록에서 기다리기")
        compose.runOnIdle { assertEquals("목록", state.phase); assertEquals("analysis", state.analysisId) }
        assertFalse(f.requests.any { it.startsWith("POST") || it.startsWith("DELETE") })
        f.analysisStatus = "REVIEW_REQUIRED"; click("진행 중인 판독 확인")
        compose.waitUntil(8_000) { state.phase == "검수" }
        assertTrue(state.reviewNotice.contains("실제 AI 분석이 아닙니다"))
    }
    @Test fun reviewLight() = review(false)
    @Test fun reviewDark() = review(true)
    @Test fun largeReviewBlocksDuplicate() = review(true, large = true)
    private fun review(dark: Boolean, large: Boolean = false) = Fixture().use { f ->
        val state = InbodyState().apply {
            openManual(); source = "사진 판독"; analysisId = "analysis"; date = "2026.09.10"; weight = "70"; muscle = "30"; fat = "20"
            reviewNotice = "서버 예시 판독입니다. 실제 AI 분석이 아닙니다."
        }
        content(dark, large) { InbodyFlow(Modifier, state, f.inbody) }
        click("검수 후 저장")
        compose.onNodeWithText("판독 수치를 확인하고 검수 완료를 선택하세요.").performScrollTo().assertIsDisplayed()
        assertFalse(f.requests.contains("POST /inbody-records"))
        if (!large) capture("26b-inbody-review-error", dark)
        click("결과표 수치를 직접 확인했어요"); click("검수 후 저장")
        compose.waitUntil(8_000) { state.saveUncertain && !state.busy }
        compose.onNodeWithText("검수 후 저장").assertIsNotEnabled()
        assertEquals(1, f.requests.count { it == "POST /inbody-records" })
        if (!large) { top(); capture("26c-inbody-save-uncertain", dark) }
        click("목록에서 저장 여부 확인")
        compose.runOnIdle { assertEquals("목록", state.phase) }
        assertEquals(1, f.requests.count { it == "POST /inbody-records" })
    }
    @Test fun deleteLight() = delete(false)
    @Test fun deleteDark() = delete(true)
    private fun delete(dark: Boolean) = Fixture().use { f ->
        f.records += f.record()
        val state = InbodyState()
        content(dark) { InbodyFlow(Modifier, state, f.inbody) }
        compose.waitUntil(8_000) { state.loaded }
        click("삭제"); capture("23d-inbody-delete-confirm", dark)
        click("취소"); assertFalse(f.requests.any { it.startsWith("DELETE") })
        click("삭제"); f.holdDelete = true; click("확인")
        compose.waitUntil(8_000) { state.busy && f.requests.any { it.startsWith("DELETE") } }
        compose.onNodeWithText("확인").assertIsNotEnabled(); compose.onNodeWithText("취소").assertIsNotEnabled()
        Espresso.pressBack(); compose.onNodeWithText("측정 기록 삭제").assertExists()
        f.releaseDelete.countDown()
        compose.waitUntil(8_000) { state.deleting == null && !state.busy && state.records.isEmpty() }
        assertEquals(1, f.requests.count { it.startsWith("DELETE") })
    }
    @Test fun logoutLight() = logout(false)
    @Test fun logoutDark() = logout(true)
    private fun logout(dark: Boolean) {
        var loggedOut = 0
        content(dark) { SettingsScreen(Modifier, UserProfile("테스트", "테스트 지역", setOf(Sport.FITNESS)),
            {}, {}, {}, { loggedOut++ }, {}, {}, {}, {}, {}) }
        click("로그아웃"); capture("31e-logout-confirm", dark)
        click("취소"); assertEquals(0, loggedOut)
        click("로그아웃"); click("확인"); assertEquals(1, loggedOut)
    }
}
