package com.fitmate.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ServerAdminDeviceTest {
    private lateinit var state: AdminUiState
    @get:Rule val compose = createComposeRule()
    @Test fun operatorScreenPersistsWarningThroughRealHttp() {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("adminUrl")
        assumeTrue(!url.isNullOrBlank())
        val api = FitMateApiClient(requireNotNull(url))
        api.authenticate("auth/login", mapOf("email" to args.getString("adminEmail"), "password" to args.getString("adminPassword")))
        val member = FitMateApiClient(url)
        val nickname = "device-${UUID.randomUUID().toString().take(12)}"
        member.authenticate("auth/signup", mapOf("email" to "device-${UUID.randomUUID()}@example.com",
            "password" to UUID.randomUUID().toString(), "nickname" to nickname,
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")))
        val id = member.request("GET", "users/me").asJsonObject["id"].asString
        val reportId = api.request("POST", "reports", mapOf("targetType" to "USER", "targetId" to id,
            "reason" to "SPAM", "detail" to "Device integration report")).asJsonObject["reportId"].asString
        state = AdminUiState(ServerAdminRepository(api))
        compose.setContent { FitmateTheme { AdminCenter(state, apiClient = api) {} } }
        awaitText("최근 24시간 운영 현황")
        compose.onNodeWithText("신고", useUnmergedTree = true).performClick()
        compose.onNodeWithText("신고 번호 · 닉네임 · 내용 검색").performTextInput(reportId)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(nickname, substring = true).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(nickname, substring = true).performScrollTo().performClick()
        awaitText("검토 시작")
        compose.onNodeWithText("검토 시작").performScrollTo().assertIsEnabled().performClick()
        awaitText("반려 / 조치 선택")
        compose.onNodeWithText("반려 / 조치 선택").performScrollTo().performClick()
        compose.onNodeWithText("처리 사유 및 사용자 안내 (필수)").performScrollTo().performTextInput("실제 HTTP 기기 검증 경고")
        compose.onNodeWithText("처리 내용 확인").performScrollTo().performClick()
        compose.onNodeWithText("적용").performClick()
        awaitText("처리 결과")
        val persisted = api.request("GET", "admin/reports/$reportId").asJsonObject
        assertEquals("RESOLVED", persisted.getAsJsonObject("report")["status"].asString)
        assertEquals("WARNING", persisted.getAsJsonObject("decision")["action"].asString)
        assertEquals("실제 HTTP 기기 검증 경고", persisted.getAsJsonObject("decision")["publicReason"].asString)
    }
    private fun awaitText(text: String) {
        compose.waitUntil(15_000) {
            check(state.error == null) { "Admin UI error: ${state.error}; waiting for $text" }
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && !state.busy
        }
    }
}
