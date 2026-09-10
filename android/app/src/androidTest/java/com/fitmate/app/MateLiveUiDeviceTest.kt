package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.*
import com.fitmate.app.ui.theme.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in local development API test. Creates private synthetic recruitment data; sends no messages. */
class MateLiveUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightCreateEditAndReload() = createEdit(false)
    @Test fun darkCreateEditAndReload() = createEdit(true)

    private fun waitText(label: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(label, substring = false).fetchSemanticsNodes().isNotEmpty() }
    private fun click(label: String) {
        waitText(label)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        val node = compose.onNodeWithText(label, substring = false)
        if (label != "7분/km") node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun enter(label: String, value: String) {
        val input = compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo()
        input.performTextReplacement(value)
        input.assertTextContains(value)
    }

    private fun createEdit(dark: Boolean) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveMateUi") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val unique = UUID.randomUUID().toString()
        val api = FitMateApiClient(BuildConfig.API_BASE_URL)
        api.authenticate("auth/signup", mapOf("email" to "mate-ui-$unique@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "메이트UI-${unique.take(6)}", "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        val account = api.request("GET", "users/me").asJsonObject
        val profile = account.toUserProfile().copy(activityRegionId = "10000000-0000-0000-0000-000000000001", region = "테스트 지역")
        val state = MateServerState().apply { page = "작성" }
        compose.setContent { CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
            val view = LocalView.current
            DisposableEffect(view) { val previous = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = previous } }
            FitmateTheme(darkTheme = dark) { WorkoutDesignTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                ServerMateFlow(Modifier, api, state, profile, account.text("id")) {}
            } } }
        } }
        compose.waitForIdle()
        waitText("러닝")
        val title = "UI 메이트 ${unique.take(6)}"
        enter("모집 제목", title)
        click("러닝")
        compose.onNodeWithText("러닝", substring = false).assertIsSelected()
        click("내일")
        enter("모집 인원", "4")
        enter("운동 내용과 준비물을 알려주세요", "합성 데이터로 검증하는 모집글입니다.")
        enter("공개 장소 (예: 성수역 인근)", "테스트 운동장")
        enter("확정자에게 공개할 집결 위치", "테스트 북문")
        click("맞팔 공개")
        enter("목표 거리 (km)", "5")
        click("목표 페이스"); click("7분/km")
        click("모집 게시")
        try { compose.waitUntil(20_000) { state.page == "상세" && !state.busy } }
        catch (error: ComposeTimeoutException) { throw AssertionError("Create did not complete: ${compose.onRoot().printToString()}", error) }
        waitText("집결 위치 · 테스트 북문")
        val records = ServerMateApi(api)
        val created = records.detail(requireNotNull(state.postId))
        assertEquals(title, created.text("title"))
        assertEquals("MUTUAL", created.text("visibility"))
        assertEquals("RUNNING", created.text("sportCode"))
        assertEquals("7분/km", created.getAsJsonObject("sportFields").text("목표 페이스"))
        assertEquals(4, created.recordInt("capacity"))
        click("모집 정보 수정")
        enter("모집 제목", "$title 수정")
        enter("모집 인원", "5")
        click("수정 저장")
        compose.waitUntil(20_000) { state.page == "상세" && !state.busy }
        waitText("$title 수정")
        val updated = records.detail(requireNotNull(state.postId))
        assertEquals("$title 수정", updated.text("title"))
        assertEquals(5, updated.recordInt("capacity"))
        assertEquals(created.get("sportFields"), updated.get("sportFields"))
        assertEquals(created.get("scheduledStartAt"), updated.get("scheduledStartAt"))
        compose.onNodeWithText("$title 수정").performScrollTo()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "mate-ui-review").apply { mkdirs() }
        File(directory, "live-detail-${if (dark) "dark" else "light"}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("새로고침").performScrollTo().performClick()
        waitText("$title 수정")
    }
}
