package com.fitmate.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebResourceRequest
import android.webkit.WebView
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
import com.fitmate.app.data.remote.*
import com.fitmate.app.ui.theme.*
import com.google.gson.JsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** All changes stay inside the device's fixture. Never modifies the user's account or consent. */
class AuthSettingsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private class Fixture : AutoCloseable {
        @Volatile var failLogin = false
        @Volatile var failSettings = false
        @Volatile var failPatch = false
        val writes = CopyOnWriteArrayList<Pair<String, JsonObject>>()
        var notificationSettings = mapOf("inAppEnabled" to true, "dmEnabled" to true, "mateEnabled" to true, "communityEnabled" to false)
        val terms = listOf(mapOf("id" to "terms", "type" to "SERVICE_TERMS", "required" to true, "version" to "1.0"),
            mapOf("id" to "privacy", "type" to "PRIVACY", "required" to true, "version" to "1.0"))
        val requests = CopyOnWriteArrayList(listOf(mapOf("userId" to "request-one", "nickname" to "테스트 러너"), mapOf("userId" to "request-two", "nickname" to "테스트 메이트")))
        val http = MateUiTestServer(automaticLogin = false) { method, path, body ->
            if (method != "GET") writes += path to body
            when {
                path == "/auth/login" && failLogin -> 400 to "로그인 테스트 오류"
                path in listOf("/auth/login", "/auth/signup") -> 200 to mapOf("tokens" to mapOf("accessToken" to UUID.randomUUID().toString(), "refreshToken" to UUID.randomUUID().toString()))
                path.contains("availability") -> 200 to mapOf("available" to true)
                path == "/terms" -> 200 to mapOf("items" to terms)
                path == "/sports" -> 200 to mapOf("items" to listOf(mapOf("code" to "HEALTH", "name" to "헬스"), mapOf("code" to "RUNNING", "name" to "러닝")))
                path.startsWith("/regions?") -> 200 to mapOf("items" to listOf(mapOf("id" to "test-region", "code" to "KR-LAW-1168010100", "name" to "테스트 지역", "level" to "TOWN")))
                path == "/users/me/notification-settings" && method == "GET" -> if (failSettings) 503 to "설정 조회 테스트 오류" else 200 to notificationSettings
                path == "/users/me/notification-settings" -> if (failPatch) 503 to "설정 저장 테스트 오류" else {
                    notificationSettings = notificationSettings.mapValues { (key, _) -> body.get(key).asBoolean }; 200 to notificationSettings
                }
                path == "/users/me/consents" -> 200 to terms.map { mapOf("termsDocumentId" to it["id"]) }
                path == "/users/me/follow-requests" -> 200 to mapOf("items" to requests)
                path.contains("/follow-requests/") -> { requests.removeAll { it["userId"] == path.split('/')[4] }; 200 to emptyMap<String, Any>() }
                else -> 200 to mapOf("items" to emptyList<Any>())
            }
        }
        val api = http.api().also { writes.clear() }
        override fun close() = http.close()
    }
    private fun content(dark: Boolean, large: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val old = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = old } }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f),
                LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
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
    private fun field(label: String, value: String) = compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    private fun toggle(label: String) {
        Espresso.closeSoftKeyboard()
        compose.onNode(isToggleable() and hasText(label)).performScrollTo().performClick()
    }
    private fun top() { Espresso.closeSoftKeyboard(); compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0); compose.waitForIdle() }
    private fun capture(name: String) {
        Espresso.closeSoftKeyboard(); compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(200)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "auth-settings-ui-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun loginLight() = login(false)
    @Test fun loginDark() = login(true)
    private fun login(dark: Boolean) = Fixture().use { f ->
        f.api.clearSession(); f.failLogin = true
        var loggedIn = false
        var signup = false
        content(dark) { ServerLogin(Modifier, f.api, { loggedIn = true }, { signup = true }) }
        compose.onNodeWithText("로그인").assertIsNotEnabled()
        field("이메일", "mint@example.com"); top(); capture("27-login-${if (dark) "dark" else "light"}")
        field("비밀번호", "Synthetic9"); click("로그인")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("로그인 테스트 오류").fetchSemanticsNodes().isNotEmpty() }
        f.failLogin = false; click("로그인")
        compose.waitUntil(8_000) { loggedIn }
        assertTrue(f.api.hasSession()); click("회원가입"); assertTrue(signup)
    }
    @Test fun signupLight() = signup(false)
    @Test fun signupDark() = signup(true)
    private fun signup(dark: Boolean) = Fixture().use { f ->
        var saved: UserProfile? = null
        val mode = if (dark) "dark" else "light"
        content(dark) { OnboardingScreen(Modifier, f.api, done = { saved = it }) }
        top(); capture("28a-signup-account-$mode")
        click("다음")
        compose.onNodeWithText("이름을 입력해 주세요.").assertExists()
        field("아이디", "mint"); click("중복 확인")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("확인됨").fetchSemanticsNodes().isNotEmpty() }
        field("비밀번호", "Synthetic9"); field("이름", "테스트 민트")
        field("YYYY", "2000"); field("MM", "01"); field("DD", "01")
        click("선택 안 함"); toggle("만 18세 이상입니다."); click("다음")
        compose.onNodeWithText("내 프로필을 꾸며요").assertExists()
        top(); capture("28b-signup-profile-$mode")
        field("닉네임", "테스트 민트"); click("중복 확인")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("사용할 수 있는 닉네임이에요.").fetchSemanticsNodes().isNotEmpty() }
        click("주소로 지역 검색")
        // Simulate only the external postcode result boundary; the existing native region lookup runs normally.
        compose.runOnIdle {
            fun find(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
            val web = requireNotNull(WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find))
            web.stopLoading()
            web.webViewClient.shouldOverrideUrlLoading(web, object : WebResourceRequest {
                override fun getUrl() = Uri.parse("https://fitmate.invalid/postcode-result?code=1168010100")
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = true
                override fun getMethod() = "GET"
                override fun getRequestHeaders() = emptyMap<String, String>()
            })
        }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("테스트 지역").fetchSemanticsNodes().isNotEmpty() }
        click("다음"); click("체력 향상"); click("습관 만들기")
        top(); capture("28c-signup-sports-$mode")
        click("다음"); top(); capture("29a-signup-consent-$mode")
        compose.onNodeWithText("FitMate 시작하기").assertIsNotEnabled()
        toggle("이용약관 동의 (필수)"); toggle("개인정보 수집·이용 동의 (필수)"); click("FitMate 시작하기")
        compose.waitUntil(8_000) { saved != null }
        val body = f.writes.single { it.first == "/auth/signup" }.second
        assertEquals("test-region", body.getAsJsonArray("activityRegionIds")[0].asString)
        assertEquals(2, body.getAsJsonArray("consentDocumentIds").size())
        assertEquals("FITNESS", body.getAsJsonArray("goals")[0].asJsonObject.text("goalCode"))
    }
    @Test fun settingsLight() = settings(false)
    @Test fun settingsDark() = settings(true)
    private fun settings(dark: Boolean) {
        var route by mutableStateOf("settings")
        var appearance by mutableStateOf(AppearanceMode.SYSTEM)
        var logout = 0
        val mode = if (dark) "dark" else "light"
        content(dark) {
            when (route) {
                "settings" -> SettingsScreen(Modifier, UserProfile("테스트", "테스트 지역", setOf(Sport.FITNESS)),
                    { route = "edit" }, { route = "samsung" }, { route = "account" }, { logout++ }, { route = "appearance" }, { route = "notice" }, { route = "follow" }, { route = "reports" }, { route = "admin" })
                "appearance" -> CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(appearance) { appearance = it }) {
                    Screen(background = MaterialTheme.colorScheme.background) { AppearanceSettings() }
                }
            }
        }
        capture("31a-settings-$mode")
        compose.onNodeWithText("운영자 데모").performScrollTo(); capture("31b-settings-more-$mode")
        click("로그아웃"); click("취소"); assertEquals(0, logout)
        click("화면 모드"); capture("31c-appearance-$mode")
        click("다크"); compose.runOnIdle { assertEquals(AppearanceMode.DARK, appearance) }
        click("라이트"); click("시스템 설정 따르기")
        compose.runOnIdle { assertEquals(AppearanceMode.SYSTEM, appearance); route = "settings" }
        click("Samsung Health"); compose.runOnIdle { assertEquals("samsung", route) }
    }
    @Test fun accountLight() = account(false)
    @Test fun accountDark() = account(true)
    private fun account(dark: Boolean) = Fixture().use { f ->
        var health by mutableStateOf(false)
        var location by mutableStateOf(false)
        var deletes = 0
        content(dark) { ServerAccountSettings(Modifier, f.api, {}, {}, health, { health = it }, location, { location = it }, { deletes++ }) }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("앱 알림").fetchSemanticsNodes().isNotEmpty() }
        top(); capture("31d-account-${if (dark) "dark" else "light"}")
        toggle("DM 알림")
        compose.waitUntil(8_000) { !f.notificationSettings.getValue("dmEnabled") }
        compose.onNode(isToggleable() and hasText("이용약관 · 1.0")).assertIsNotEnabled()
        toggle("기기 내 건강정보 이용 동의"); assertTrue(health)
        click("내 기기의 헬스 기록·임시 운동 삭제"); click("취소"); assertEquals(0, deletes)
        click("비밀번호 변경")
        compose.onNodeWithText("확인").assertIsNotEnabled()
        Espresso.pressBack()
        click("회원 탈퇴"); compose.onNodeWithText("확인").assertIsNotEnabled(); Espresso.pressBack()
        assertFalse(f.writes.any { it.first == "/users/me" || it.first == "/users/me/password" })
    }
    @Test fun noticesLight() = notices(false)
    @Test fun noticesDark() = notices(true)
    private fun notices(dark: Boolean) {
        val state = SocialState(emptyList(), emptyList())
        state.accounts.notices += listOf(AppNotice(1, "테스트 운동 일정이 있어요", "메이트", "MATE_LIST"), AppNotice(2, "테스트 운동 기록을 확인했어요", "운동", "HISTORY", read = true))
        var opened: AppNotice? = null
        content(dark) { NotificationFlow(Modifier, state, { opened = it }) }
        capture("30a-notifications-${if (dark) "dark" else "light"}")
        click("테스트 운동 일정이 있어요"); assertEquals(1L, opened?.id)
        assertTrue(state.accounts.notices.first().read)
        compose.onNodeWithContentDescription("알림 관리").performClick(); click("전체 삭제"); click("취소")
        assertEquals(2, state.accounts.notices.size)
    }
    @Test fun followsLight() = follows(false)
    @Test fun followsDark() = follows(true)
    private fun follows(dark: Boolean) = Fixture().use { f ->
        content(dark) { FollowRequestsScreen(Modifier, SocialState(emptyList(), emptyList()), ServerSocialApi(f.api)) }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("테스트 러너").fetchSemanticsNodes().isNotEmpty() }
        capture("30b-follow-requests-${if (dark) "dark" else "light"}")
        click("수락"); compose.waitUntil(8_000) { f.requests.size == 1 }
        click("거절"); compose.waitUntil(8_000) { f.requests.isEmpty() }
        assertEquals(2, f.writes.size)
    }
    @Test fun healthLight() = health(false)
    @Test fun healthDark() = health(true)
    private fun health(dark: Boolean) {
        var agreed = false
        var later = false
        content(dark) { HealthConsentScreen(Modifier, { agreed = true }, { later = true }) }
        capture("29b-health-consent-${if (dark) "dark" else "light"}")
        click("나중에"); assertTrue(later); assertFalse(agreed)
        click("건강정보 처리에 동의하고 계속"); assertTrue(agreed)
    }
    @Test fun largeTextAccountFailureAndRecovery() = Fixture().use { f ->
        f.failSettings = true
        content(true, large = true) { ServerAccountSettings(Modifier, f.api, {}, {}, false, {}, false, {}, {}) }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("설정 조회 테스트 오류").fetchSemanticsNodes().isNotEmpty() }
        f.failSettings = false; click("다시 불러오기")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("앱 알림").fetchSemanticsNodes().isNotEmpty() }
        f.failPatch = true; toggle("앱 알림")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("설정 저장 테스트 오류").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(f.notificationSettings.getValue("inAppEnabled"))
        f.failPatch = false; toggle("앱 알림")
        compose.waitUntil(8_000) { !f.notificationSettings.getValue("inAppEnabled") }
    }
}
