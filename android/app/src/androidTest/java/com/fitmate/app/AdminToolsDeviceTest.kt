package com.fitmate.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AdminToolsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun operator(): FitMateApiClient {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("adminUrl")
        assumeTrue(!url.isNullOrBlank())
        return FitMateApiClient(requireNotNull(url)).also {
            it.authenticate("auth/login", mapOf("email" to args.getString("adminEmail"), "password" to args.getString("adminPassword")))
        }
    }
    private fun member(): FitMateApiClient {
        val api = FitMateApiClient(requireNotNull(InstrumentationRegistry.getArguments().getString("adminUrl")))
        api.authenticate("auth/signup", mapOf("email" to "tools-device-${UUID.randomUUID()}@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "tools-${UUID.randomUUID().toString().take(12)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")))
        return api
    }

    @Test fun noticeCanBeCreatedPublishedReadEditedAndEndedOnDevice() {
        val admin = operator(); val user = member()
        var managing by mutableStateOf(true)
        compose.setContent { FitmateTheme { key(managing) { NoticeScreen(if (managing) admin else user, managing) {} } } }
        click("새 공지 작성")
        val title = "기기 공지 ${UUID.randomUUID().toString().take(6)}"
        compose.onNode(hasSetTextAction() and hasText("제목", substring = true)).performTextInput(title)
        compose.onNode(hasSetTextAction() and hasText("본문", substring = true)).performScrollTo().performTextInput("실제 서버에 저장하는 공지 본문")
        closeSoftKeyboard()
        click("저장"); click("확인", false); click("전체 사용자에게 게시"); click("확인", false)
        await("게시 중")
        val notice = admin.request("GET", "admin/notices").asJsonObject["items"].asJsonArray.first { it.asJsonObject["title"].asString == title }.asJsonObject
        val id = notice["id"].asString
        assertEquals("PUBLISHED", user.request("GET", "notices/$id").asJsonObject["status"].asString)
        click("수정")
        compose.onNode(hasSetTextAction() and hasText("본문", substring = true)).performScrollTo().performTextReplacement("게시 후 수정한 공지 본문")
        closeSoftKeyboard(); click("저장"); click("확인", false); await("게시 중")
        assertEquals("게시 후 수정한 공지 본문", user.request("GET", "notices/$id").asJsonObject["body"].asString)
        compose.runOnIdle { managing = false }
        click(title); await("게시 후 수정한 공지 본문"); screenshot("notice-user")
        compose.runOnIdle { managing = true }
        click(title); click("게시 종료"); click("확인", false); await("종료")
        val failure = runCatching { user.request("GET", "notices/$id") }.exceptionOrNull()
        assertTrue(failure is com.fitmate.app.data.remote.ApiFailure)
        assertEquals(404, (failure as com.fitmate.app.data.remote.ApiFailure).status)
    }

    @Test fun categoryOrderSavesThroughDeviceUi() {
        val api = operator()
        val before = api.request("GET", "admin/categories").asJsonObject["items"].asJsonArray.map { it.asJsonObject }
        compose.setContent { FitmateTheme { AdminCategoryScreen(api) {} } }
        val first = before.first()["name"].asString
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("$first 아래로").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("$first 아래로").performScrollTo().assertIsEnabled().performClick()
        click("순서 저장"); click("저장", false); await("표시 순서를 저장했습니다.")
        val after = api.request("GET", "admin/categories").asJsonObject["items"].asJsonArray
        assertEquals(before[1]["id"].asString, after[0].asJsonObject["id"].asString)
        assertEquals(before[0]["id"].asString, after[1].asJsonObject["id"].asString)
        screenshot("categories")
    }

    @Test fun systemSummaryHandlesLightDarkAndRangeChanges() {
        val api = operator()
        var dark by mutableStateOf(false)
        compose.setContent { FitmateTheme(darkTheme = dark) { AdminSystemScreen(api) {} } }
        await("API 오류율 · 응답 시간 · 활성 사용자: 미집계")
        screenshot("ocr-light")
        click("최근 7일")
        await("API 오류율 · 응답 시간 · 활성 사용자: 미집계")
        compose.runOnIdle { dark = true }
        screenshot("ocr-dark")
        compose.onNodeWithText("API 오류율 · 응답 시간 · 활성 사용자: 미집계").performScrollTo().assertExists()
    }

    @Test fun regularAccountCannotOpenOperatorCenter() {
        operator()
        val api = member()
        compose.setContent { FitmateTheme { AdminCenter(apiClient = api) {} } }
        await(AdminError.FORBIDDEN.message)
        compose.onNodeWithText("대기 신고").assertDoesNotExist()
    }
    @Test fun loginTitleIsVisibleInLightAndDarkMode() {
        var dark by mutableStateOf(false)
        val api = FitMateApiClient("http://127.0.0.1:1")
        compose.setContent {
            CompositionLocalProvider(com.fitmate.app.ui.theme.LocalAppearancePreference provides
                com.fitmate.app.ui.theme.AppearancePreference(if (dark) com.fitmate.app.ui.theme.AppearanceMode.DARK else com.fitmate.app.ui.theme.AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = dark) { ServerLogin(androidx.compose.ui.Modifier, api, {}, {}) }
            }
        }
        await("다시 만나서 반가워요")
        compose.onNodeWithText("다시 만나서 반가워요").assertIsDisplayed()
        screenshot("login-light")
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("다시 만나서 반가워요").assertIsDisplayed()
        screenshot("login-dark")
    }
    private fun await(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String, scroll: Boolean = true) {
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val dir = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("admin-evidence"))
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
