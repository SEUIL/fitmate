package com.fitmate.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.remote.ApiFailure
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Run phases separately against a disposable DB; only the host fixture expires sanctions between phases. */
class ServerAdminLifecycleDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val args get() = InstrumentationRegistry.getArguments()
    private fun api() = FitMateApiClient(requireNotNull(args.getString("adminUrl")))
    private fun login(prefix: String) = api().also {
        it.authenticate("auth/login", mapOf("email" to args.getString("${prefix}Email"), "password" to args.getString("${prefix}Password")))
    }
    @Test fun suspendOnDeviceBlocksExistingSessionAndLogin() {
        assumeTrue(args.getString("phase") == "suspend")
        val admin = login("admin"); val subject = login("subject")
        val subjectId = subject.request("GET", "users/me").asJsonObject["id"].asString
        val reportId = report(admin, subjectId)
        val state = AdminUiState(ServerAdminRepository(admin))
        compose.setContent { FitmateTheme { AdminCenter(state, admin) {} } }
        await("최근 24시간 운영 현황")
        compose.onNodeWithText("신고", useUnmergedTree = true).performClick()
        compose.onNodeWithText("신고 번호 · 닉네임 · 내용 검색").performTextInput(reportId)
        compose.waitUntil(20_000) { state.snapshot?.reports?.singleOrNull()?.id == reportId && !state.busy }
        compose.onNodeWithText("회원 프로필 신고").performScrollTo().performClick()
        click("검토 시작"); click("반려 / 조치 선택"); click("기간 정지"); click("30일")
        compose.onNodeWithText("처리 사유 및 사용자 안내 (필수)").performScrollTo().performTextInput("가상 계정 기간 정지 검증")
        closeSoftKeyboard(); click("처리 내용 확인"); click("적용", false); await("처리 결과")
        val blockedSession = runCatching { subject.request("GET", "users/me") }.exceptionOrNull()
        assertTrue(blockedSession is ApiFailure && blockedSession.status in listOf(401, 403))
        assertSuspendedLogin()
        // A second reporter creates an independent, shorter sanction on the same synthetic subject.
        val reporter = api()
        reporter.authenticate("auth/signup", mapOf("email" to "overlap-${UUID.randomUUID()}@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "overlap-${UUID.randomUUID().toString().take(10)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")))
        val shortId = report(reporter, subjectId)
        val claimed = admin.request("POST", "admin/reports/$shortId/claim", mapOf("expectedVersion" to 0)).asJsonObject
        admin.request("POST", "admin/reports/$shortId/decisions", mapOf("expectedVersion" to claimed["report"].asJsonObject["version"].asLong,
            "resolution" to "RESOLVED", "action" to "SUSPEND", "reasonCode" to "SPAM", "publicReason" to "가상 계정 짧은 정지", "durationDays" to 1),
            headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
        assertSuspendedLogin()
    }
    @Test fun shortExpiryDoesNotRestoreDeviceLogin() {
        assumeTrue(args.getString("phase") == "short-expired")
        assertSuspendedLogin()
    }
    @Test fun allExpiredAllowsLoginThroughDeviceUi() {
        assumeTrue(args.getString("phase") == "all-expired")
        val subject = api()
        var signedIn by mutableStateOf(false)
        compose.setContent { FitmateTheme { if (signedIn) Text("복구된 계정으로 로그인 완료") else ServerLogin(Modifier, subject, { signedIn = true }, {}) } }
        compose.onNode(hasSetTextAction() and hasText("이메일")).performTextInput(requireNotNull(args.getString("subjectEmail")))
        compose.onNode(hasSetTextAction() and hasText("비밀번호")).performTextInput(requireNotNull(args.getString("subjectPassword")))
        closeSoftKeyboard(); click("로그인"); await("복구된 계정으로 로그인 완료")
        assertTrue(subject.request("GET", "users/me").asJsonObject.has("id"))
    }
    private fun assertSuspendedLogin() {
        val failure = runCatching { login("subject") }.exceptionOrNull()
        assertTrue(failure is ApiFailure)
        assertEquals(403, (failure as ApiFailure).status)
    }
    private fun report(reporter: FitMateApiClient, subjectId: String) = reporter.request("POST", "reports", mapOf("targetType" to "USER", "targetId" to subjectId,
        "reason" to "SPAM", "detail" to "Synthetic lifecycle verification")).asJsonObject["reportId"].asString
    private fun await(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String, scroll: Boolean = true) {
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithText(text); if (scroll) node.performScrollTo(); node.performClick()
    }
}
