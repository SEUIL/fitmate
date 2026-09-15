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
        verifyDecision("WARNING")
    }
    @Test fun operatorScreenPersistsDismissalAndInternalNoteThroughRealHttp() {
        verifyDecision("NONE")
    }
    @Test fun lostDecisionResponseCanBeRecoveredWithoutAnotherSanction() {
        verifyDecision("WARNING", loseResponse = true)
    }
    @Test fun moreThanTwentyReportsAndMissingEvidenceCanBeReadOnDevice() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(!args.getString("adminUrl").isNullOrBlank())
        val api = FitMateApiClient(requireNotNull(args.getString("adminUrl")))
        api.authenticate("auth/login", mapOf("email" to args.getString("adminEmail"), "password" to args.getString("adminPassword")))
        val ids = (1..25).map {
            api.request("POST", "reports", mapOf("targetType" to "POST", "targetId" to UUID.randomUUID().toString(),
                "reason" to "HATE", "detail" to "기기 검증용 원문 없는 신고")).asJsonObject["reportId"].asString
        }
        // Keep the unfiltered first page different from the completed search result.
        api.request("POST", "reports", mapOf("targetType" to "POST", "targetId" to UUID.randomUUID().toString(), "reason" to "OTHER"))
        state = AdminUiState(ServerAdminRepository(api))
        compose.setContent { FitmateTheme { AdminCenter(state, apiClient = api) {} } }
        awaitText("최근 24시간 운영 현황")
        compose.onNodeWithText("신고", useUnmergedTree = true).performClick()
        val beforeSearch = state.snapshot
        compose.onNodeWithText("신고 번호 · 닉네임 · 내용 검색").performTextInput("HATE")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(20_000) { !state.busy && state.snapshot !== beforeSearch && state.snapshot?.reports?.size == 20 && state.snapshot?.reports?.all { it.reason == "혐오" } == true }
        var pages = 0
        while (state.snapshot?.nextCursor != null) {
            check(pages++ < 10) { "Unexpected number of test pages" }
            val count = state.snapshot!!.reports.size
            compose.onNodeWithText("더 보기").performScrollTo().performClick()
            compose.waitUntil(20_000) { check(state.error == null) { "Page error: ${state.error}" }; !state.busy && state.snapshot!!.reports.size > count }
        }
        assertTrue(state.snapshot!!.reports.map { it.id }.containsAll(ids))
        assertEquals(state.snapshot!!.reports.size, state.snapshot!!.reports.distinctBy { it.id }.size)
        compose.onNodeWithText("신고 번호 · 닉네임 · 내용 검색").performScrollTo().performTextReplacement(ids.first())
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(20_000) { !state.busy && state.snapshot?.reports?.map { it.id } == listOf(ids.first()) }
        compose.onNodeWithText("삭제되었거나 없는 게시물").performScrollTo().performClick()
        awaitText("검토 시작")
        compose.onNodeWithText("원문 근거를 제공할 수 없습니다.").performScrollTo().assertExists()
        compose.onNodeWithText("신고자 설명").performScrollTo().assertExists()
        compose.onNodeWithText("기기 검증용 원문 없는 신고").performScrollTo().assertExists()
        compose.onNodeWithText("검토 시작").performScrollTo().performClick()
        awaitText("반려 / 조치 선택")
        compose.onNodeWithText("반려 / 조치 선택").performScrollTo().performClick()
        compose.onNodeWithText("반려").performScrollTo().assertExists()
        compose.onNodeWithText("경고").assertDoesNotExist()
        compose.onNodeWithText("기간 정지").assertDoesNotExist()
    }
    private fun verifyDecision(action: String, loseResponse: Boolean = false) {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("adminUrl")
        assumeTrue(!url.isNullOrBlank())
        val direct = FitMateApiClient(requireNotNull(url))
        direct.authenticate("auth/login", mapOf("email" to args.getString("adminEmail"), "password" to args.getString("adminPassword")))
        lateinit var bridge: MateUiTestServer
        bridge = MateUiTestServer { method, path, body ->
            val headers = bridge.requestHeaders["idempotency-key"]?.let { mapOf("Idempotency-Key" to it) }.orEmpty()
            val data = direct.request(method, path.removePrefix("/"), body.takeIf { method != "GET" }, headers = headers)
            if (loseResponse && path.endsWith("/decisions")) 0 to emptyMap<String, String>() else 200 to data
        }
        try {
        val api = if (loseResponse) bridge.api() else direct
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
        if (action == "NONE") compose.onNodeWithText("반려").performScrollTo().performClick()
        val publicReason = if (action == "NONE") "실제 HTTP 기기 검증 반려" else "실제 HTTP 기기 검증 경고"
        compose.onNodeWithText("처리 사유 및 사용자 안내 (필수)").performScrollTo().performTextInput(publicReason)
        compose.onNodeWithText("내부 메모 (선택)").performScrollTo().performTextInput("가상 신고 근거 확인 완료")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("처리 내용 확인").performScrollTo().performClick()
        compose.onNodeWithText("적용").performClick()
        if (loseResponse) {
            compose.waitUntil(20_000) { !state.busy && state.requiresRefresh }
            assertEquals(AdminError.NETWORK, state.error)
            val persistedBeforeRefresh = direct.request("GET", "admin/reports/$reportId").asJsonObject
            assertEquals("RESOLVED", persistedBeforeRefresh.getAsJsonObject("report")["status"].asString)
            assertEquals(1, persistedBeforeRefresh.getAsJsonArray("history").count { it.asJsonObject["action"].asString == "REPORT_DECIDED" })
            compose.onNodeWithText("최신 상태 다시 조회").performScrollTo().performClick()
            compose.waitUntil(20_000) { !state.busy && state.error == null && !state.requiresRefresh }
            // Reopen the report after the recovery flow returns to the report list.
            compose.onNodeWithText(nickname, substring = true).performScrollTo().performClick()
        }
        awaitText("처리 결과")
        val persisted = direct.request("GET", "admin/reports/$reportId").asJsonObject
        assertEquals(if (action == "NONE") "DISMISSED" else "RESOLVED", persisted.getAsJsonObject("report")["status"].asString)
        assertEquals(action, persisted.getAsJsonObject("decision")["action"].asString)
        assertEquals(publicReason, persisted.getAsJsonObject("decision")["publicReason"].asString)
        assertEquals("가상 신고 근거 확인 완료", persisted.getAsJsonObject("decision")["internalNote"].asString)
        assertEquals(1, persisted.getAsJsonArray("history").count { it.asJsonObject["action"].asString == "REPORT_DECIDED" })
        } finally { bridge.close() }
    }
    private fun awaitText(text: String) {
        compose.waitUntil(15_000) {
            check(state.error == null) { "Admin UI error: ${state.error}; waiting for $text" }
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && !state.busy
        }
    }
}
