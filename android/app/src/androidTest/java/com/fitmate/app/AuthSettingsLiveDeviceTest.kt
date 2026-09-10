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
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in local server test. Login and settings writes use only a new synthetic account. */
class AuthSettingsLiveDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightLoginAndSettings() = verify(false)
    @Test fun darkLoginAndSettings() = verify(true)
    private fun verify(dark: Boolean) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAuthSettings") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val seed = FitMateApiClient(BuildConfig.API_BASE_URL)
        val id = UUID.randomUUID().toString()
        val email = "auth-ui-$id@example.com"
        val password = UUID.randomUUID().toString() + "9a"
        seed.authenticate("auth/signup", mapOf("email" to email, "password" to password, "nickname" to "인증UI-${id.take(6)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to seed.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        val original = seed.request("GET", "users/me/notification-settings").asJsonObject
        val originalDm = original.get("dmEnabled").asBoolean
        val api = FitMateApiClient(BuildConfig.API_BASE_URL)
        var loggedIn by mutableStateOf(false)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val old = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = old } }
            CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
            FitmateTheme(darkTheme = dark) { WorkoutDesignTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                if (!loggedIn) ServerLogin(Modifier, api, { loggedIn = true }, {})
                else ServerAccountSettings(Modifier, api, {}, {}, false, {}, false, {}, {})
            } } }
            }
        }
        compose.onNode(hasSetTextAction() and hasText("이메일")).performTextReplacement(email)
        compose.onNode(hasSetTextAction() and hasText("비밀번호")).performTextReplacement(password)
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("로그인").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("DM 알림").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isToggleable() and hasText("DM 알림")).performScrollTo().performClick()
        compose.waitUntil(10_000) { seed.request("GET", "users/me/notification-settings").asJsonObject.get("dmEnabled").asBoolean != originalDm }
        val stored = seed.request("GET", "users/me/notification-settings").asJsonObject
        assertEquals(!originalDm, stored.get("dmEnabled").asBoolean)
        listOf("inAppEnabled", "mateEnabled", "communityEnabled").forEach { assertEquals(original.get(it), stored.get(it)) }
        assertTrue(api.hasSession())
        assertTrue(api.request("GET", "users/me").asJsonObject.text("email") == email)
    }
}
