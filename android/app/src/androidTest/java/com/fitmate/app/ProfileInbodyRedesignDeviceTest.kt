package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.*
import com.fitmate.app.ui.theme.*
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** All writes and analysis responses stay inside the device's HTTP fixture. */
class ProfileInbodyRedesignDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val extras = mapOf("HEIGHT_CM" to 172.0, "BODY_FAT_MASS_KG" to 14.0, "BMI" to 23.7,
        "WAIST_HIP_RATIO" to .85, "BASAL_METABOLIC_RATE_KCAL" to 1500.0,
        "RIGHT_ARM_MUSCLE_KG" to 3.1, "LEFT_ARM_MUSCLE_KG" to 3.0, "TRUNK_MUSCLE_KG" to 25.0,
        "RIGHT_LEG_MUSCLE_KG" to 8.2, "LEFT_LEG_MUSCLE_KG" to 8.1,
        "RIGHT_ARM_FAT_KG" to .7, "LEFT_ARM_FAT_KG" to .8, "TRUNK_FAT_KG" to 7.9,
        "RIGHT_LEG_FAT_KG" to 2.0, "LEFT_LEG_FAT_KG" to 2.1, "RECOMMENDED_WEIGHT_KG" to 68.0, "WEIGHT_CONTROL_KG" to -2.0)
    private inner class Fixture : AutoCloseable {
        @Volatile var failSave = false
        @Volatile var failList = false
        val saves = CopyOnWriteArrayList<JsonObject>()
        val records = CopyOnWriteArrayList<Map<String, Any>>(listOf(record("one", "2026-09-10", 70.0), record("two", "2026-09-03", 71.0), record("three", "2026-08-27", 72.0)))
        fun record(id: String, date: String, weight: Double) = mapOf("id" to id, "measuredDate" to date, "weightKg" to weight,
            "skeletalMuscleMassKg" to 30.0, "bodyFatPercentage" to 20.0, "optionalValues" to extras, "sourceType" to "MANUAL")
        val http = MateUiTestServer { method, path, body ->
            when {
                path == "/inbody-records" && method == "GET" -> if (failList) 503 to "기록 조회 테스트 오류" else 200 to mapOf("items" to records)
                path == "/inbody-records" && method == "POST" -> {
                    saves += body
                    if (failSave) 503 to "저장 응답 테스트 오류" else {
                        val saved = record("saved", body.text("measuredDate"), body.get("weightKg").asDouble) + mapOf("skeletalMuscleMassKg" to body.get("skeletalMuscleMassKg").asDouble, "bodyFatPercentage" to body.get("bodyFatPercentage").asDouble)
                        records.add(0, saved); 200 to saved
                    }
                }
                path.startsWith("/inbody-records/") && method == "DELETE" -> { records.removeAll { it["id"] == path.substringAfterLast('/') }; 200 to emptyMap<String, Any>() }
                path == "/inbody-analyses/analysis" -> 200 to mapOf("id" to "analysis", "status" to "REVIEW_REQUIRED", "provider" to "MOCK", "fields" to listOf(
                    mapOf("code" to "MEASUREMENT_DATE", "value" to "2026-09-10", "status" to "RECOGNIZED"),
                    mapOf("code" to "WEIGHT_KG", "value" to "70.0", "status" to "RECOGNIZED"),
                    mapOf("code" to "SKELETAL_MUSCLE_MASS_KG", "value" to "30.0", "status" to "RECOGNIZED"),
                    mapOf("code" to "BODY_FAT_PERCENT", "value" to "20.0", "status" to "NEEDS_REVIEW")))
                path.endsWith("/profile") -> 200 to mapOf("followerCount" to 8, "followingCount" to 10)
                else -> 200 to mapOf("items" to emptyList<Any>(), "hasNext" to false)
            }
        }
        val api = http.api()
        val server = ServerInbodyApi(api)
        override fun close() = http.close()
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
        compose.waitForIdle()
    }
    private fun click(label: String) {
        Espresso.closeSoftKeyboard(); compose.waitForIdle()
        val node = compose.onAllNodesWithText(label).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun heading(label: String) {
        val node = compose.onAllNodesWithText(label).onFirst().performScrollTo()
        val viewport = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        val distance = node.fetchSemanticsNode().boundsInRoot.top - viewport.fetchSemanticsNode().boundsInRoot.top - 24f
        viewport.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        Espresso.closeSoftKeyboard(); compose.waitForIdle(); compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(200)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "profile-inbody-ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun editMetric(label: String, value: String) {
        click(label)
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        click("완료")
    }

    @Test fun profileLight() = profile(false)
    @Test fun profileDark() = profile(true)
    private fun profile(dark: Boolean) = Fixture().use { fixture ->
        var profile by mutableStateOf(UserProfile("테스트 민트", "테스트 지역", setOf(Sport.FITNESS, Sport.RUNNING), displayName = "민트", bio = "무리하지 않고 꾸준히 운동해요.",
            goals = linkedSetOf("체력 향상", "운동 습관 만들기"), experience = "6개월 미만", availableTime = "평일 저녁"))
        var saved: UserProfile? = null
        val social = SocialState(emptyList(), emptyList())
        val theme = if (dark) "dark" else "light"
        content(dark) { ProfileScreen(Modifier, profile, social, emptyList(), emptyList(), fixture.api,
            onReload = {}, onSave = { saved = it; profile = it }, onSavePhoto = { profile }, onOpenInbody = {}, onSamsungTest = {}, onAccount = {}, onHistory = {},
            onNotifications = {}, onReports = {}, onOpenAdminDemo = {}, editRequest = 0, onOpenSettings = {}, selfUserId = "self") }
        capture("21-profile-$theme")
        click("프로필 편집")
        heading("프로필 수정"); capture("22a-profile-basic-$theme")
        compose.onNode(hasSetTextAction() and hasText("닉네임")).performTextReplacement("테스트 수정")
        heading("관심 운동"); capture("22b-profile-sports-$theme")
        click("클라이밍")
        compose.onNode(hasSetTextAction() and hasText("직접 추가할 운동")).performScrollTo().performTextInput("수영")
        click("관심 운동 추가")
        click("저장")
        compose.waitUntil(8_000) { saved != null }
        assertEquals("테스트 수정", saved!!.nickname)
        assertEquals(setOf("수영"), saved!!.customInterests)
        assertTrue(Sport.CLIMBING in saved!!.interests)
        assertEquals(listOf("체력 향상", "운동 습관 만들기"), saved!!.goals.toList())
    }

    @Test fun inbodyLight() = inbody(false)
    @Test fun inbodyDark() = inbody(true)
    private fun inbody(dark: Boolean) = Fixture().use { fixture ->
        val state = InbodyState()
        val theme = if (dark) "dark" else "light"
        content(dark) { InbodyFlow(Modifier, state, fixture.server) }
        compose.waitUntil(8_000) { state.loaded }
        capture("23a-inbody-list-$theme")
        heading("측정 기록"); capture("23b-inbody-records-$theme")
        click("상세 결과 보기")
        val detailList = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        detailList.performScrollToIndex(0); capture("24a-inbody-detail-$theme")
        detailList.performScrollToIndex(4)
        compose.onNodeWithText("부위별 지방량").assertIsDisplayed()
        capture("24b-inbody-fat-$theme")
        detailList.performScrollToIndex(2)
        compose.onAllNodesWithText("미측정").onFirst().assertExists()
        compose.runOnIdle { state.openManual(); state.date = "2026.09.09"; state.weight = "70.1"; state.muscle = "30.1"; state.fat = "20.1" }
        heading("인바디 결과 검수"); capture("25-inbody-manual-$theme")
        compose.onNodeWithText("결과표 수치를 직접 확인했어요").assertDoesNotExist()
        editMetric("체중", "69.8")
        click("검수 후 저장")
        compose.waitUntil(8_000) { state.phase == "목록" && fixture.saves.isNotEmpty() }
        assertEquals(69.8, fixture.saves.single().get("weightKg").asDouble, .001)
        assertFalse(fixture.saves.single().has("analysisId"))
        click("삭제"); click("취소")
        assertEquals(4, fixture.records.size)
    }

    @Test fun photoReviewLight() = photoReview(false)
    @Test fun photoReviewDark() = photoReview(true)
    private fun photoReview(dark: Boolean) = Fixture().use { fixture ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photoFile = File.createTempFile("synthetic-report-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(640, 440, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 28f }
        listOf("SYNTHETIC TEST REPORT", "Date: 2026-09-10", "Weight: 70.0 kg", "Muscle: 30.0 kg", "Body fat: 20.0 %").forEachIndexed { index, text -> canvas.drawText(text, 36f, 60f + index * 70f, paint) }
        photoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val state = InbodyState().apply { phase = "사진 확인"; source = "사진 판독"; analysisId = "analysis"; photo = android.net.Uri.fromFile(photoFile).toString() }
        val theme = if (dark) "dark" else "light"
        content(dark) { InbodyFlow(Modifier, state, fixture.server) }
        capture("25b-inbody-photo-$theme")
        // Resume an existing analysis; no image upload or external provider call.
        compose.runOnIdle { state.phase = "판독 중" }
        compose.waitUntil(8_000) { state.phase == "검수" }
        assertTrue(state.reviewNotice.contains("서버 예시 판독"))
        heading("인바디 결과 검수"); capture("26-inbody-review-$theme")
        click("검수 후 저장")
        compose.runOnIdle { assertTrue(fixture.saves.isEmpty()); assertNotNull(state.error) }
        click("결과표 수치를 직접 확인했어요")
        click("검수 후 저장")
        compose.waitUntil(8_000) { fixture.saves.isNotEmpty() && state.phase == "목록" }
        assertEquals("analysis", fixture.saves.single().text("analysisId"))
    }

    @Test fun uncertainSaveBlocksDuplicateAndListFailureIsRetryable() = Fixture().use { fixture ->
        fixture.failSave = true
        val state = InbodyState().apply { openManual(); weight = "70"; muscle = "30"; fat = "20" }
        content(false) { InbodyFlow(Modifier, state, fixture.server) }
        click("검수 후 저장")
        compose.waitUntil(8_000) { state.saveUncertain && !state.busy }
        compose.onNodeWithText("검수 후 저장").assertIsNotEnabled()
        assertEquals(1, fixture.saves.size)
        fixture.failList = true
        click("목록에서 저장 여부 확인")
        compose.waitUntil(8_000) { state.listError != null }
        fixture.failList = false
        click("기록 새로고침")
        compose.waitUntil(8_000) { state.listError == null && state.loaded }
    }

    @Test fun largeTextManualFieldsRemainEditable() = Fixture().use { fixture ->
        val state = InbodyState().apply { openManual() }
        content(true, large = true) { InbodyFlow(Modifier, state, fixture.server) }
        editMetric("체중", "70")
        editMetric("골격근량", "30")
        editMetric("체지방률", "20")
        click("검수 후 저장")
        compose.waitUntil(8_000) { state.phase == "목록" && fixture.saves.isNotEmpty() }
    }

    @Test fun profileEditRequestIsConsumedAcrossTabReentry(): Unit = Fixture().use { fixture ->
        val profile = UserProfile("테스트 닉네임", "테스트 지역", setOf(Sport.FITNESS), goals = setOf("운동 습관 만들기"))
        val social = SocialState(emptyList(), emptyList())
        var showProfile by mutableStateOf(true)
        var editRequest by mutableStateOf(1)
        var saves = 0
        content(false) {
            if (showProfile) ProfileScreen(Modifier, profile, social, emptyList(), emptyList(), fixture.api,
                onReload = {}, onSave = { saves++ }, onSavePhoto = { profile }, onOpenInbody = {},
                onSamsungTest = {}, onAccount = {}, onHistory = {}, onNotifications = {}, onReports = {},
                onOpenAdminDemo = {}, editRequest = editRequest, onOpenSettings = {}, selfUserId = "self",
                onEditRequestConsumed = { editRequest = 0 })
            else Text("홈 테스트 화면")
        }
        fun reenterProfile() {
            compose.runOnIdle { showProfile = false }
            compose.onNodeWithText("홈 테스트 화면").assertExists()
            compose.runOnIdle { showProfile = true }
            compose.onNodeWithText("프로필 편집").assertExists()
            compose.onNodeWithText("프로필 수정").assertDoesNotExist()
        }
        compose.onNodeWithText("프로필 수정").assertExists()
        compose.runOnIdle { assertEquals(0, editRequest) }
        // Leaving the editor via a root tab must not replay the old request.
        reenterProfile()
        repeat(2) { reenterProfile() }
        // A new explicit edit request must still open the editor.
        compose.runOnIdle { editRequest++ }
        compose.onNodeWithText("프로필 수정").assertExists()
        click("취소")
        reenterProfile()
        compose.runOnIdle { editRequest++ }
        compose.onNodeWithText("프로필 수정").assertExists()
        click("저장")
        compose.runOnIdle { assertEquals(1, saves) }
        reenterProfile()
        click("프로필 편집")
        compose.onNodeWithText("프로필 수정").assertExists()
    }

    @Test fun profileConflictKeepsInputsAndCanReloadLatest() = Fixture().use { fixture ->
        var profile by mutableStateOf(UserProfile("기존 닉네임", "테스트 지역", setOf(Sport.FITNESS), goals = setOf("운동 습관 만들기")))
        var reloads = 0
        val social = SocialState(emptyList(), emptyList())
        content(false) { ProfileScreen(Modifier, profile, social, emptyList(), emptyList(), fixture.api,
            onReload = { reloads++; profile = profile.copy(nickname = "최신 닉네임") }, onSave = { throw IllegalStateException("프로필 충돌 테스트") },
            onSavePhoto = { profile }, onOpenInbody = {}, onSamsungTest = {}, onAccount = {}, onHistory = {}, onNotifications = {}, onReports = {},
            onOpenAdminDemo = {}, editRequest = 1, onOpenSettings = {}, selfUserId = "self") }
        compose.onNode(hasSetTextAction() and hasText("닉네임")).performTextReplacement("작성 중 닉네임")
        click("저장")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("프로필 충돌 테스트").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("닉네임")).assertTextContains("작성 중 닉네임")
        click("편집 취소하고 서버 최신 정보 불러오기")
        compose.waitUntil(8_000) { reloads == 1 && compose.onAllNodesWithText("프로필 편집").fetchSemanticsNodes().isNotEmpty() }
    }
}
