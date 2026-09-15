package com.fitmate.app.ui

import android.content.Context
import android.content.ContextWrapper
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
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.*
import com.fitmate.app.ui.theme.*
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses only synthetic accounts and a separate encrypted file in the isolated test app. */
class RememberedAccountDeviceTest {
    @get:Rule val compose = createComposeRule()

    private class Fixture : AutoCloseable {
        @Volatile var status = 200
        @Volatile var refreshes = 0
        val http = MateUiTestServer(automaticLogin = false) { _, path, body ->
            when (path) {
                "/auth/login" -> 200 to mapOf("tokens" to tokens(0))
                "/auth/refresh" -> when {
                    status != 200 -> status to "일시적인 연결 오류"
                    body.get("refreshToken").asString != "fixture-refresh-$refreshes" -> 401 to "Expired fixture"
                    else -> { refreshes++; 200 to tokens(refreshes) }
                }
                else -> 200 to mapOf("id" to "fixture-user")
            }
        }
        private fun tokens(index: Int) = mapOf("accessToken" to "fixture-access-$index", "refreshToken" to "fixture-refresh-$index")
        val context: Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            private val isolated = File(baseContext.noBackupFilesDir, "remember-test-${UUID.randomUUID()}").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = isolated
        }
        val store = SessionTokenStore(context)
        fun client() = FitMateApiClient(http.baseUrl, tokenStore = store)
        fun saved(): FitMateApiClient = client().also {
            it.authenticate("auth/login", emptyMap<String, String>(), "mint@example.com")
            it.logout()
        }
        init { check(BuildConfig.APPLICATION_ID.endsWith(".dev.records")) }
        override fun close() { store.write(null); http.close() }
    }

    private fun content(dark: Boolean, large: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val previous = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = previous } }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f),
                LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = dark) {
                    Surface(Modifier.widthIn(max = if (large) 320.dp else 600.dp).fillMaxHeight().safeDrawingPadding()) { body() }
                }
            }
        }
    }
    private fun click(label: String) {
        compose.waitForIdle()
        val node = compose.onAllNodesWithText(label).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun await(label: String) = compose.waitUntil(8_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0); compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "remembered-account-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun encryptedAccountSurvivesNewClientAndRotatesOnDisk() = Fixture().use { f ->
        f.saved()
        val encrypted = File(f.context.noBackupFilesDir, "api-session.bin").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(encrypted.contains("mint@example.com")); assertFalse(encrypted.contains("fixture-refresh"))
        repeat(2) {
            val restarted = f.client()
            assertFalse(restarted.hasSession()); assertEquals("mint@example.com", restarted.rememberedEmail())
            restarted.continueRememberedAccount()
            assertTrue(f.client().hasSession())
            assertEquals(f.store.readState().tokens?.refreshToken, f.store.readState().rememberedAccount?.refreshToken)
            restarted.logout()
        }
        assertEquals(2, f.refreshes)
        f.client().clearSession(); assertNull(f.client().rememberedEmail())
    }

    @Test fun legacyEncryptedSessionMigratesWithoutLosingLogin() = Fixture().use { f ->
        f.store.write(SessionTokens("fixture-access", "fixture-refresh")) // Initialize the app's Keystore key.
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keyStore.getKey("fitmate.api.session", null) as SecretKey) }
        val legacy = """{"accessToken":"fixture-access","refreshToken":"fixture-refresh"}""".toByteArray()
        File(f.context.noBackupFilesDir, "api-session.bin").writeBytes(cipher.iv + cipher.doFinal(legacy))
        assertTrue(f.client().hasSession()); assertNull(f.client().rememberedEmail())
        assertEquals("fixture-refresh", f.store.read()?.refreshToken)
    }

    @Test fun quickLoginAndForgetLight() = quickLoginAndForget(false)
    @Test fun quickLoginAndForgetDark() = quickLoginAndForget(true)
    @Test fun quickLoginAndForgetLargeText() = quickLoginAndForget(true, large = true)
    private fun quickLoginAndForget(dark: Boolean, large: Boolean = false): Unit = Fixture().use { f ->
        var api by mutableStateOf(f.saved())
        var signedIn by mutableStateOf(false)
        var generation by mutableIntStateOf(0)
        content(dark, large) { key(generation) {
            val scope = rememberCoroutineScope()
            if (!signedIn) ServerLogin(Modifier, api, { signedIn = true }, {})
            else SettingsScreen(Modifier, UserProfile("테스트 민트", "테스트 지역", setOf(Sport.FITNESS)),
                {}, {}, {}, { scope.launch {
                    withContext(Dispatchers.IO) { api.logout() }
                    api = f.client(); signedIn = false; generation++
                } }, {}, {}, {}, {}, {}, remembersAccount = true)
        } }
        await("이 계정으로 계속하기")
        compose.onNodeWithText("Fit", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Mate", useUnmergedTree = true).assertExists()
        capture("remembered-${if (large) "large" else if (dark) "dark" else "light"}")
        click("이 계정으로 계속하기")
        compose.waitUntil(8_000) { signedIn }; assertTrue(api.hasSession())
        click("로그아웃")
        compose.onNodeWithText("로그인 화면으로 이동합니다.", substring = true).assertExists()
        click("취소"); assertTrue(signedIn)
        click("로그아웃"); click("확인")
        await("이 계정으로 계속하기"); assertFalse(api.hasSession())
        click("기억한 계정 삭제")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("기억한 계정").fetchSemanticsNodes().isEmpty() }
        assertNull(f.client().rememberedEmail())
        compose.onNodeWithText("로그인").assertExists()
    }

    @Test fun connectionErrorCanRetryAndExpiredAccountFallsBackToPassword() = Fixture().use { f ->
        val api = f.saved()
        content(false) { ServerLogin(Modifier, api, {}, {}) }
        await("이 계정으로 계속하기"); f.status = 503
        click("이 계정으로 계속하기"); await("일시적인 연결 오류")
        assertEquals("mint@example.com", f.client().rememberedEmail())
        f.status = 401; click("이 계정으로 계속하기")
        await("기억한 계정의 로그인이 만료되었어요. 비밀번호로 다시 로그인해 주세요.")
        compose.onNode(hasSetTextAction() and hasText("이메일")).assertTextContains("mint@example.com")
        assertNull(f.client().rememberedEmail())
    }
}
