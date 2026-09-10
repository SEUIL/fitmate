package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.*
import com.fitmate.app.ui.theme.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in local API: edits only a new synthetic profile and adds its manual measurement. No AI upload. */
class ProfileInbodyLiveDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightProfileAndManualRecord() = verify(false)
    @Test fun darkProfileAndManualRecord() = verify(true)
    private fun click(label: String) {
        Espresso.closeSoftKeyboard(); compose.waitForIdle()
        val node = compose.onAllNodesWithText(label).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun editMetric(label: String, value: String) {
        click(label)
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        click("완료")
    }
    private fun verify(dark: Boolean) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveProfileInbody") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val unique = UUID.randomUUID().toString()
        val api = FitMateApiClient(BuildConfig.API_BASE_URL)
        api.authenticate("auth/signup", mapOf("email" to "profile-ui-$unique@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "프로필UI-${unique.take(6)}", "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        val account = api.request("GET", "users/me").asJsonObject
        var profile by mutableStateOf(account.toUserProfile())
        var version = account.getAsJsonObject("profile").get("version").asLong
        val oldVersion = version
        var inbody by mutableStateOf(false)
        val social = SocialState(emptyList(), emptyList())
        val state = InbodyState()
        val server = ServerInbodyApi(api)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val old = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = old } }
            CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = dark) { WorkoutDesignTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                    if (inbody) InbodyFlow(Modifier, state, server)
                    else ProfileScreen(Modifier, profile, social, emptyList(), emptyList(), api,
                        onReload = {}, onSave = { updated ->
                            val saved = withContext(Dispatchers.IO) { api.request("PATCH", "users/me/profile", profilePatch(profile, updated, version, api.fetchSports().map { it.code }.toSet())).asJsonObject }
                            version = saved.getAsJsonObject("profile").get("version").asLong; profile = saved.toUserProfile()
                        }, onSavePhoto = { profile }, onOpenInbody = { inbody = true }, onSamsungTest = {}, onAccount = {}, onHistory = {}, onNotifications = {}, onReports = {},
                        onOpenAdminDemo = {}, editRequest = 0, onOpenSettings = {}, selfUserId = account.text("id"))
                } } }
            }
        }
        click("프로필 편집")
        val nickname = "수정UI-${unique.take(6)}"
        compose.onNode(hasSetTextAction() and hasText("닉네임")).performTextReplacement(nickname)
        compose.onNode(hasSetTextAction() and hasText("직접 추가할 운동")).performScrollTo().performTextInput("수영")
        click("관심 운동 추가")
        click("저장")
        compose.waitUntil(20_000) { version > oldVersion }
        val loaded = api.request("GET", "users/me").asJsonObject.toUserProfile()
        assertEquals(nickname, loaded.nickname)
        assertTrue("수영" in loaded.customInterests)
        assertEquals(profile.interests, loaded.interests)
        click("인바디 기록")
        compose.waitUntil(20_000) { state.loaded }
        click("직접 입력")
        editMetric("체중", "70.2")
        editMetric("골격근량", "30.1")
        editMetric("체지방률", "20.3")
        click("검수 후 저장")
        compose.waitUntil(20_000) { state.phase == "목록" && state.records.size == 1 && !state.busy }
        val saved = server.records().single()
        assertEquals(70.2, saved.get("weightKg").asDouble, .001)
        assertEquals(30.1, saved.get("skeletalMuscleMassKg").asDouble, .001)
        assertEquals(20.3, saved.get("bodyFatPercentage").asDouble, .001)
        assertEquals("MANUAL", saved.text("sourceType"))
        click("기록 새로고침")
        click("상세 결과 보기")
        compose.onNodeWithText("핵심 요약").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("부위별 지방량"))
        compose.onNodeWithText("부위별 지방량").assertIsDisplayed()
    }
}
